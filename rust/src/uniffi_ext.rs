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
//! Design notes:
//! - rustpush's protocol types can't derive UniFFI traits (they live in the
//!   upstream crate), so every type crossing the FFI boundary is mirrored
//!   into a `U*` record/enum here. Core iMessage semantics are mapped
//!   field-by-field; exotic variants carry `serde_json` strings under
//!   `*_json` fields so nothing is lost while Kotlin stays typed for MVP.
//! - Attachment transport details (`AttachmentType::MMCS`/`Inline`) stay in
//!   Rust; Kotlin gets mime/uti/name and drives transfer through the
//!   dedicated attachment APIs (batch 2).
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
    Attachment { part: u64, uti: String, mime: String, name: String, iris: bool },
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
    IconChange { json: String },
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
        Message::IconChange(IconChangeMessage { .. }) => UMessage::IconChange { json: j(m) },
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
