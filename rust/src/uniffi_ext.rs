//! UniFFI extension surface for the native (no-Dart) OpenBubbles clients.
//!
//! Batch 1 (M0.2): queue consumption (journal + pointer queues), and the
//! core send path (text/typing/read/reaction) off the live push state.
//!
//! Batch 2 (M0.3): the auth/login surface — hardware bootstrap (setup_push +
//! anisette), Apple ID login, device/SMS 2FA, carrier (EAP-AKA / SMS-gateway)
//! phone registration, and IDS registration. All flow state the Dart wizard
//! kept in Dart (`AppleAccount`, `CircleClientSession`, the APS broadcast
//! receiver, the IDMS listener, `VerifyBody`, `UpdateAccountFinish`) lives
//! inside the `ULoginSession` object; Kotlin only drives the coarse flow and
//! reacts to `ULoginDelegate` callbacks.
//!
//! Batch 3 (M0.4): attachments (incoming download / outgoing upload+send,
//! with plist-XML persistence for transfer state across restarts),
//! edit/unsend, and group operations (rename, participants, leave, icon).
//!
//! Batch 4 (M0.5): CloudKit message-history sync — paged chat/message
//! pulls with caller-owned continuation cursors, remote tombstone deletes,
//! and a coarse `sync_history` driver with progress + cooperative cancel.
//!
//! Design notes:
//! - rustpush's protocol types can't derive UniFFI traits (they live in the
//!   upstream crate), so every type crossing the FFI boundary is mirrored
//!   into a `U*` record/enum here. Core iMessage semantics are mapped
//!   field-by-field; exotic variants carry `serde_json` strings under
//!   `*_json` fields so nothing is lost while Kotlin stays typed for MVP.
//! - Raw attachment transport data (`AttachmentType::MMCS`/`Inline`) stays
//!   in Rust: `UPart::Attachment.xml` carries the plist-XML blob the Dart
//!   app persisted under `metadata["rustpush"]`, `restore_attachment`
//!   turns it back into an opaque `UAttachment`, and the transfer methods
//!   on `NativePushState` drive the bytes.
//! - Transfer progress uses the plain `UProgressCallback` trait (the UniFFI
//!   equivalent of FRB's StreamSink progress events).
//! - Exports are synchronous and drive the global RUNTIME via `block_on`.
//!   Kotlin callers should stay off the main thread (they already run on
//!   Dispatchers.IO in the service layer).

use crate::api::api::{self, PushMessage, SharedPushState};
use crate::native::NativePushState;
use crate::RUNTIME;
// All message-model types are re-exported at the rustpush crate root.
use rustpush::{
    ChangeParticipantMessage, ConversationData, EditMessage, ErrorMessage, IconChangeMessage,
    IndexedMessagePart, Message, MessageInst, MessagePart, MessageParts, MessageType,
    MoveToRecycleBinMessage, NormalMessage, OperatedChat, PermanentDeleteMessage, ReactMessage,
    ReactMessageType, Reaction, RenameMessage, ShareProfileMessage, UnsendMessage,
    UpdateExtensionMessage, UpdateProfileMessage, UpdateProfileSharingMessage,
};
use serde::Serialize;

fn j<T: Serialize>(t: &T) -> String {
    serde_json::to_string(t).unwrap_or_default()
}

/// Error type crossing the FFI boundary (UniFFI rejects bare String throws).
#[derive(Debug, uniffi::Error)]
pub enum UError {
    SendFailed { reason: String },
    InvalidArgument { reason: String },
    /// Apple ID / 2FA / registration failed. `reason` carries the upstream
    /// (GS/IDS) error text — surface it verbatim like the Dart wizard did.
    LoginFailed { reason: String },
    /// The session is in the wrong state for this call (not connected, no
    /// account, not awaiting a code, ...).
    NotReady { reason: String },
    /// A post-login lookup on the live state failed.
    Failed { reason: String },
}

impl std::fmt::Display for UError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            UError::SendFailed { reason } => write!(f, "send failed: {reason}"),
            UError::InvalidArgument { reason } => write!(f, "invalid argument: {reason}"),
            UError::LoginFailed { reason } => write!(f, "login failed: {reason}"),
            UError::NotReady { reason } => write!(f, "not ready: {reason}"),
            UError::Failed { reason } => write!(f, "failed: {reason}"),
        }
    }
}

// ---------------------------------------------------------------------------
// Mirrored types
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct UConversation {
    pub participants: Vec<String>,
    pub cv_name: Option<String>,
    pub sender_guid: Option<String>,
    pub after_guid: Option<String>,
}

#[derive(uniffi::Enum)]
pub enum UPart {
    Text { text: String, format_json: String },
    /// `xml` is the serialized rustpush `Attachment` (plist XML) — persist it
    /// (the Dart app stored it as `attachment.metadata["rustpush"]`) and feed
    /// it back through `restore_attachment` to download later.
    Attachment { part: u64, uti: String, mime: String, name: String, iris: bool, xml: String },
    Mention { mention: String, text: String },
    Object { json: String },
}

#[derive(uniffi::Record)]
pub struct UIndexedPart {
    pub part: UPart,
    pub idx: Option<u64>,
}

#[derive(uniffi::Enum)]
pub enum UMessage {
    Normal {
        parts: Vec<UIndexedPart>,
        effect: Option<String>,
        reply_guid: Option<String>,
        reply_part: Option<String>,
        subject: Option<String>,
        voice: bool,
        is_sms: bool,
        app_json: Option<String>,
        link_json: Option<String>,
    },
    React {
        to_uuid: String,
        to_part: Option<u64>,
        reaction_json: String,
        to_text: String,
    },
    Rename { new_name: String },
    ChangeParticipants { new_participants: Vec<String>, group_version: u64 },
    Delivered,
    Read,
    Typing { typing: bool },
    Unsend { tuuid: String, edit_part: u64 },
    Edit { tuuid: String, edit_part: u64, parts: Vec<UIndexedPart> },
    /// `icon_xml` is the serialized `MMCSFile` (plist XML) when a new group
    /// photo was attached — pass it to `NativePushState.download_mmcs` to
    /// fetch the image.
    IconChange { json: String, icon_xml: Option<String> },
    SmsConfirmSent { status: bool },
    EnableSmsActivation { enable: bool },
    MessageReadOnDevice,
    MarkUnread,
    Error { for_uuid: String, status: u64, status_str: String },
    MoveToRecycleBin { json: String },
    RecoverChat { json: String },
    PermanentDelete { json: String },
    UpdateProfile { json: String },
    UpdateProfileSharing { json: String },
    ShareProfile { json: String },
    SetTranscriptBackground { json: String },
    UpdateExtension { json: String },
    Unschedule,
    PeerCacheInvalidate,
    NotifyAnyways,
}

#[derive(uniffi::Record)]
pub struct UMessageInst {
    /// Staging GUID; matches `Message.stagingGuid` on the persistence side.
    pub id: String,
    pub sender: Option<String>,
    pub conversation: Option<UConversation>,
    pub message: UMessage,
    pub sent_timestamp: u64,
    pub send_delivered: bool,
    pub verification_failed: bool,
}

#[derive(uniffi::Enum)]
pub enum UPushMessage {
    IMessage { inst: UMessageInst },
    SendConfirm { uuid: String, error: Option<String> },
    // These payload types derive neither Serialize nor Debug; Kotlin gets
    // the event signal only. Typed records land with the batches that
    // consume them (FaceTime UI, StatusKit, Idms).
    RegistrationState,
    NewPhotostream { json: String },
    FaceTime { debug: String },
    StatusUpdate,
    Idms { debug: String },
    TwoFaAuthEvent { success: bool },
    CircleFinishEvent,
    BeaconShared { sender: String, beacon: String, attributes_json: String },
    ProcessQueue,
}

#[derive(uniffi::Record)]
pub struct UQueuedJournal {
    pub id: u64,
    pub attempts: u8,
    pub message: UPushMessage,
}

// ---------------------------------------------------------------------------
// Conversions (rustpush -> U*)
// ---------------------------------------------------------------------------

fn conv_conversation(c: &ConversationData) -> UConversation {
    UConversation {
        participants: c.participants.clone(),
        cv_name: c.cv_name.clone(),
        sender_guid: c.sender_guid.clone(),
        after_guid: c.after_guid.clone(),
    }
}

fn conv_part(p: &MessagePart) -> UPart {
    match p {
        MessagePart::Text(t, fmt) => UPart::Text { text: t.clone(), format_json: j(fmt) },
        MessagePart::Attachment(a) => UPart::Attachment {
            part: a.part,
            uti: a.uti_type.clone(),
            mime: a.mime.clone(),
            name: a.name.clone(),
            iris: a.iris,
            xml: to_plist_xml(a).unwrap_or_default(),
        },
        MessagePart::Mention(m, t) => UPart::Mention { mention: m.clone(), text: t.clone() },
        MessagePart::Object(o) => UPart::Object { json: o.clone() },
    }
}

fn conv_parts(parts: &MessageParts) -> Vec<UIndexedPart> {
    parts
        .0
        .iter()
        .map(|ip: &IndexedMessagePart| UIndexedPart {
            part: conv_part(&ip.part),
            idx: ip.idx.map(|i| i as u64),
        })
        .collect()
}

fn conv_message(m: &Message) -> UMessage {
    match m {
        Message::Message(n) => UMessage::Normal {
            parts: conv_parts(&n.parts),
            effect: n.effect.clone(),
            reply_guid: n.reply_guid.clone(),
            reply_part: n.reply_part.clone(),
            subject: n.subject.clone(),
            voice: n.voice,
            is_sms: matches!(n.service, MessageType::SMS { .. }),
            app_json: n.app.as_ref().map(j),
            link_json: n.link_meta.as_ref().map(j),
        },
        Message::React(r) => UMessage::React {
            to_uuid: r.to_uuid.clone(),
            to_part: r.to_part,
            reaction_json: j(&r.reaction),
            to_text: r.to_text.clone(),
        },
        Message::RenameMessage(RenameMessage { new_name }) => UMessage::Rename { new_name: new_name.clone() },
        Message::ChangeParticipants(ChangeParticipantMessage { new_participants, group_version }) => {
            UMessage::ChangeParticipants {
                new_participants: new_participants.clone(),
                group_version: *group_version,
            }
        }
        Message::Delivered => UMessage::Delivered,
        Message::Read => UMessage::Read,
        Message::Typing(t, _) => UMessage::Typing { typing: *t },
        Message::Unsend(UnsendMessage { tuuid, edit_part }) => UMessage::Unsend { tuuid: tuuid.clone(), edit_part: *edit_part },
        Message::Edit(EditMessage { tuuid, edit_part, new_parts }) => UMessage::Edit {
            tuuid: tuuid.clone(),
            edit_part: *edit_part,
            parts: conv_parts(new_parts),
        },
        Message::IconChange(IconChangeMessage { file, .. }) => UMessage::IconChange {
            json: j(m),
            icon_xml: file.as_ref().and_then(|f| to_plist_xml(f).ok()),
        },
        Message::EnableSmsActivation(e) => UMessage::EnableSmsActivation { enable: *e },
        Message::MessageReadOnDevice => UMessage::MessageReadOnDevice,
        Message::SmsConfirmSent(status) => UMessage::SmsConfirmSent { status: *status },
        Message::MarkUnread => UMessage::MarkUnread,
        Message::PeerCacheInvalidate => UMessage::PeerCacheInvalidate,
        Message::UpdateExtension(UpdateExtensionMessage { .. }) => UMessage::UpdateExtension { json: j(m) },
        Message::Error(ErrorMessage { for_uuid, status, status_str }) => UMessage::Error {
            for_uuid: for_uuid.clone(),
            status: *status,
            status_str: status_str.clone(),
        },
        Message::MoveToRecycleBin(MoveToRecycleBinMessage { .. }) => UMessage::MoveToRecycleBin { json: j(m) },
        Message::RecoverChat(OperatedChat { .. }) => UMessage::RecoverChat { json: j(m) },
        Message::PermanentDelete(PermanentDeleteMessage { .. }) => UMessage::PermanentDelete { json: j(m) },
        Message::UpdateProfile(UpdateProfileMessage { .. }) => UMessage::UpdateProfile { json: j(m) },
        Message::UpdateProfileSharing(UpdateProfileSharingMessage { .. }) => UMessage::UpdateProfileSharing { json: j(m) },
        Message::ShareProfile(ShareProfileMessage { .. }) => UMessage::ShareProfile { json: j(m) },
        Message::NotifyAnyways => UMessage::NotifyAnyways,
        Message::Unschedule => UMessage::Unschedule,
        Message::SetTranscriptBackground(_) => UMessage::SetTranscriptBackground { json: j(m) },
    }
}

fn conv_inst(i: &MessageInst) -> UMessageInst {
    UMessageInst {
        id: i.id.clone(),
        sender: i.sender.clone(),
        conversation: i.conversation.as_ref().map(conv_conversation),
        message: conv_message(&i.message),
        sent_timestamp: i.sent_timestamp,
        send_delivered: i.send_delivered,
        verification_failed: i.verification_failed,
    }
}

fn conv_push(p: &PushMessage) -> UPushMessage {
    match p {
        PushMessage::IMessage(inst) => UPushMessage::IMessage { inst: conv_inst(inst) },
        PushMessage::SendConfirm { uuid, error } => UPushMessage::SendConfirm { uuid: uuid.clone(), error: error.clone() },
        PushMessage::RegistrationState(_) => UPushMessage::RegistrationState,
        PushMessage::NewPhotostream(a) => UPushMessage::NewPhotostream { json: j(a) },
        PushMessage::FaceTime(f) => UPushMessage::FaceTime { debug: format!("{f:?}") },
        PushMessage::StatusUpdate(_) => UPushMessage::StatusUpdate,
        PushMessage::Idms(i) => UPushMessage::Idms { debug: format!("{i:?}") },
        PushMessage::TwoFaAuthEvent(b) => UPushMessage::TwoFaAuthEvent { success: *b },
        PushMessage::CircleFinishEvent => UPushMessage::CircleFinishEvent,
        PushMessage::BeaconShared { sender, beacon, attributes } => UPushMessage::BeaconShared {
            sender: sender.clone(),
            beacon: beacon.clone(),
            attributes_json: j(attributes),
        },
        PushMessage::ProcessQueue => UPushMessage::ProcessQueue,
    }
}

fn back_conversation(c: UConversation) -> ConversationData {
    ConversationData {
        participants: c.participants,
        cv_name: c.cv_name,
        sender_guid: c.sender_guid,
        after_guid: c.after_guid,
    }
}

// ---------------------------------------------------------------------------
// Queue consumption (Kotlin replaces the Dart retry loop)
// ---------------------------------------------------------------------------

#[uniffi::export]
pub fn read_queued_journal() -> Option<UQueuedJournal> {
    RUNTIME
        .block_on(api::read_queued_message())
        .map(|(id, attempts, msg)| UQueuedJournal { id, attempts, message: conv_push(&msg) })
}

#[uniffi::export]
pub fn mark_journal_attempt(id: u64, success: bool) {
    RUNTIME.block_on(api::mark_queue_attempt(id, success));
}

#[uniffi::export]
pub fn ptr_to_message(ptr: String) -> Option<UPushMessage> {
    RUNTIME.block_on(api::ptr_to_dart(ptr)).as_ref().map(conv_push)
}

#[uniffi::export]
pub fn complete_message(ptr: String) {
    RUNTIME.block_on(api::complete_msg(ptr));
}

// ---------------------------------------------------------------------------
// Send path (methods on the live push state)
// ---------------------------------------------------------------------------

fn reaction_from_idx(idx: u64, emoji: Option<String>) -> Option<Reaction> {
    Some(match (idx, emoji) {
        (0, None) => Reaction::Heart,
        (1, None) => Reaction::Like,
        (2, None) => Reaction::Dislike,
        (3, None) => Reaction::Laugh,
        (4, None) => Reaction::Emphasize,
        (5, None) => Reaction::Question,
        (6, Some(em)) => Reaction::Emoji(em),
        _ => return None,
    })
}

fn send_inst(state: &SharedPushState, inst: MessageInst) -> Result<UMessageInst, UError> {
    RUNTIME
        .block_on(api::send(&state.client, &state.local_broadcast, inst.clone()))
        .map(|_| conv_inst(&inst))
        .map_err(|e| UError::SendFailed { reason: e.to_string() })
}
#[uniffi::export]
impl NativePushState {
    /// Send a plain (optionally formatted-later) text message. Returns the
    /// staged MessageInst — `id` is the staging GUID to persist.
    pub fn send_text(
        &self,
        conversation: UConversation,
        sender: String,
        text: String,
        reply_guid: Option<String>,
        reply_part: Option<String>,
        effect: Option<String>,
        subject: Option<String>,
    ) -> Result<UMessageInst, UError> {
        let mut normal = NormalMessage::new(text, MessageType::IMessage);
        normal.reply_guid = reply_guid;
        normal.reply_part = reply_part;
        normal.effect = effect;
        normal.subject = subject;
        let inst = RUNTIME.block_on(api::new_msg(
            back_conversation(conversation),
            sender,
            Message::Message(normal),
        ));
        send_inst(self.shared(), inst)
    }

    pub fn send_typing(&self, conversation: UConversation, sender: String, typing: bool) -> Result<(), UError> {
        let inst = RUNTIME.block_on(api::new_msg(
            back_conversation(conversation),
            sender,
            Message::Typing(typing, None),
        ));
        send_inst(self.shared(), inst).map(|_| ())
    }

    pub fn send_read(&self, conversation: UConversation, sender: String) -> Result<(), UError> {
        let inst = RUNTIME.block_on(api::new_msg(
            back_conversation(conversation),
            sender,
            Message::Read,
        ));
        send_inst(self.shared(), inst).map(|_| ())
    }

    /// Send (or remove, with `enable: false`) a tapback.
    /// `reaction_idx`: 0 heart, 1 like, 2 dislike, 3 laugh, 4 emphasize,
    /// 5 question; 6 + `emoji` for custom emoji tapbacks.
    pub fn send_reaction(
        &self,
        conversation: UConversation,
        sender: String,
        to_uuid: String,
        to_part: Option<u64>,
        reaction_idx: u64,
        emoji: Option<String>,
        to_text: String,
        enable: bool,
    ) -> Result<(), UError> {
        let reaction = reaction_from_idx(reaction_idx, emoji).ok_or(UError::InvalidArgument { reason: "invalid reaction idx".to_string() })?;
        let react = ReactMessage {
            to_uuid,
            to_part,
            reaction: ReactMessageType::React { reaction, enable },
            to_text,
            embedded_profile: None,
        };
        let inst = RUNTIME.block_on(api::new_msg(
            back_conversation(conversation),
            sender,
            Message::React(react),
        ));
        send_inst(self.shared(), inst).map(|_| ())
    }
}

// ---------------------------------------------------------------------------
// Batch 2: login / 2FA / phone-auth / registration surface
// ---------------------------------------------------------------------------
//
// Flow (mirrors the Dart setup wizard, see lib/app/layouts/setup/setup_view.dart):
//
//   let session = createLoginSession(dir, delegate)   // reads hw_info.plist
//   session.connect()                    // optional; login() auto-connects
//   session.login(user, pass)            // -> ULoginState, pumps the 2FA machine
//   session.submit2faCode("123456")      // device-code or SMS-code entry
//   session.chooseSmsPhone(phoneId)      // when >1 trusted phone is offered
//   session.register()                   // -> URegistrationResult (writes id.plist)
//   initNative(dir, null, handler)       // existing export rebuilds the live state
//
// Everything is synchronous + `RUNTIME.block_on`; delegate callbacks fire on
// the calling thread, before the call returns. Call off the Android main
// thread, and never re-enter the session from inside a delegate callback.

use std::fmt::Debug;
use std::sync::{Arc, Mutex};

use rustpush::{
    APSMessage, AppleAccount, ArcAnisetteClient, authenticate_smsless, CircleClientSession,
    DefaultAnisetteProvider, DebugMutex, EntitlementAuthState, IdmsAuthListener, IDSNGMIdentity,
    IDSUser, LoginState, PushError, TrustedPhoneNumber, UpdateAccountFinish,
};

use tokio::sync::broadcast;

/// The account handle rustpush passes around (`Arc<Mutex<AppleAccount>>`).
type AccountRef = Arc<DebugMutex<AppleAccount<DefaultAnisetteProvider>>>;

fn not_connected() -> UError {
    UError::NotReady { reason: "login session is not connected; call connect() first".to_string() }
}
fn no_account() -> UError {
    UError::NotReady { reason: "no Apple account in this session; call login() first".to_string() }
}
fn login_err(e: impl std::fmt::Display) -> UError {
    UError::LoginFailed { reason: e.to_string() }
}

/// Mirror of rustpush `LoginState`. The SMS `VerifyBody` stays inside the
/// session (its fields are private upstream); `phone_id`/`mode` are extracted
/// for display only.
#[derive(uniffi::Enum)]
pub enum ULoginState {
    LoggedIn,
    /// A trusted device should be showing the code prompt.
    NeedsDevice2Fa,
    /// Device 2FA is armed; submit the code shown on the trusted device.
    Needs2FaVerification,
    /// A trusted phone must be chosen (see `get_sms_phone_options`).
    NeedsSms2Fa,
    /// SMS 2FA was sent to `phone_id`; submit the received code.
    NeedsSms2FaVerification { phone_id: u32, mode: String },
    /// Apple wants extra account steps (terms); see
    /// `get_update_account_page` / `complete_update_account`.
    NeedsExtraStep { detail: String },
    NeedsLogin,
}

/// Coarse progress signal fired through `ULoginDelegate::on_stage`.
#[derive(uniffi::Enum)]
pub enum ULoginStage {
    /// Establishing the APS connection + anisette (`setup_push`).
    Connecting,
    /// Verifying Apple ID + password (`try_auth`).
    Authenticating,
    /// Circle session started; waiting on the trusted device.
    AwaitingDevice2Fa,
    FetchingSmsOptions,
    SendingSmsCode,
    VerifyingCode,
    RegisteringIds,
    Finished,
}

/// Mirror of rustpush `TrustedPhoneNumber` (SMS 2FA target choice).
#[derive(uniffi::Record)]
pub struct UTrustedPhone {
    pub number_with_dial_code: String,
    pub last_two_digits: String,
    pub push_mode: String,
    pub id: u32,
}

/// Mirror of api.rs `RegisterState` (IDS registration health of the live
/// client — `NativePushState::get_regstate`).
#[derive(uniffi::Enum)]
pub enum URegisterState {
    Registered { next_s: i64 },
    Registering,
    Failed { retry_wait: Option<u64>, error: String },
}

/// Result of `ULoginSession::register`.
#[derive(uniffi::Enum)]
pub enum URegistrationResult {
    /// `id.plist` was written. Rebuild the live state with `init_native`.
    Registered,
    /// Apple refused registration and wants the user to read this first.
    AppleBlocked {
        title: String,
        body: String,
        action_url: Option<String>,
        action_label: Option<String>,
    },
}

/// A carrier-authenticated phone user, serialized for Kotlin-side caching
/// (mirrors the Dart `sms-auth-<subscription>` cachedCodes entries).
#[derive(uniffi::Record)]
pub struct UPhoneUser {
    pub subscription: i64,
    pub serialized: String,
}

/// Mirror of api.rs `DeviceInfo` (the emulated Mac's identity).
#[derive(uniffi::Record)]
pub struct UDeviceInfo {
    pub name: String,
    pub serial: String,
    pub os_version: String,
}

/// Progress callbacks for the login flow. All methods run synchronously on
/// the thread that invoked the session method; do NOT call back into the
/// session (or any other UniFFI export) from inside them.
#[uniffi::export(with_foreign)]
pub trait ULoginDelegate: Send + Sync + Debug {
    /// Coarse progress (each step of the internal state machine).
    fn on_stage(&self, stage: ULoginStage);
    /// Emitted whenever the machine settles: at the end of `login`,
    /// `submit_2fa_code`, `choose_sms_phone`, and `request_sms_fallback`.
    fn on_state(&self, state: ULoginState);
    /// Circle proximity pairing session (mirrors the Dart
    /// `circle-proximity-session` method-channel call): `Some(sid)` starts
    /// the nearby-device pairing surface, `None` clears it.
    fn on_circle_session(&self, sid: Option<String>);
    /// Non-fatal diagnostics (e.g. no trusted phone numbers available).
    /// Fatal failures are returned as `UError` from the calling method.
    fn on_error(&self, reason: String);
}

/// EAP-AKA challenge callback for SMS-less carrier authentication
/// (`sms_less_auth`). Runs on the Rust runtime thread; must not call back
/// into Rust. Return an empty string to signal failure.
#[uniffi::export(with_foreign)]
pub trait UEapAkaHandler: Send + Sync + Debug {
    fn process_challenge(&self, challenge: String) -> String;
}

struct LoginInner {
    path: String,
    config: api::JoinedOSConfig,
    identity: IDSNGMIdentity,
    cached_push: Option<rustpush::APSState>,
    conn: Option<rustpush::APSConnection>,
    anisette: Option<ArcAnisetteClient<DefaultAnisetteProvider>>,
    account: Option<AccountRef>,
    state: LoginState,
    circle: Option<CircleClientSession<DefaultAnisetteProvider>>,
    receiver: Option<broadcast::Receiver<APSMessage>>,
    idms: Option<Arc<IdmsAuthListener>>,
    apple_user: Option<IDSUser>,
    phone_users: Vec<(i64, IDSUser)>,
    sms_opts: Vec<TrustedPhoneNumber>,
    update_finish: Option<UpdateAccountFinish>,
}

/// Stateful login driver. Create with [`create_login_session`].
#[derive(uniffi::Object)]
pub struct ULoginSession {
    delegate: Arc<dyn ULoginDelegate>,
    inner: Mutex<LoginInner>,
}

fn conv_login_state(s: &LoginState) -> ULoginState {
    match s {
        LoginState::LoggedIn => ULoginState::LoggedIn,
        LoginState::NeedsDevice2FA => ULoginState::NeedsDevice2Fa,
        LoginState::Needs2FAVerification => ULoginState::Needs2FaVerification,
        LoginState::NeedsSMS2FA => ULoginState::NeedsSms2Fa,
        LoginState::NeedsSMS2FAVerification(body) => {
            // VerifyBody's fields are private upstream; read them via serde.
            let v = serde_json::to_value(body).unwrap_or(serde_json::Value::Null);
            ULoginState::NeedsSms2FaVerification {
                phone_id: v
                    .get("phoneNumber")
                    .and_then(|p| p.get("id"))
                    .and_then(|i| i.as_u64())
                    .unwrap_or(0) as u32,
                mode: v.get("mode").and_then(|m| m.as_str()).unwrap_or("").to_string(),
            }
        }
        LoginState::NeedsExtraStep(detail) => ULoginState::NeedsExtraStep { detail: detail.clone() },
        LoginState::NeedsLogin => ULoginState::NeedsLogin,
    }
}

fn conv_regstate(r: api::RegisterState) -> URegisterState {
    match r {
        api::RegisterState::Registered { next_s } => URegisterState::Registered { next_s },
        api::RegisterState::Registering => URegisterState::Registering,
        api::RegisterState::Failed { retry_wait, error } => URegisterState::Failed { retry_wait, error },
    }
}

enum Step {
    NeedsLogin,
    Device2Fa,
    Sms2Fa,
    VerifyDevice,
    VerifySms,
    Terminal,
}

fn step_of(s: &LoginState) -> Step {
    match s {
        LoginState::NeedsLogin => Step::NeedsLogin,
        LoginState::NeedsDevice2FA => Step::Device2Fa,
        LoginState::NeedsSMS2FA => Step::Sms2Fa,
        LoginState::Needs2FAVerification => Step::VerifyDevice,
        LoginState::NeedsSMS2FAVerification(_) => Step::VerifySms,
        _ => Step::Terminal,
    }
}

fn upsert_phone_user(inner: &mut LoginInner, subscription: i64, user: IDSUser) {
    if let Some(slot) = inner.phone_users.iter_mut().find(|(s, _)| *s == subscription) {
        slot.1 = user;
    } else {
        inner.phone_users.push((subscription, user));
    }
}

fn connect_locked(delegate: &Arc<dyn ULoginDelegate>, inner: &mut LoginInner) -> Result<(), UError> {
    if inner.conn.is_some() && inner.anisette.is_some() {
        return Ok(());
    }
    delegate.on_stage(ULoginStage::Connecting);
    let (conn, err) = RUNTIME.block_on(api::setup_push(
        &inner.config,
        &inner.identity,
        inner.cached_push.take(),
        inner.path.clone(),
    ));
    if let Some(e) = err {
        return Err(UError::LoginFailed { reason: format!("failed to establish push connection: {e}") });
    }
    let anisette = RUNTIME.block_on(api::make_anisette(inner.path.clone(), &inner.config, &conn));
    inner.conn = Some(conn);
    inner.anisette = Some(anisette);
    Ok(())
}

fn ensure_watcher_locked(inner: &mut LoginInner) {
    let Some(conn) = inner.conn.clone() else { return };
    if inner.receiver.is_none() {
        inner.receiver = Some(api::subscribe_conn(&conn));
    }
    if inner.idms.is_none() {
        inner.idms = Some(RUNTIME.block_on(api::make_idms(&conn)));
    }
}

fn send_sms_locked(
    delegate: &Arc<dyn ULoginDelegate>,
    inner: &mut LoginInner,
    phone_id: u32,
) -> Result<(), UError> {
    delegate.on_stage(ULoginStage::SendingSmsCode);
    let account = inner.account.clone().ok_or_else(no_account)?;
    let locked = inner.circle.take();
    inner.state = RUNTIME
        .block_on(api::send_2fa_sms(locked, &account, phone_id))
        .map_err(login_err)?;
    Ok(())
}

/// Drives the login state machine until it reaches a state that needs user
/// input (code entry, phone choice, terms acceptance) or a terminal state.
fn pump_locked(
    delegate: &Arc<dyn ULoginDelegate>,
    inner: &mut LoginInner,
    creds: Option<(String, String)>,
) -> Result<ULoginState, UError> {
    let mut creds = creds;
    for _ in 0..10 {
        match step_of(&inner.state) {
            Step::NeedsLogin => {
                delegate.on_stage(ULoginStage::Authenticating);
                let conn = inner.conn.clone().ok_or_else(not_connected)?;
                let anisette = inner.anisette.clone().ok_or_else(not_connected)?;
                let (account, state) = RUNTIME
                    .block_on(api::try_auth(inner.path.clone(), &inner.config, &conn, &anisette, creds.take()))
                    .map_err(login_err)?;
                let user = RUNTIME
                    .block_on(api::try_icloud_login(inner.path.clone(), &inner.config, &account))
                    .map_err(login_err)?;
                if let Some(u) = user {
                    inner.apple_user = Some(u);
                }
                inner.account = Some(account);
                inner.state = state;
            }
            Step::Device2Fa => {
                delegate.on_stage(ULoginStage::AwaitingDevice2Fa);
                ensure_watcher_locked(inner);
                let account = inner.account.clone().ok_or_else(no_account)?;
                let conn = inner.conn.clone().ok_or_else(not_connected)?;
                let (circle, state, sid) = RUNTIME
                    .block_on(api::send_2fa_to_devices(&account, &conn))
                    .map_err(login_err)?;
                inner.circle = Some(circle);
                inner.state = state;
                delegate.on_circle_session(sid);
            }
            Step::Sms2Fa => {
                // Mirror Dart: clear the proximity pairing surface when the
                // SMS path is entered.
                delegate.on_circle_session(None);
                delegate.on_stage(ULoginStage::FetchingSmsOptions);
                let account = inner.account.clone().ok_or_else(no_account)?;
                let (opts, new_state) = RUNTIME
                    .block_on(api::get_2fa_sms_opts(&account))
                    .map_err(login_err)?;
                inner.sms_opts = opts;
                if let Some(ns) = new_state {
                    inner.state = ns;
                    continue;
                }
                if inner.sms_opts.len() == 1 {
                    let phone_id = inner.sms_opts[0].id;
                    send_sms_locked(delegate, inner, phone_id)?;
                } else if inner.sms_opts.is_empty() {
                    delegate.on_error("no trusted phone numbers are available for SMS 2FA".to_string());
                    delegate.on_state(conv_login_state(&inner.state));
                    return Ok(conv_login_state(&inner.state));
                } else {
                    // Kotlin must pick: get_sms_phone_options + choose_sms_phone.
                    delegate.on_state(conv_login_state(&inner.state));
                    return Ok(conv_login_state(&inner.state));
                }
            }
            Step::Terminal | Step::VerifyDevice | Step::VerifySms => break,
        }
    }
    if matches!(inner.state, LoginState::LoggedIn) {
        delegate.on_circle_session(None);
        delegate.on_stage(ULoginStage::Finished);
    }
    delegate.on_state(conv_login_state(&inner.state));
    Ok(conv_login_state(&inner.state))
}

/// Create a login session from a provisioned hardware config
/// (`hw_info.plist`, written by the hardware-provisioning flow).
/// Fails with `UError::NotReady` when no config exists yet.
#[uniffi::export]
pub fn create_login_session(path: String, delegate: Arc<dyn ULoginDelegate>) -> Result<Arc<ULoginSession>, UError> {
    let Some(hw) = api::read_hardware(path.clone()) else {
        return Err(UError::NotReady {
            reason: format!("no hardware config at {path} (hw_info.plist missing); provision a device config first"),
        });
    };
    let identity = api::decode_identity(&hw.identity)
        .map_err(|e| UError::NotReady { reason: format!("failed to decode stored identity: {e}") })?;
    Ok(Arc::new(ULoginSession {
        delegate,
        inner: Mutex::new(LoginInner {
            path,
            config: hw.os_config,
            identity,
            cached_push: Some(hw.push),
            conn: None,
            anisette: None,
            account: None,
            state: LoginState::NeedsLogin,
            circle: None,
            receiver: None,
            idms: None,
            apple_user: None,
            phone_users: Vec::new(),
            sms_opts: Vec::new(),
            update_finish: None,
        }),
    }))
}

/// Username persisted in `gsa.plist`, if a previous login saved credentials.
#[uniffi::export]
pub fn saved_login_username(path: String) -> Option<String> {
    api::get_available_user(path)
}

/// Whether `id.plist` holds registered IDS users (i.e. setup previously
/// completed far enough to register).
#[uniffi::export]
pub fn has_saved_users(path: String) -> bool {
    api::restore_users(path).map(|u| !u.is_empty()).unwrap_or(false)
}

impl ULoginSession {
    fn pump(&self, creds: Option<(String, String)>) -> Result<ULoginState, UError> {
        let mut inner = self.inner.lock().expect("login session lock poisoned");
        pump_locked(&self.delegate, &mut inner, creds)
    }
}

#[uniffi::export]
impl ULoginSession {
    /// Establish the APS connection + anisette (wraps `setup_push` +
    /// `make_anisette`). `login()` calls this automatically; it is separate
    /// so phone registration can connect before any Apple ID login.
    pub fn connect(&self) -> Result<(), UError> {
        let mut inner = self.inner.lock().expect("login session lock poisoned");
        connect_locked(&self.delegate, &mut inner)
    }

    /// Identity (name/serial/OS) of the emulated hardware. `name` containing
    /// "iPhone"/"iPad"/"iPod" gates phone-number registration, like Dart.
    pub fn device_info(&self) -> Result<UDeviceInfo, UError> {
        let inner = self.inner.lock().expect("login session lock poisoned");
        let info = api::get_device_info(&inner.config)
            .map_err(|e| UError::Failed { reason: e.to_string() })?;
        Ok(UDeviceInfo { name: info.name, serial: info.serial, os_version: info.os_version })
    }

    /// Current login state snapshot.
    pub fn state(&self) -> ULoginState {
        let inner = self.inner.lock().expect("login session lock poisoned");
        conv_login_state(&inner.state)
    }

    /// Start (or resume) Apple ID login. With `username`+`password` the
    /// stored credentials are replaced (fresh login); without them the
    /// previously saved `gsa.plist` credentials are reused. Pumps the 2FA
    /// state machine and returns the state requiring user action next.
    pub fn login(&self, username: Option<String>, password: Option<String>) -> Result<ULoginState, UError> {
        // Apple IDs are case-insensitive; GSA wants lowercase.
        let creds = match (username, password) {
            (Some(u), Some(p)) => Some((u.to_lowercase(), p)),
            _ => None,
        };
        {
            let mut inner = self.inner.lock().expect("login session lock poisoned");
            connect_locked(&self.delegate, &mut inner)?;
        }
        self.pump(creds)
    }

    /// Switch a device-2FA prompt to SMS 2FA (the "send code to phone
    /// instead" button in the Dart 2FA page).
    pub fn request_sms_fallback(&self) -> Result<ULoginState, UError> {
        {
            let mut inner = self.inner.lock().expect("login session lock poisoned");
            inner.state = LoginState::NeedsSMS2FA;
        }
        self.pump(None)
    }

    /// Trusted phone numbers for SMS 2FA (cached from the last pump).
    pub fn get_sms_phone_options(&self) -> Result<Vec<UTrustedPhone>, UError> {
        let mut inner = self.inner.lock().expect("login session lock poisoned");
        if inner.sms_opts.is_empty() {
            let account = inner.account.clone().ok_or_else(no_account)?;
            let (opts, _) = RUNTIME.block_on(api::get_2fa_sms_opts(&account)).map_err(login_err)?;
            inner.sms_opts = opts;
        }
        Ok(inner
            .sms_opts
            .iter()
            .map(|o| UTrustedPhone {
                number_with_dial_code: o.number_with_dial_code.clone(),
                last_two_digits: o.last_two_digits.clone(),
                push_mode: o.push_mode.clone(),
                id: o.id,
            })
            .collect())
    }

    /// Send the SMS 2FA code to the chosen phone; then await
    /// `submit_2fa_code`.
    pub fn choose_sms_phone(&self, phone_id: u32) -> Result<ULoginState, UError> {
        {
            let mut inner = self.inner.lock().expect("login session lock poisoned");
            send_sms_locked(&self.delegate, &mut inner, phone_id)?;
        }
        self.pump(None)
    }

    /// Submit a 2FA code — either the trusted-device code (after
    /// `Needs2FaVerification`) or the SMS code (after
    /// `NeedsSms2FaVerification`). Pumps the machine afterwards.
    pub fn submit_2fa_code(&self, code: String) -> Result<ULoginState, UError> {
        {
            let mut inner = self.inner.lock().expect("login session lock poisoned");
            delegate_stage(&self.delegate, ULoginStage::VerifyingCode);
            match step_of(&inner.state) {
                Step::VerifyDevice => {
                    let mut circle = inner
                        .circle
                        .take()
                        .ok_or_else(|| UError::NotReady { reason: "no device 2FA session".to_string() })?;
                    let mut receiver = inner
                        .receiver
                        .take()
                        .ok_or_else(|| UError::NotReady { reason: "no APS watcher".to_string() })?;
                    let account = inner.account.clone().ok_or_else(no_account)?;
                    let anisette = inner.anisette.clone().ok_or_else(not_connected)?;
                    let idms = inner
                        .idms
                        .clone()
                        .ok_or_else(|| UError::NotReady { reason: "no idms listener".to_string() })?;
                    let result = RUNTIME.block_on(api::verify_2fa(
                        inner.path.clone(),
                        &mut circle,
                        &anisette,
                        &inner.config,
                        &account,
                        &mut receiver,
                        &idms,
                        code,
                    ));
                    inner.circle = Some(circle);
                    inner.receiver = Some(receiver);
                    let (state, user) = result.map_err(login_err)?;
                    inner.state = state;
                    if let Some(u) = user {
                        inner.apple_user = Some(u);
                    }
                }
                Step::VerifySms => {
                    let st = std::mem::replace(&mut inner.state, LoginState::NeedsLogin);
                    let LoginState::NeedsSMS2FAVerification(body) = st else {
                        inner.state = st;
                        return Err(UError::NotReady { reason: "login is not awaiting an SMS code".to_string() });
                    };
                    let account = inner.account.clone().ok_or_else(no_account)?;
                    let anisette = inner.anisette.clone().ok_or_else(not_connected)?;
                    let (state, user) = RUNTIME
                        .block_on(api::verify_2fa_sms(
                            inner.path.clone(),
                            &account,
                            &anisette,
                            &inner.config,
                            &body,
                            code,
                        ))
                        .map_err(login_err)?;
                    inner.state = state;
                    if let Some(u) = user {
                        inner.apple_user = Some(u);
                    }
                }
                _ => return Err(UError::NotReady { reason: "login is not awaiting a 2FA code".to_string() }),
            }
        }
        self.pump(None)
    }

    /// Fetch the account-update (terms) page HTML. Mirrors Dart's
    /// `updateAccountUi` webview source; the page is finished with
    /// `complete_update_account`.
    pub fn get_update_account_page(&self) -> Result<String, UError> {
        let mut inner = self.inner.lock().expect("login session lock poisoned");
        let account = inner.account.clone().ok_or_else(no_account)?;
        let (html, finish) = RUNTIME
            .block_on(api::update_account_headers(&account, &inner.config))
            .map_err(login_err)?;
        inner.update_finish = Some(finish);
        Ok(html)
    }

    /// Finish the terms/account-update flow (calls `do_login` with the
    /// stored `UpdateAccountFinish`), producing the Apple IDS user.
    pub fn complete_update_account(&self) -> Result<ULoginState, UError> {
        {
            let mut inner = self.inner.lock().expect("login session lock poisoned");
            let account = inner.account.clone().ok_or_else(no_account)?;
            let finish = inner.update_finish.take();
            let user = RUNTIME
                .block_on(api::do_login(inner.path.clone(), &account, finish, &inner.config))
                .map_err(login_err)?;
            inner.apple_user = Some(user);
            inner.state = LoginState::LoggedIn;
        }
        self.pump(None)
    }

    /// Display name of the logged-in Apple account.
    pub fn get_username(&self) -> Result<String, UError> {
        let inner = self.inner.lock().expect("login session lock poisoned");
        let account = inner.account.clone().ok_or_else(no_account)?;
        RUNTIME.block_on(api::get_user_name(&account)).map_err(login_err)
    }

    /// SMS-less carrier authentication (EAP-AKA): `mccmnc`/`subscriber`/`imei`
    /// come from the Android telephony stack (see native `get_carrier` for the
    /// gateway lookup); `handler.process_challenge` answers carrier challenges
    /// (return an empty string to abort). On success the phone user is stored
    /// for `register`.
    pub fn sms_less_auth(
        &self,
        subscription: i64,
        mccmnc: String,
        subscriber: String,
        imei: String,
        handler: Arc<dyn UEapAkaHandler>,
    ) -> Result<(), UError> {
        let mut inner = self.inner.lock().expect("login session lock poisoned");
        let conn = inner.conn.clone().ok_or_else(not_connected)?;
        let config = inner.config.clone();
        let mut entitlementstate = EntitlementAuthState::new(subscriber, mccmnc, imei);
        let challenge_handler = handler.clone();
        let entitlements = RUNTIME
            .block_on(entitlementstate.get_entitlements(&*config, &conn, |challenge| async move {
                let resp = challenge_handler.process_challenge(challenge);
                if resp.is_empty() {
                    Err(PushError::IoError(std::io::Error::new(
                        std::io::ErrorKind::Other,
                        "EAP-AKA challenge callback failed",
                    )))
                } else {
                    Ok(resp)
                }
            }))
            .map_err(|e| UError::LoginFailed { reason: format!("carrier entitlement failed: {e}") })?;
        let user = RUNTIME
            .block_on(authenticate_smsless(&entitlements.phone, &entitlements.host, &*config, &conn))
            .map_err(|e| UError::LoginFailed { reason: format!("carrier authentication failed: {e}") })?;
        upsert_phone_user(&mut inner, subscription, user);
        Ok(())
    }

    /// SMS-gateway phone registration: `number` + `sig` are the gateway
    /// response parts (the Dart side split on "|"), `sig` the hex-decoded
    /// signature bytes.
    pub fn auth_phone(&self, subscription: i64, number: String, sig: Vec<u8>) -> Result<(), UError> {
        let mut inner = self.inner.lock().expect("login session lock poisoned");
        let conn = inner.conn.clone().ok_or_else(not_connected)?;
        let config = inner.config.clone();
        let user = RUNTIME
            .block_on(api::auth_phone(&conn, &config, number, sig))
            .map_err(login_err)?;
        upsert_phone_user(&mut inner, subscription, user);
        Ok(())
    }

    /// Serialized phone users for Kotlin-side persistence. Restore with
    /// `import_phone_user` on future runs to skip carrier auth.
    pub fn export_phone_users(&self) -> Vec<UPhoneUser> {
        let inner = self.inner.lock().expect("login session lock poisoned");
        inner
            .phone_users
            .iter()
            .filter_map(|(sub, u)| {
                api::save_user(u).ok().map(|serialized| UPhoneUser { subscription: *sub, serialized })
            })
            .collect()
    }

    /// Restore a cached phone user. Returns `false` (and drops it) when the
    /// user's certificate no longer validates against the live connection —
    /// Kotlin should discard the cached entry then, like Dart did.
    pub fn import_phone_user(&self, subscription: i64, serialized: String) -> Result<bool, UError> {
        let mut inner = self.inner.lock().expect("login session lock poisoned");
        let user = api::restore_user(serialized)
            .map_err(|e| UError::InvalidArgument { reason: format!("cached user failed to restore: {e}") })?;
        let conn = inner.conn.clone().ok_or_else(not_connected)?;
        match RUNTIME.block_on(api::validate_cert(&conn, &user)) {
            Ok(_) => {
                upsert_phone_user(&mut inner, subscription, user);
                Ok(true)
            }
            Err(_) => Ok(false),
        }
    }

    /// Register all collected users (Apple ID + phone numbers) with IDS.
    /// On `Registered`, `id.plist` is written — rebuild the live state with
    /// `init_native(dir, null, handler)`. `AppleBlocked` mirrors the Dart
    /// support-alert dialog (registration stopped until acknowledged).
    pub fn register(&self) -> Result<URegistrationResult, UError> {
        {
            let mut inner = self.inner.lock().expect("login session lock poisoned");
            let conn = inner.conn.clone().ok_or_else(not_connected)?;
            let mut users: Vec<IDSUser> = Vec::new();
            if let Some(u) = &inner.apple_user {
                users.push(u.clone());
            }
            for (_, u) in &inner.phone_users {
                users.push(u.clone());
            }
            if users.is_empty() {
                return Err(UError::NotReady {
                    reason: "no users to register; complete Apple ID login or phone auth first".to_string(),
                });
            }
            delegate_stage(&self.delegate, ULoginStage::RegisteringIds);
            let (new_users, alert) = RUNTIME
                .block_on(api::register_ids(inner.path.clone(), &inner.config, &conn, &inner.identity, users))
                .map_err(login_err)?;
            if let Some(alert) = alert {
                return Ok(URegistrationResult::AppleBlocked {
                    title: alert.title,
                    body: alert.body,
                    action_url: alert.action.as_ref().map(|a| a.url.clone()),
                    action_label: alert.action.as_ref().map(|a| a.button.clone()),
                });
            }
            if let Some(registered) = new_users {
                // register_ids preserves order: apple user first (if any),
                // then phone users by subscription.
                let mut iter = registered.into_iter();
                if inner.apple_user.is_some() {
                    if let Some(u) = iter.next() {
                        inner.apple_user = Some(u);
                    }
                }
                for (slot, u) in inner.phone_users.iter_mut().zip(iter) {
                    slot.1 = u;
                }
            }
        }
        Ok(URegistrationResult::Registered)
    }

    /// Rotate the NGM identity: generates a fresh identity, persists it via
    /// `set_identity`, resets anisette, and tears the session down to
    /// `NeedsLogin` (mirrors Dart `configureHostedDevice`'s reset).
    pub fn set_new_identity(&self) -> Result<(), UError> {
        let mut inner = self.inner.lock().expect("login session lock poisoned");
        let identity = api::new_ngm_identity().map_err(login_err)?;
        RUNTIME.block_on(api::set_identity(inner.path.clone(), &inner.config, &identity));
        api::reset_anisette(inner.path.clone());
        if let Some(conn) = inner.conn.take() {
            api::close_aps(&conn);
        }
        inner.identity = identity;
        inner.cached_push = None;
        inner.anisette = None;
        inner.circle = None;
        inner.receiver = None;
        inner.idms = None;
        inner.account = None;
        inner.apple_user = None;
        inner.phone_users.clear();
        inner.sms_opts.clear();
        inner.update_finish = None;
        inner.state = LoginState::NeedsLogin;
        Ok(())
    }

    /// Tear down and re-establish the APS connection with a fresh push token
    /// (required before SMS-gateway phone registration, like Dart's PNR
    /// flow). Kept: account, users, login state.
    pub fn reset_connection(&self) -> Result<(), UError> {
        let mut inner = self.inner.lock().expect("login session lock poisoned");
        if let Some(conn) = inner.conn.take() {
            api::close_aps(&conn);
        }
        inner.anisette = None;
        inner.cached_push = None;
        inner.circle = None;
        inner.receiver = None;
        inner.idms = None;
        connect_locked(&self.delegate, &mut inner)
    }
}

/// Small helper so `delegate.on_stage(...)` reads uniformly in locked scopes.
fn delegate_stage(delegate: &Arc<dyn ULoginDelegate>, stage: ULoginStage) {
    delegate.on_stage(stage);
}

#[uniffi::export]
impl NativePushState {
    /// Tear down the push connection and (with `logout`) deregister from
    /// iMessage and clear the saved Apple account. Hardware validation
    /// data is kept (`reset_hw = false`) so re-login doesn't need new
    /// validation. After this the state object is dead; the caller
    /// re-inits via `init_native` after a fresh login.
    pub fn teardown(&self, logout: bool) -> Result<(), UError> {
        let state = self.shared();
        let account = state.icloud_services.as_ref().map(|s| s.account.clone());
        RUNTIME
            .block_on(api::reset_state(
                &state.cancel_poll,
                state.conf_dir.clone(),
                &state.os_config,
                &state.conn,
                account,
                false,
                logout,
            ))
            .map_err(|e| UError::Failed { reason: e.to_string() })
    }

    /// All handles (emails + phone numbers) registered for this account.
    /// The intake layer uses these to decide `isFromMe`.
    pub fn get_handles(&self) -> Result<Vec<String>, UError> {
        RUNTIME
            .block_on(api::get_handles(&self.shared().client))
            .map_err(|e| UError::Failed { reason: e.to_string() })
    }

    /// Only the tel: handles registered for this account.
    pub fn get_my_phone_handles(&self) -> Result<Vec<String>, UError> {
        RUNTIME
            .block_on(api::get_my_phone_handles(&self.shared().client))
            .map_err(|e| UError::Failed { reason: e.to_string() })
    }

    /// IDS registration health of the live client (drives the
    /// "registering..." / retry UI).
    pub fn get_regstate(&self) -> Result<URegisterState, UError> {
        RUNTIME
            .block_on(api::get_regstate(&self.shared().client))
            .map(conv_regstate)
            .map_err(|e| UError::Failed { reason: e.to_string() })
    }
}

// ---------------------------------------------------------------------------
// Batch 3: attachments, edit/unsend, group operations
// ---------------------------------------------------------------------------
//
// Mirrors the Dart surfaces in lib/services/rustpush/rustpush_service.dart:
// - incoming download: `downloadAttachment` (writes to `path`, progress) —
//   Kotlin passes the plist-XML blob persisted alongside the attachment.
// - outgoing upload: `uploadAttachment` -> `Attachment::new_mmcs`, then a
//   NormalMessage carrying the Attachment part (`sendAttachment` here does
//   both in one call; `upload_attachment` exposes the upload alone so the
//   XML can be persisted before the message is sent, like Dart did).
// - edit/unsend: EditMessage/UnsendMessage via `new_msg` (same shape the
//   Dart `edit`/`unsend` overrides build).
// - group ops: RenameMessage / ChangeParticipantMessage (Dart
//   `renameChat`/`chatParticipant`/`leaveChat`) and IconChangeMessage fed by
//   an `uploadMmcs` upload (Dart `setChatIcon`/`deleteChatIcon`).
//
// `group_version` is tracked by Kotlin (bump it by one on every group
// mutation, starting from the version of the last incoming
// ChangeParticipants/IconChange message — mirrors Dart's
// `chat.groupVersion = (chat.groupVersion ?? -1) + 1`).

use std::io::{Cursor, Seek, Write};
use std::path::Path;

use rustpush::{Attachment, AttachmentType, MMCSFile, TextFormat};

/// Byte-level progress for attachment transfers. Mirrors FRB's
/// `TransferProgress` stream events. `total` may be 0 when the size is not
/// (yet) known. Callbacks fire synchronously from inside the transfer —
/// treat the thread as unspecified and never re-enter Rust from one.
#[uniffi::export(with_foreign)]
pub trait UProgressCallback: Send + Sync + Debug {
    fn on_progress(&self, done: u64, total: u64);
}

fn progress_cb(progress: Option<Arc<dyn UProgressCallback>>) -> impl FnMut(usize, usize) + Send + Sync + 'static {
    move |done, total| {
        if let Some(cb) = progress.as_ref() {
            cb.on_progress(done as u64, total as u64);
        }
    }
}

fn attachment_from_xml(xml: &str) -> Result<Attachment, UError> {
    plist::from_reader_xml(Cursor::new(xml))
        .map_err(|e| UError::InvalidArgument { reason: format!("invalid attachment data: {e}") })
}

fn mmcs_from_xml(xml: &str) -> Result<MMCSFile, UError> {
    plist::from_reader_xml(Cursor::new(xml))
        .map_err(|e| UError::InvalidArgument { reason: format!("invalid mmcs file data: {e}") })
}

fn to_plist_xml<T: Serialize>(value: &T) -> Result<String, UError> {
    let mut buf = Vec::new();
    plist::to_writer_xml(Cursor::new(&mut buf), value)
        .map_err(|e| UError::Failed { reason: format!("failed to serialize plist: {e}") })?;
    String::from_utf8(buf).map_err(|e| UError::Failed { reason: format!("plist was not utf-8: {e}") })
}

fn create_dest(dest_path: &str) -> Result<std::fs::File, UError> {
    let path = Path::new(dest_path);
    if let Some(prefix) = path.parent() {
        if !prefix.as_os_str().is_empty() {
            std::fs::create_dir_all(prefix)
                .map_err(|e| UError::Failed { reason: format!("failed to create directory {}: {e}", prefix.display()) })?;
        }
    }
    std::fs::File::create(path)
        .map_err(|e| UError::Failed { reason: format!("failed to create {dest_path}: {e}") })
}

/// Upload a local file to MMCS as an iMessage attachment (the
/// `Attachment::new_mmcs` path from api.rs `upload_attachment`). Returns the
/// opaque attachment; persist it with `UAttachment::save_attachment` so the
/// transfer survives restarts, or send it right away with `send_attachment`.
fn upload_attachment_inner(
    conn: &rustpush::APSConnection,
    file_path: String,
    mime: String,
    uti: String,
    name: Option<String>,
    progress: Option<Arc<dyn UProgressCallback>>,
) -> Result<Attachment, UError> {
    let path = Path::new(&file_path);
    let mut file = std::fs::File::open(path)
        .map_err(|e| UError::InvalidArgument { reason: format!("cannot open {}: {e}", path.display()) })?;
    let prepared = RUNTIME
        .block_on(MMCSFile::prepare_put(&mut file))
        .map_err(|e| UError::Failed { reason: format!("failed to prepare attachment: {e}") })?;
    file.rewind()
        .map_err(|e| UError::Failed { reason: format!("failed to rewind {}: {e}", path.display()) })?;
    let name = name.unwrap_or_else(|| {
        path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_else(|| "attachment".to_string())
    });
    RUNTIME
        .block_on(Attachment::new_mmcs(conn, &prepared, file, &mime, &uti, &name, progress_cb(progress)))
        .map_err(|e| UError::Failed { reason: format!("attachment upload failed: {e}") })
}

/// UPart -> MessagePart (needed for edit-message parts coming from Kotlin).
fn back_part(p: UPart) -> Result<MessagePart, UError> {
    match p {
        UPart::Text { text, format_json } => {
            let format = if format_json.is_empty() {
                TextFormat::default()
            } else {
                serde_json::from_str(&format_json)
                    .map_err(|e| UError::InvalidArgument { reason: format!("bad format json: {e}") })?
            };
            Ok(MessagePart::Text(text, format))
        }
        UPart::Attachment { xml, .. } => Ok(MessagePart::Attachment(attachment_from_xml(&xml)?)),
        UPart::Mention { mention, text } => Ok(MessagePart::Mention(mention, text)),
        UPart::Object { json } => Ok(MessagePart::Object(json)),
    }
}

fn back_parts(parts: Vec<UIndexedPart>) -> Result<MessageParts, UError> {
    let mut out = Vec::with_capacity(parts.len());
    for ip in parts {
        let part = back_part(ip.part)?;
        out.push(IndexedMessagePart {
            part,
            idx: ip.idx.map(|i| i as usize),
            ext: None,
        });
    }
    Ok(MessageParts(out))
}

/// Opaque handle to a rustpush attachment (restored incoming metadata or a
/// fresh `upload_attachment` result). Persist across restarts via the
/// plist-XML round trip `save_attachment` / `restore_attachment` — the same
/// blob the Dart app kept under `attachment.metadata["rustpush"]`.
#[derive(uniffi::Object)]
pub struct UAttachment {
    inner: Attachment,
}

/// Parse a persisted attachment (plist XML from `UPart::Attachment.xml` or
/// `UAttachment::save_attachment`). Mirrors api.rs `restore_attachment`.
#[uniffi::export]
pub fn restore_attachment(xml: String) -> Result<Arc<UAttachment>, UError> {
    Ok(Arc::new(UAttachment { inner: attachment_from_xml(&xml)? }))
}

#[uniffi::export]
impl UAttachment {
    pub fn uti(&self) -> String {
        self.inner.uti_type.clone()
    }

    pub fn mime(&self) -> String {
        self.inner.mime.clone()
    }

    pub fn name(&self) -> String {
        self.inner.name.clone()
    }

    /// Part index this attachment occupies in its message.
    pub fn part_index(&self) -> u64 {
        self.inner.part
    }

    /// Live-photo / iris flag.
    pub fn iris(&self) -> bool {
        self.inner.iris
    }

    /// Whether the attachment payload is embedded inline (already have the
    /// bytes — no MMCS transfer needed; `total_size` is the payload length).
    pub fn is_inline(&self) -> bool {
        matches!(self.inner.a_type, AttachmentType::Inline(_))
    }

    /// Transfer size in bytes (inline payload length or MMCS file size).
    pub fn total_size(&self) -> u64 {
        self.inner.get_size() as u64
    }

    /// Serialize for persistence (mirrors api.rs `save_attachment`).
    pub fn save_attachment(&self) -> Result<String, UError> {
        to_plist_xml(&self.inner)
    }
}

#[uniffi::export]
impl NativePushState {
    /// Download an incoming attachment to `dest_path` (Kotlin chose the
    /// path; parent directories are created). Mirrors the api.rs
    /// `download_attachment` sink loop, including inline attachments (bytes
    /// written straight to the file).
    pub fn download_attachment(
        &self,
        attachment: Arc<UAttachment>,
        dest_path: String,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<(), UError> {
        let mut file = create_dest(&dest_path)?;
        RUNTIME
            .block_on(attachment.inner.get_attachment(&self.shared().conn, &mut file, progress_cb(progress)))
            .map_err(|e| UError::Failed { reason: format!("attachment download failed: {e}") })?;
        file.flush().map_err(|e| UError::Failed { reason: format!("failed to flush {dest_path}: {e}") })?;
        Ok(())
    }

    /// Download a bare MMCS file (e.g. a group icon from
    /// `UMessage.IconChange.icon_xml`) to `dest_path`.
    pub fn download_mmcs(
        &self,
        mmcs_xml: String,
        dest_path: String,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<(), UError> {
        let mmcs = mmcs_from_xml(&mmcs_xml)?;
        let mut file = create_dest(&dest_path)?;
        RUNTIME
            .block_on(mmcs.get_attachment(&self.shared().conn, &mut file, progress_cb(progress)))
            .map_err(|e| UError::Failed { reason: format!("mmcs download failed: {e}") })?;
        file.flush().map_err(|e| UError::Failed { reason: format!("failed to flush {dest_path}: {e}") })?;
        Ok(())
    }

    /// Upload a local file to MMCS without sending a message (api.rs
    /// `upload_attachment`). Persist the result XML before sending if the
    /// send may be retried after a restart.
    pub fn upload_attachment(
        &self,
        file_path: String,
        mime: String,
        uti: String,
        name: Option<String>,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<Arc<UAttachment>, UError> {
        Ok(Arc::new(UAttachment {
            inner: upload_attachment_inner(&self.shared().conn, file_path, mime, uti, name, progress)?,
        }))
    }

    /// Upload a local file and send it as an attachment message in one call
    /// (the Dart `sendAttachment` flow). `text` is an optional caption part
    /// sent before the attachment. Returns the staged MessageInst; `id` is
    /// the staging GUID to persist.
    pub fn send_attachment(
        &self,
        conversation: UConversation,
        sender: String,
        file_path: String,
        text: Option<String>,
        mime: String,
        uti: String,
        name: Option<String>,
        reply_guid: Option<String>,
        reply_part: Option<String>,
        effect: Option<String>,
        subject: Option<String>,
        voice: bool,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<UMessageInst, UError> {
        let attachment =
            upload_attachment_inner(&self.shared().conn, file_path, mime, uti, name, progress)?;
        let mut parts: Vec<IndexedMessagePart> = Vec::new();
        if let Some(text) = text.filter(|t| !t.is_empty()) {
            parts.push(IndexedMessagePart {
                part: MessagePart::Text(text, TextFormat::default()),
                idx: None,
                ext: None,
            });
        }
        parts.push(IndexedMessagePart {
            part: MessagePart::Attachment(attachment),
            idx: None,
            ext: None,
        });
        let mut normal = NormalMessage::new(String::new(), MessageType::IMessage);
        normal.parts = MessageParts(parts);
        normal.reply_guid = reply_guid;
        normal.reply_part = reply_part;
        normal.effect = effect;
        normal.subject = subject;
        normal.voice = voice;
        let inst = RUNTIME.block_on(api::new_msg(
            back_conversation(conversation),
            sender,
            Message::Message(normal),
        ));
        send_inst(self.shared(), inst)
    }

    /// Edit a previously-sent message part (Dart `edit`). `to_uuid` is the
    /// original message GUID, `edit_part` the part index being replaced,
    /// `new_parts` the full replacement part list (text/mention parts with
    /// optional formatting; attachment parts reference an already-uploaded
    /// attachment via their `xml`). No progress callback: nothing is
    /// transferred.
    pub fn edit_message(
        &self,
        conversation: UConversation,
        sender: String,
        to_uuid: String,
        edit_part: u64,
        new_parts: Vec<UIndexedPart>,
    ) -> Result<UMessageInst, UError> {
        let msg = Message::Edit(EditMessage {
            tuuid: to_uuid,
            edit_part,
            new_parts: back_parts(new_parts)?,
        });
        let inst = RUNTIME.block_on(api::new_msg(back_conversation(conversation), sender, msg));
        send_inst(self.shared(), inst)
    }

    /// Unsend (remove for everyone) a previously-sent message part
    /// (Dart `unsend`). `to_uuid` is the original message GUID, `edit_part`
    /// the part index to retract.
    pub fn unsend_message(
        &self,
        conversation: UConversation,
        sender: String,
        to_uuid: String,
        edit_part: u64,
    ) -> Result<UMessageInst, UError> {
        let msg = Message::Unsend(UnsendMessage { tuuid: to_uuid, edit_part });
        let inst = RUNTIME.block_on(api::new_msg(back_conversation(conversation), sender, msg));
        send_inst(self.shared(), inst)
    }

    /// Rename a group chat (Dart `renameChat`).
    pub fn rename_chat(
        &self,
        conversation: UConversation,
        sender: String,
        new_name: String,
    ) -> Result<UMessageInst, UError> {
        let msg = Message::RenameMessage(RenameMessage { new_name });
        let inst = RUNTIME.block_on(api::new_msg(back_conversation(conversation), sender, msg));
        send_inst(self.shared(), inst)
    }

    /// Set the full participant list of a group (add/remove inferred by
    /// comparison, exactly like rustpush/Dart `chatParticipant`). Pass every
    /// participant including `sender`, formatted+prefixed
    /// (`tel:+1...` / `mailto:...`). Bump `group_version` by one.
    pub fn change_participants(
        &self,
        conversation: UConversation,
        sender: String,
        new_participants: Vec<String>,
        group_version: u64,
    ) -> Result<UMessageInst, UError> {
        let msg = Message::ChangeParticipants(ChangeParticipantMessage {
            new_participants,
            group_version,
        });
        let inst = RUNTIME.block_on(api::new_msg(back_conversation(conversation), sender, msg));
        send_inst(self.shared(), inst)
    }

    /// Leave a group chat: sends ChangeParticipants with `sender` removed
    /// (Dart `leaveChat`). The removal matches the sender with or without
    /// its `tel:`/`mailto:` prefix.
    pub fn leave_chat(
        &self,
        conversation: UConversation,
        sender: String,
        group_version: u64,
    ) -> Result<UMessageInst, UError> {
        let stripped = sender.trim_start_matches("tel:").trim_start_matches("mailto:");
        let new_participants: Vec<String> = conversation
            .participants
            .iter()
            .filter(|p| {
                let ps = p.trim_start_matches("tel:").trim_start_matches("mailto:");
                ps != stripped
            })
            .cloned()
            .collect();
        if new_participants.len() == conversation.participants.len() {
            return Err(UError::InvalidArgument {
                reason: format!("cannot leave chat: sender {sender} is not a participant"),
            });
        }
        let msg = Message::ChangeParticipants(ChangeParticipantMessage {
            new_participants,
            group_version,
        });
        let inst = RUNTIME.block_on(api::new_msg(back_conversation(conversation), sender, msg));
        send_inst(self.shared(), inst)
    }

    /// Set the group photo: uploads the local image to MMCS (Dart
    /// `setChatIcon`, api.rs `upload_mmcs`) and sends the IconChange
    /// message. The file should be a 570x570 PNG.
    pub fn set_group_icon(
        &self,
        conversation: UConversation,
        sender: String,
        file_path: String,
        group_version: u64,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<UMessageInst, UError> {
        let path = Path::new(&file_path);
        let mut file = std::fs::File::open(path)
            .map_err(|e| UError::InvalidArgument { reason: format!("cannot open {}: {e}", path.display()) })?;
        let prepared = RUNTIME
            .block_on(MMCSFile::prepare_put(&mut file))
            .map_err(|e| UError::Failed { reason: format!("failed to prepare group icon: {e}") })?;
        file.rewind()
            .map_err(|e| UError::Failed { reason: format!("failed to rewind {}: {e}", path.display()) })?;
        let mmcs = RUNTIME
            .block_on(MMCSFile::new(&self.shared().conn, &prepared, file, progress_cb(progress)))
            .map_err(|e| UError::Failed { reason: format!("group icon upload failed: {e}") })?;
        let msg = Message::IconChange(IconChangeMessage { file: Some(mmcs), group_version });
        let inst = RUNTIME.block_on(api::new_msg(back_conversation(conversation), sender, msg));
        send_inst(self.shared(), inst)
    }

    /// Remove the group photo (Dart `deleteChatIcon`): IconChange with no
    /// attached file.
    pub fn remove_group_icon(
        &self,
        conversation: UConversation,
        sender: String,
        group_version: u64,
    ) -> Result<UMessageInst, UError> {
        let msg = Message::IconChange(IconChangeMessage { file: None, group_version });
        let inst = RUNTIME.block_on(api::new_msg(back_conversation(conversation), sender, msg));
        send_inst(self.shared(), inst)
    }
}

// ---------------------------------------------------------------------------
// Batch 4: CloudKit message-history sync
// ---------------------------------------------------------------------------
//
// Mirrors the download half of the Dart `doCloudKitSyncPrivate` loop
// (lib/services/rustpush/rustpush_service.dart) over the FRB surface in
// api.rs (`sync_chats` / `sync_messages` / `delete_chats` / `delete_messages`):
//
// - CloudKit zones page with an opaque continuation token (bytes). The
//   caller OWNS the token: pass the previous page's `next_cursor` back in
//   and persist it only after the page was applied (the Dart app kept them
//   as base64 prefs: chatSyncToken / messageSyncToken). A page ends when the
//   zone status reaches 3 (`more == false`).
// - Records arrive as `record_id -> Option<T>` maps: `None` is a tombstone
//   (the record was deleted in iCloud — delete the local row matched by
//   ckRecordId).
// - Deletions the app performed locally must be pushed BEFORE pulling, or
//   the pull resurrects them (Dart flushed its `*DeletionIds-1` pref queues
//   first) — hence `delete_chats_remote` / `delete_messages_remote`.
// - Two drive styles: pull pages yourself through `sync_chats_page` /
//   `sync_messages_page` (what core's CloudSyncManager does, so cursors and
//   progress live in Kotlin), or use the coarse `sync_history` driver that
//   runs both zones in Rust and streams records + running counts through
//   `USyncPageCallback` (cooperatively cancellable between pages).
//
// The up/upload half (save_chats / save_messages, the Dart `uploadMessages`
// flow) is deliberately NOT part of batch 4 — this surface only backfills /
// incrementally updates local history from iCloud.

use rustpush::cloud_messages::{CloudChat, CloudMessage, CloudMessagesClient, MessageSummaryInfo};
use rustpush::{coder_decode_flattened, NSAttributedString, NSString};

/// Availability of CloudKit message-history sync on the live state.
#[derive(uniffi::Enum)]
pub enum USyncState {
    /// iCloud services + the cloud-messages client are live; sync can run.
    Available,
    /// No Apple/iCloud account on this state (finish login first).
    NeedsLogin,
    /// Account exists but the CloudKit messages stack is unavailable (no
    /// keychain — chat/message records could not be decrypted).
    NotEnabled,
}

/// Which cursors `sync_history` starts from.
#[derive(uniffi::Enum)]
pub enum USyncMode {
    /// Backfill: ignore stored cursors, start both zones from scratch.
    Full,
    /// Resume from the cursors passed in (periodic / on-demand refresh).
    Incremental,
}

/// Mirror of the `chatEncryptedv2` CloudKit record — only the fields the
/// persistence layer maps onto the Chat entity.
#[derive(uniffi::Record)]
pub struct UCloudChat {
    /// Chat guid (`iMessage;+/-;chatIdentifier`).
    pub guid: String,
    /// 43 = group chat, 45 = normal.
    pub style: i64,
    pub chat_identifier: String,
    /// Cloud-side chat guid (`Chat.cloudGuid`).
    pub group_id: String,
    /// "iMessage" for real chats; the Dart loop skipped everything else.
    pub service_name: String,
    /// Participant uris (`tel:`/`mailto:` prefixed), including mine.
    pub participants: Vec<String>,
    /// The account handle that last addressed the chat.
    pub last_addressed_handle: String,
    pub display_name: Option<String>,
    /// `CloudProp.pv` — group version; only apply changes when the cloud
    /// version is newer than the local one.
    pub group_version: Option<u32>,
    /// `CloudProp.lastSeenMessageGuid` → `Chat.lastReadMessageGuid`.
    pub last_seen_message_guid: Option<String>,
    /// ns since the Apple epoch (2001-01-01) → `Chat.dbOnlyLatestMessageDate`.
    pub last_read_message_timestamp: i64,
    /// A group-photo asset rides on the record (the image itself downloads
    /// through the attachment batch).
    pub has_group_photo: bool,
}

/// Mirror of the `MessageEncryptedv3` CloudKit record with the gzipped
/// `msgProto` already decoded: flattened text (plain field or attributed
/// body), attachment guids (converted to the local `<msgGuid>_<part>` form),
/// thread/association/receipt fields.
#[derive(uniffi::Record)]
pub struct UCloudMessage {
    pub guid: String,
    /// Chat reference: `iMessage;+/-;chatIdentifier` (contains `;`) or the
    /// chat's cloud guid / rust guid.
    pub chat_id: String,
    /// Sender (`tel:`/`mailto:` handle) — empty for some system messages.
    pub sender: String,
    /// ns since the Apple epoch.
    pub time: i64,
    pub msg_type: i64,
    pub error: i64,
    pub service: String,
    /// Raw `MessageFlags` bits (bit 2 = IS_FROM_ME).
    pub flags_bits: i64,
    /// Flattened text: the plain `text` field, else the attributed-body
    /// string.
    pub text: Option<String>,
    pub subject: Option<String>,
    /// Attributed body contains file-transfer runs.
    pub has_attachments: bool,
    /// Local-form attachment guids (`at_X_Y` cloud form converted).
    pub attachment_guids: Vec<String>,
    pub balloon_bundle_id: Option<String>,
    /// An app balloon payload is attached (raw payload decode is a later
    /// batch; the flag preserves `hasApplePayloadData`).
    pub has_payload_data: bool,
    /// `msgProto.messageSummaryInfo` (edits/retractions) serialized to JSON.
    pub summary_info_json: Option<String>,
    pub effect: Option<String>,
    /// ns since the Apple epoch, 0 mapped to none.
    pub date_read_ns: Option<i64>,
    pub date_delivered_ns: Option<i64>,
    /// Raw associated-message type code (2 sticker, 2000+ tapback,
    /// 3000+ tapback-removed) — the caller maps to the REACTION_* strings.
    pub associated_message_type: Option<i64>,
    pub associated_message_guid: Option<String>,
    /// Parsed from `msgProto2.reply` (`r:<part>:<guid>`).
    pub thread_originator_guid: Option<String>,
    pub thread_originator_part: Option<String>,
    /// From `msgProto4`.
    pub associated_message_emoji: Option<String>,
}

/// One chat-zone change: `chat == None` is a tombstone.
#[derive(uniffi::Record)]
pub struct UChatChange {
    pub record_id: String,
    pub chat: Option<UCloudChat>,
}

/// One message-zone change: `message == None` is a tombstone.
#[derive(uniffi::Record)]
pub struct UMessageChange {
    pub record_id: String,
    pub message: Option<UCloudMessage>,
}

/// A page from the chat zone (`sync_chats`).
#[derive(uniffi::Record)]
pub struct UChatSyncPage {
    pub records: Vec<UChatChange>,
    /// Continuation token for the next page — persist after applying.
    pub next_cursor: Vec<u8>,
    /// CloudKit zone status reached 3 (no more changes pending).
    pub more: bool,
    pub status: i32,
}

/// A page from the message zone (`sync_messages`).
#[derive(uniffi::Record)]
pub struct UMessageSyncPage {
    pub records: Vec<UMessageChange>,
    pub next_cursor: Vec<u8>,
    pub more: bool,
    pub status: i32,
}

/// Totals for one `sync_history` run.
#[derive(uniffi::Record)]
pub struct USyncSummary {
    pub chats_done: u64,
    pub chat_tombstones: u64,
    pub messages_done: u64,
    pub message_tombstones: u64,
    pub duration_ms: u64,
    /// Stopped early because `keep_going` returned false.
    pub cancelled: bool,
}

/// `sync_history` result: summary plus the (possibly mid-run) cursors to
/// persist so an interrupted run resumes where it stopped.
#[derive(uniffi::Record)]
pub struct USyncOutcome {
    pub summary: USyncSummary,
    pub chat_cursor: Vec<u8>,
    pub message_cursor: Vec<u8>,
}

/// Page callback for the coarse `sync_history` driver. Methods run
/// synchronously on the thread that called `sync_history`, between pages —
/// persist/emit there, but never re-enter Rust from inside them.
#[uniffi::export(with_foreign)]
pub trait USyncPageCallback: Send + Sync + Debug {
    /// One page of changes with running totals. Chat pages arrive first
    /// (records contain only `USyncRecord::Chat`), then message pages.
    fn on_page(&self, records: Vec<USyncRecord>, chats_done: u64, messages_done: u64);
    /// Cooperative cancellation — checked before every page. Return false
    /// to stop the run (already-received pages stay applied; the outcome
    /// carries the cursors reached).
    fn keep_going(&self) -> bool;
}

/// A single changed record inside a `sync_history` page.
#[derive(uniffi::Enum)]
pub enum USyncRecord {
    Chat { record_id: String, chat: Option<UCloudChat> },
    Message { record_id: String, message: Option<UCloudMessage> },
}

fn cloud_messages_client(state: &SharedPushState) -> Result<Arc<CloudMessagesClient<DefaultAnisetteProvider>>, UError> {
    state
        .icloud_services
        .as_ref()
        .and_then(|s| s.cloud_messages_client.clone())
        .ok_or_else(|| {
            UError::NotReady { reason: "iCloud message sync unavailable: no iCloud account or keychain".to_string() }
        })
}

fn conv_chat(c: &CloudChat) -> UCloudChat {
    UCloudChat {
        guid: c.guid.clone(),
        style: c.style,
        chat_identifier: c.chat_identifier.clone(),
        group_id: c.group_id.clone(),
        service_name: c.service_name.clone(),
        participants: c.participants.iter().map(|p| p.uri.clone()).collect(),
        last_addressed_handle: c.last_addressed_handle.clone(),
        display_name: c.display_name.clone(),
        group_version: c.properties.as_ref().and_then(|p| p.pv),
        last_seen_message_guid: c.properties.as_ref().and_then(|p| p.last_seen_message_guid.clone()),
        last_read_message_timestamp: c.last_read_message_timestamp,
        has_group_photo: c.group_photo.is_some(),
    }
}

/// Dart `convertAttachmentGuid`: cloud `at_<msgGuid>_<part>` -> local
/// `<msgGuid>_<part>`.
fn convert_attachment_guid(guid: &str) -> String {
    if guid.starts_with("at") {
        let items: Vec<&str> = guid.split('_').collect();
        if items.len() >= 3 {
            return format!("{}_{}", items[2], items[1]);
        }
    }
    guid.to_string()
}

/// Decode a streamtyped attributed body into (text, attachment guids).
/// Decoder panics on malformed input — caught per value, matching the Dart
/// side's tolerance for odd payloads.
fn decode_attributed(data: &[u8]) -> Option<(String, Vec<String>)> {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let mut text = String::new();
        let mut attachments: Vec<String> = Vec::new();
        for value in coder_decode_flattened(data) {
            let decoded = match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                NSAttributedString::decode(&value)
            })) {
                Ok(d) => d,
                Err(_) => continue,
            };
            if text.is_empty() {
                text = decoded.text.clone();
            }
            for (_len, dict) in &decoded.ranges {
                if let Some(guid_value) = dict.0.get("__kIMFileTransferGUIDAttributeName") {
                    if let Ok(ns) = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                        NSString::decode(guid_value)
                    })) {
                        attachments.push(convert_attachment_guid(&ns.0));
                    }
                }
            }
        }
        (text, attachments)
    }))
    .ok()
}

/// Dart `applyFromCloud` thread parsing: `msgProto2.reply` holds
/// `r:<part>:<originatorGuid>` -> (guid, part).
fn thread_originators(p2: Option<&rustpush::cloud_messages::GZipWrapper<rustpush::cloud_messages::cloudmessagesp::MessageProto2>>) -> Option<(String, String)> {
    let reply = p2?.0.reply.as_ref()?;
    if !reply.starts_with("r:") {
        return None;
    }
    let parts: Vec<&str> = reply.split(':').collect();
    if parts.len() < 2 {
        return None;
    }
    Some((parts[parts.len() - 1].to_string(), parts[1..parts.len() - 1].join(":")))
}

fn conv_cloud_message(c: &CloudMessage) -> UCloudMessage {
    let proto = &c.msg_proto.0;

    // Flattened text: the plain field wins, else the attributed-body string
    // (most messages only carry the attributed body).
    let mut text = proto.text.clone();
    let mut attachment_guids: Vec<String> = Vec::new();
    if let Some(body) = &proto.attributed_body {
        if let Some((body_text, guids)) = decode_attributed(body) {
            if text.as_deref().unwrap_or("").is_empty() {
                text = Some(body_text);
            }
            attachment_guids = guids;
        }
    }

    let (thread_guid, thread_part) = match thread_originators(c.msg_proto_2.as_ref()) {
        Some((guid, part)) => (Some(guid), Some(part)),
        None => (None, None),
    };

    // Edits/retractions: plist-encoded MessageSummaryInfo -> JSON for the
    // entity's dbMessageSummaryInfo.
    let summary_info_json = proto.message_summary_info.as_ref().and_then(|bytes| {
        plist::from_bytes::<MessageSummaryInfo>(bytes)
            .ok()
            .and_then(|s| serde_json::to_string(&s).ok())
    });

    UCloudMessage {
        guid: c.guid.clone(),
        chat_id: c.chat_id.clone(),
        sender: c.sender.clone(),
        time: c.time,
        msg_type: c.r#type,
        error: c.error,
        service: c.service.clone(),
        flags_bits: c.flags.bits(),
        text,
        subject: proto.subject.clone(),
        has_attachments: !attachment_guids.is_empty(),
        attachment_guids,
        balloon_bundle_id: proto.balloon_bundle_id.clone(),
        has_payload_data: proto.payload_data.is_some(),
        summary_info_json,
        effect: proto.effect.clone(),
        date_read_ns: proto.date_read.filter(|t| *t != 0).map(|t| t as i64),
        date_delivered_ns: proto.date_delivered.filter(|t| *t != 0).map(|t| t as i64),
        associated_message_type: proto.associated_message_type.map(|t| t as i64),
        associated_message_guid: proto.associated_message_guid.clone(),
        thread_originator_guid: thread_guid,
        thread_originator_part: thread_part,
        associated_message_emoji: c.msg_proto_4.as_ref().and_then(|p4| p4.0.associated_message_emoji.clone()),
    }
}

fn sync_err(e: impl std::fmt::Display) -> UError {
    UError::Failed { reason: format!("cloudkit sync failed: {e}") }
}

#[uniffi::export]
impl NativePushState {
    /// Whether CloudKit message-history sync can run on this state.
    pub fn cloud_sync_state(&self) -> USyncState {
        match &self.shared().icloud_services {
            None => USyncState::NeedsLogin,
            Some(services) => {
                if services.cloud_messages_client.is_some() && services.keychain.is_some() {
                    USyncState::Available
                } else {
                    USyncState::NotEnabled
                }
            }
        }
    }

    /// Circle membership check — the Dart sync loop skipped (and disabled
    /// cloud syncing) when the device fell out of the iCloud clique.
    pub fn is_in_clique(&self) -> Result<bool, UError> {
        let keychain = self
            .shared()
            .icloud_services
            .as_ref()
            .and_then(|s| s.keychain.clone())
            .ok_or_else(|| UError::NotReady { reason: "no keychain on this state".to_string() })?;
        Ok(RUNTIME.block_on(api::is_in_clique(&keychain)))
    }

    /// Pull one page of chat changes (`sync_chats`). Pass the previous
    /// page's `next_cursor` (none for the first page); persist the returned
    /// cursor after applying the records. `more == false` ends the zone.
    pub fn sync_chats_page(&self, cursor: Option<Vec<u8>>) -> Result<UChatSyncPage, UError> {
        let client = cloud_messages_client(self.shared())?;
        let (next, items, status) =
            RUNTIME.block_on(api::sync_chats(&client, cursor)).map_err(sync_err)?;
        Ok(UChatSyncPage {
            records: items
                .into_iter()
                .map(|(record_id, chat)| UChatChange { record_id, chat: chat.as_ref().map(conv_chat) })
                .collect(),
            next_cursor: next,
            more: status != 3,
            status,
        })
    }

    /// Pull one page of message changes (`sync_messages`). Same cursor
    /// contract as `sync_chats_page`.
    pub fn sync_messages_page(&self, cursor: Option<Vec<u8>>) -> Result<UMessageSyncPage, UError> {
        let client = cloud_messages_client(self.shared())?;
        let (next, items, status) =
            RUNTIME.block_on(api::sync_messages(&client, cursor)).map_err(sync_err)?;
        Ok(UMessageSyncPage {
            records: items
                .into_iter()
                .map(|(record_id, message)| UMessageChange {
                    record_id,
                    message: message.as_ref().map(conv_cloud_message),
                })
                .collect(),
            next_cursor: next,
            more: status != 3,
            status,
        })
    }

    /// Push local deletions to iCloud BEFORE pulling (`delete_chats`);
    /// otherwise the pull resurrects rows the user removed. Flushes the
    /// caller's pending-delete queues like Dart's `chatDeletionIds-1`.
    pub fn delete_chats_remote(&self, record_ids: Vec<String>) -> Result<(), UError> {
        let client = cloud_messages_client(self.shared())?;
        RUNTIME.block_on(api::delete_chats(&client, &record_ids)).map_err(sync_err)
    }

    /// Push local message deletions to iCloud (`delete_messages`).
    pub fn delete_messages_remote(&self, record_ids: Vec<String>) -> Result<(), UError> {
        let client = cloud_messages_client(self.shared())?;
        RUNTIME.block_on(api::delete_messages(&client, &record_ids)).map_err(sync_err)
    }

    /// Coarse driver: pull both zones (chats, then messages) to completion,
    /// streaming every page's records + running counts through `on_page`.
    /// `mode` picks the start cursors — `Full` ignores the passed cursors,
    /// `Incremental` resumes from them. Runs entirely on the calling thread
    /// (`RUNTIME.block_on` per page); cooperative cancellation is checked
    /// between pages via `keep_going`. Returns the summary and the cursors
    /// reached — persist them either way, treating an EMPTY cursor as
    /// "zone never pulled a page" (i.e. keep the previously stored one).
    /// Per-record failures are the callback's concern — the Rust loop only
    /// aborts on transport errors.
    pub fn sync_history(
        &self,
        chat_cursor: Option<Vec<u8>>,
        message_cursor: Option<Vec<u8>>,
        mode: USyncMode,
        on_page: Arc<dyn USyncPageCallback>,
    ) -> Result<USyncOutcome, UError> {
        let started = std::time::Instant::now();
        let client = cloud_messages_client(self.shared())?;
        let mut summary = USyncSummary {
            chats_done: 0,
            chat_tombstones: 0,
            messages_done: 0,
            message_tombstones: 0,
            duration_ms: 0,
            cancelled: false,
        };

        let mut chat_cursor = if matches!(mode, USyncMode::Full) { None } else { chat_cursor };
        'chats: loop {
            if !on_page.keep_going() {
                summary.cancelled = true;
                break 'chats;
            }
            let (next, items, status) =
                RUNTIME.block_on(api::sync_chats(&client, chat_cursor.clone())).map_err(sync_err)?;
            chat_cursor = Some(next);
            let mut records = Vec::with_capacity(items.len());
            for (record_id, chat) in items {
                match chat {
                    Some(c) => {
                        summary.chats_done += 1;
                        records.push(USyncRecord::Chat { record_id, chat: Some(conv_chat(&c)) });
                    }
                    None => {
                        summary.chat_tombstones += 1;
                        records.push(USyncRecord::Chat { record_id, chat: None });
                    }
                }
            }
            on_page.on_page(records, summary.chats_done, summary.messages_done);
            if status == 3 {
                break 'chats;
            }
        }

        let mut message_cursor = if matches!(mode, USyncMode::Full) { None } else { message_cursor };
        if !summary.cancelled {
            'messages: loop {
                if !on_page.keep_going() {
                    summary.cancelled = true;
                    break 'messages;
                }
                let (next, items, status) = RUNTIME
                    .block_on(api::sync_messages(&client, message_cursor.clone()))
                    .map_err(sync_err)?;
                message_cursor = Some(next);
                let mut records = Vec::with_capacity(items.len());
                for (record_id, message) in items {
                    match message {
                        Some(m) => {
                            summary.messages_done += 1;
                            records.push(USyncRecord::Message { record_id, message: Some(conv_cloud_message(&m)) });
                        }
                        None => {
                            summary.message_tombstones += 1;
                            records.push(USyncRecord::Message { record_id, message: None });
                        }
                    }
                }
                on_page.on_page(records, summary.chats_done, summary.messages_done);
                if status == 3 {
                    break 'messages;
                }
            }
        }

        summary.duration_ms = started.elapsed().as_millis() as u64;
        Ok(USyncOutcome {
            summary,
            chat_cursor: chat_cursor.unwrap_or_default(),
            message_cursor: message_cursor.unwrap_or_default(),
        })
    }
}

// ---------------------------------------------------------------------------
// Batch 5: hardware provisioning (writes hw_info.plist so createLoginSession
// can proceed on a fresh install). Mirrors the Flutter hw_inp.dart flow:
// config -> fresh NGM identity -> setup_push -> SavedHardwareState persisted.
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct UHwExtra {
    pub version: String,
    pub protocol_version: u32,
    pub device_id: String,
    pub icloud_ua: String,
    pub aoskit_version: String,
}

fn provision(config: api::JoinedOSConfig, dir: String) -> Result<(), UError> {
    let identity = api::new_ngm_identity().map_err(|e| UError::Failed { reason: e.to_string() })?;
    let (_, err) = RUNTIME.block_on(api::setup_push(&config, &identity, None, dir));
    match err {
        Some(e) => Err(UError::Failed { reason: e.to_string() }),
        None => Ok(()),
    }
}

/// Provision from raw validation data (517 bytes, 0x02-prefixed) extracted
/// from a real Mac. One-time per install; see the Flutter app's hw_inp flow.
#[uniffi::export]
pub fn provision_from_validation_data(
    dir: String,
    data: Vec<u8>,
    extra: UHwExtra,
) -> Result<(), UError> {
    let hw_extra = api::HwExtra {
        version: extra.version,
        protocol_version: extra.protocol_version,
        device_id: extra.device_id,
        icloud_ua: extra.icloud_ua,
        aoskit_version: extra.aoskit_version,
    };
    let config = api::config_from_validation_data(data, hw_extra)
        .map_err(|e| UError::InvalidArgument { reason: e.to_string() })?;
    provision(config, dir)
}

/// Provision via a hosted relay slot (hw.openbubbles.app-style bridge).
#[uniffi::export]
pub fn provision_from_relay(
    dir: String,
    code: String,
    host: String,
    token: Option<String>,
) -> Result<(), UError> {
    let config = RUNTIME
        .block_on(api::config_from_relay(code, host, &token))
        .map_err(|e| UError::Failed { reason: e.to_string() })?;
    provision(config, dir)
}

/// True when hw_info.plist exists and parses — gates the login UI's
/// provisioning step.
#[uniffi::export]
pub fn has_hardware_config(dir: String) -> bool {
    api::read_hardware(dir).is_some()
}
