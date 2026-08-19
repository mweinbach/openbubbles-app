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
//! Batch 8: FindMy (devices / following / beacon items), contact posters
//! (transcript + incoming-call parse/pack), the CloudKit upload half
//! (save chats/messages/attachments + group photo via re-uploadable record
//! blobs), profile fetch/set, and the SMS helpers (send via the relay
//! service type, SMS routing targets for the forwarding UI).
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
//! - Most exports are synchronous and drive the global RUNTIME via
//!   `block_on`; Kotlin callers stay off the main thread (they already run
//!   on Dispatchers.IO in the service layer). The hot transfer paths —
//!   sends and attachment up/downloads — are async exports (`suspend fun`
//!   in Kotlin) driven through [drive_ffi], so a long network transfer
//!   suspends the Kotlin coroutine instead of parking one of its threads.

use crate::api::api::{self, PushMessage, SharedPushState};
use crate::native::NativePushState;
use crate::RUNTIME;
use prost::Message as ProstMessage;
// All message-model types are re-exported at the rustpush crate root.
use rustpush::{
    Balloon, ChangeParticipantMessage, ConversationData, EditMessage, ErrorMessage, ExtensionApp,
    IconChangeMessage, LinkMeta,
    IndexedMessagePart, Message, MessageInst, MessagePart, MessageParts, MessageType,
    MoveToRecycleBinMessage, NormalMessage, OperatedChat, PartExtension, PermanentDeleteMessage,
    ReactMessage, ReactMessageType, Reaction, RenameMessage, ReportMessage, ScheduleMode,
    SetTranscriptBackgroundMessage,
    ShareProfileMessage, UnsendMessage, UpdateExtensionMessage, UpdateProfileMessage,
    UpdateProfileSharingMessage,
};
use serde::Serialize;
use sha2::{Digest, Sha256};

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
    /// Serialized `PartExtension`, including sticker placement metadata.
    pub ext_json: Option<String>,
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
        /// Attachment/object body for sticker and app-extension reactions.
        parts: Vec<UIndexedPart>,
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
    SetTranscriptBackground {
        json: String,
        version: u64,
        chat_id: Option<String>,
        remove: bool,
        /// Serialized MMCS descriptor for the poster payload when setting.
        mmcs_xml: Option<String>,
    },
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
    FaceTime { message: UFtMessage },
    StatusUpdate { user: String, mode: Option<String>, allowed: bool },
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
            ext_json: ip.ext.as_ref().map(j),
        })
        .collect()
}

fn reaction_parts(reaction: &ReactMessageType) -> Vec<UIndexedPart> {
    match reaction {
        ReactMessageType::React {
            reaction: Reaction::Sticker { body, .. },
            ..
        }
        | ReactMessageType::Extension { body, .. } => conv_parts(body),
        _ => Vec::new(),
    }
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
            app_json: n.app.as_ref().map(app_json),
            link_json: n.link_meta.as_ref().map(j),
        },
        Message::React(r) => UMessage::React {
            to_uuid: r.to_uuid.clone(),
            to_part: r.to_part,
            reaction_json: j(&r.reaction),
            to_text: r.to_text.clone(),
            parts: reaction_parts(&r.reaction),
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
        Message::SetTranscriptBackground(background) => match background {
            SetTranscriptBackgroundMessage::Remove { bid, chat_id, .. } => {
                UMessage::SetTranscriptBackground {
                    json: j(background),
                    version: *bid,
                    chat_id: chat_id.clone(),
                    remove: true,
                    mmcs_xml: None,
                }
            }
            SetTranscriptBackgroundMessage::Set { bid, chat_id, .. } => {
                UMessage::SetTranscriptBackground {
                    json: j(background),
                    version: *bid,
                    chat_id: chat_id.clone(),
                    remove: false,
                    mmcs_xml: background
                        .to_mmcs()
                        .and_then(|file| to_plist_xml(&file).ok()),
                }
            }
        },
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct UAppJson<'a> {
    app_name: &'a str,
    app_id: Option<u64>,
    bundle_id: &'a str,
    url: Option<&'a str>,
    session: Option<&'a str>,
    ld_text: Option<&'a str>,
    is_live: bool,
    layout: Option<&'a rustpush::BalloonLayout>,
}

fn app_json(app: &rustpush::ExtensionApp) -> String {
    let balloon = app.balloon.as_ref();
    j(&UAppJson {
        app_name: &app.name,
        app_id: app.app_id,
        bundle_id: &app.bundle_id,
        url: balloon.map(|value| value.url.as_str()),
        session: balloon.and_then(|value| value.session.as_deref()),
        ld_text: balloon.and_then(|value| value.ld_text.as_deref()),
        is_live: balloon.is_some_and(|value| value.is_live),
        layout: balloon.and_then(|value| value.layout.as_ref()),
    })
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
        PushMessage::FaceTime(f) => UPushMessage::FaceTime { message: conv_ft(f) },
        PushMessage::StatusUpdate(rustpush::statuskit::StatusKitMessage::StatusChanged {
            user,
            mode,
            allowed,
        }) => UPushMessage::StatusUpdate {
            user: user.clone(),
            mode: mode.clone(),
            allowed: *allowed,
        },
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
pub fn mark_journal_attempt(id: u64, success: bool) -> Result<(), UError> {
    RUNTIME.block_on(api::mark_queue_attempt(id, success)).map_err(login_err)
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

async fn send_inst_on(state: &SharedPushState, inst: MessageInst) -> Result<UMessageInst, UError> {
    // Convert before the send consumes (and mutates) the instance; the
    // caller-visible value has always been the pre-send snapshot.
    let converted = conv_inst(&inst);
    api::send(&state.client, &state.local_broadcast, inst)
        .await
        .map(|_| converted)
        .map_err(|e| UError::SendFailed { reason: e.to_string() })
}

fn send_inst(state: &SharedPushState, inst: MessageInst) -> Result<UMessageInst, UError> {
    RUNTIME.block_on(send_inst_on(state, inst))
}

/// Stamp a new envelope for `msg` and send it — the body every simple
/// async send export drives through [drive_ffi].
async fn send_msg_on(
    state: Arc<SharedPushState>,
    conversation: UConversation,
    sender: String,
    msg: Message,
) -> Result<UMessageInst, UError> {
    let inst = api::new_msg(back_conversation(conversation), sender, msg).await;
    send_inst_on(&state, inst).await
}

/// Runs an async export's work without occupying a runtime worker: the
/// future is driven to completion from a blocking-pool thread — the same
/// execution profile the sync exports have (a parked thread polling via
/// `block_on`), except the parked thread is Rust's, so the Kotlin caller
/// suspends instead of blocking one of its Dispatchers.IO threads for the
/// whole network transfer.
async fn drive_ffi<T, F>(task: F) -> Result<T, UError>
where
    T: Send + 'static,
    F: std::future::Future<Output = Result<T, UError>> + Send + 'static,
{
    // Spawn on RUNTIME's own blocking pool (not the ambient context uniffi's
    // async wrapper provides) so this never depends on who polls the export.
    RUNTIME
        .spawn_blocking(move || RUNTIME.block_on(task))
        .await
        .unwrap_or_else(|join| Err(UError::Failed { reason: format!("engine task panicked: {join}") }))
}

#[uniffi::export(async_runtime = "tokio")]
impl NativePushState {
    /// Send a plain (optionally formatted-later) text message. Returns the
    /// staged MessageInst — `id` is the staging GUID to persist.
    pub async fn send_text(
        &self,
        conversation: UConversation,
        sender: String,
        text: String,
        reply_guid: Option<String>,
        reply_part: Option<String>,
        effect: Option<String>,
        subject: Option<String>,
    ) -> Result<UMessageInst, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let mut normal = NormalMessage::new(text, MessageType::IMessage);
            normal.reply_guid = reply_guid;
            normal.reply_part = reply_part;
            normal.effect = effect;
            normal.subject = subject;
            let inst = api::new_msg(
                back_conversation(conversation),
                sender,
                Message::Message(normal),
            ).await;
            send_inst_on(&state, inst).await
        }).await
    }

    /// Same as `send_text`, with Apple scheduled-send metadata.
    pub async fn send_scheduled_text(
        &self,
        conversation: UConversation,
        sender: String,
        text: String,
        reply_guid: Option<String>,
        reply_part: Option<String>,
        effect: Option<String>,
        subject: Option<String>,
        scheduled_ms: u64,
    ) -> Result<UMessageInst, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let mut normal = NormalMessage::new(text, MessageType::IMessage);
            normal.reply_guid = reply_guid;
            normal.reply_part = reply_part;
            normal.effect = effect;
            normal.subject = subject;
            normal.scheduled = Some(ScheduleMode { ms: scheduled_ms, schedule: true });
            let inst = api::new_msg(
                back_conversation(conversation),
                sender,
                Message::Message(normal),
            ).await;
            send_inst_on(&state, inst).await
        }).await
    }

    /// Sends an iMessage app balloon (polls, Find My live location).
    pub async fn send_app(
        &self,
        conversation: UConversation,
        sender: String,
        bundle_id: String,
        app_name: String,
        url: String,
        session: Option<String>,
        ld_text: Option<String>,
    ) -> Result<UMessageInst, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let mut normal = NormalMessage::new(
                ld_text.clone().unwrap_or_default(),
                MessageType::IMessage,
            );
            normal.app = Some(ExtensionApp {
                name: app_name,
                app_id: None,
                bundle_id,
                balloon: Some(Balloon {
                    url,
                    session,
                    layout: None,
                    ld_text,
                    is_live: false,
                    icon: None,
                }),
            });
            let inst = api::new_msg(
                back_conversation(conversation),
                sender,
                Message::Message(normal),
            ).await;
            send_inst_on(&state, inst).await
        }).await
    }

    pub async fn send_parts(
        &self,
        conversation: UConversation,
        sender: String,
        parts: Vec<UIndexedPart>,
        reply_guid: Option<String>,
        reply_part: Option<String>,
        effect: Option<String>,
        subject: Option<String>,
    ) -> Result<UMessageInst, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let mut normal = NormalMessage::new(String::new(), MessageType::IMessage);
            normal.parts = back_parts(parts)?;
            normal.reply_guid = reply_guid;
            normal.reply_part = reply_part;
            normal.effect = effect;
            normal.subject = subject;
            let inst = api::new_msg(
                back_conversation(conversation),
                sender,
                Message::Message(normal),
            ).await;
            send_inst_on(&state, inst).await
        }).await
    }

    pub async fn send_typing(&self, conversation: UConversation, sender: String, typing: bool) -> Result<(), UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let inst = api::new_msg(
                back_conversation(conversation),
                sender,
                Message::Typing(typing, None),
            ).await;
            send_inst_on(&state, inst).await.map(|_| ())
        }).await
    }

    pub async fn send_read(
        &self,
        conversation: UConversation,
        sender: String,
        message_guid: String,
    ) -> Result<(), UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let mut inst = api::new_msg(
                back_conversation(conversation),
                sender,
                Message::Read,
            ).await;
            // Read receipts identify the newest message they acknowledge. A fresh
            // random id is a valid iMessage envelope but cannot update the remote
            // transcript's read state.
            inst.id = message_guid;
            send_inst_on(&state, inst).await.map(|_| ())
        }).await
    }

    /// Send (or remove, with `enable: false`) a tapback.
    /// `reaction_idx`: 0 heart, 1 like, 2 dislike, 3 laugh, 4 emphasize,
    /// 5 question; 6 + `emoji` for custom emoji tapbacks.
    pub async fn send_reaction(
        &self,
        conversation: UConversation,
        sender: String,
        to_uuid: String,
        to_part: Option<u64>,
        reaction_idx: u64,
        emoji: Option<String>,
        to_text: String,
        enable: bool,
    ) -> Result<UMessageInst, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let reaction = reaction_from_idx(reaction_idx, emoji).ok_or(UError::InvalidArgument { reason: "invalid reaction idx".to_string() })?;
            let react = ReactMessage {
                to_uuid,
                to_part,
                reaction: ReactMessageType::React { reaction, enable },
                to_text,
                embedded_profile: None,
            };
            let inst = api::new_msg(
                back_conversation(conversation),
                sender,
                Message::React(react),
            ).await;
            send_inst_on(&state, inst).await
        }).await
    }

    /// Upload an image and attach it as a positional sticker to one message
    /// part. Coordinates are normalized to the target bubble (0..1), rotation
    /// is in radians, and scale is relative to the sticker's natural size.
    pub async fn send_sticker(
        &self,
        conversation: UConversation,
        sender: String,
        to_uuid: String,
        to_part: Option<u64>,
        to_text: String,
        file_path: String,
        mime: String,
        uti: String,
        name: Option<String>,
        msg_width: f64,
        normalized_x: f64,
        normalized_y: f64,
        rotation: f64,
        scale: f64,
        effect_type: i64,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<UMessageInst, UError> {
        if !msg_width.is_finite() || msg_width <= 0.0 {
            return Err(UError::InvalidArgument {
                reason: "sticker message width must be positive".to_string(),
            });
        }
        if !(0.0..=1.0).contains(&normalized_x) || !(0.0..=1.0).contains(&normalized_y) {
            return Err(UError::InvalidArgument {
                reason: "sticker coordinates must be between 0 and 1".to_string(),
            });
        }
        if !scale.is_finite() || !(0.1..=4.0).contains(&scale) || !rotation.is_finite() {
            return Err(UError::InvalidArgument {
                reason: "invalid sticker scale or rotation".to_string(),
            });
        }

        let state = self.shared_arc();
        drive_ffi(async move {
            let source = std::fs::read(&file_path).map_err(|e| UError::InvalidArgument {
                reason: format!("cannot read sticker: {e}"),
            })?;
            let hash = format!("{:x}", Sha256::digest(&source));
            let attachment = upload_attachment_task(
                state.conn.clone(),
                file_path,
                mime,
                uti,
                name,
                progress,
            ).await?;
            let extension = PartExtension::sticker(
                msg_width,
                rotation,
                scale,
                normalized_x,
                normalized_y,
                hash,
                effect_type,
                uuid::Uuid::new_v4().to_string(),
            );
            let react = ReactMessage {
                to_uuid,
                to_part,
                reaction: ReactMessageType::React {
                    reaction: Reaction::Sticker {
                        spec: None,
                        body: MessageParts(vec![IndexedMessagePart {
                            part: MessagePart::Attachment(attachment),
                            idx: Some(0),
                            ext: Some(extension),
                        }]),
                    },
                    enable: true,
                },
                to_text,
                embedded_profile: None,
            };
            let inst = api::new_msg(
                back_conversation(conversation),
                sender,
                Message::React(react),
            ).await;
            send_inst_on(&state, inst).await
        }).await
    }
}

// ---------------------------------------------------------------------------
// Native Settings: iCloud Passwords and Shared Albums
// ---------------------------------------------------------------------------

#[derive(Clone, Copy, uniffi::Enum)]
pub enum UVaultItemKind {
    Password,
    Passkey,
    Code,
    Wifi,
}

#[derive(uniffi::Record)]
pub struct UVaultItem {
    pub id: String,
    pub kind: UVaultItemKind,
    pub title: String,
    pub username: Option<String>,
    pub group_id: Option<String>,
    pub modified_at_ms: u64,
}

#[derive(uniffi::Record)]
pub struct UVaultSecret {
    pub value: String,
    pub expires_at_s: Option<u64>,
}

#[derive(uniffi::Record)]
pub struct UVaultGroupMember {
    pub name: Option<String>,
    pub handle: String,
    pub joined: bool,
    pub current_user: bool,
}

#[derive(uniffi::Record)]
pub struct UVaultGroup {
    pub id: String,
    pub name: String,
    pub owner: bool,
    pub member_count: u64,
    pub members: Vec<UVaultGroupMember>,
}

#[derive(uniffi::Record)]
pub struct UVaultInvite {
    pub id: String,
    pub group_name: String,
    pub inviter: String,
}

#[derive(uniffi::Record)]
pub struct USharedAlbum {
    pub id: String,
    pub name: String,
    pub owner_name: Option<String>,
    pub owner_email: Option<String>,
    pub location: Option<String>,
    pub asset_count: u64,
    pub invitation: bool,
    pub syncing: bool,
    pub sync_status: Option<String>,
}

#[derive(uniffi::Record)]
pub struct USharedAlbumAsset {
    pub id: String,
    pub filename: String,
}

fn native_password_manager(
    state: &SharedPushState,
) -> Result<Arc<rustpush::passwords::PasswordManager<DefaultAnisetteProvider>>, UError> {
    state
        .icloud_services
        .as_ref()
        .and_then(|services| services.passwords.clone())
        .ok_or_else(|| UError::NotReady { reason: "iCloud Passwords unavailable".to_string() })
}

fn normalize_password_share_handle(handle: &str) -> Result<String, UError> {
    let handle = handle.trim();
    if handle.is_empty() {
        return Err(UError::InvalidArgument { reason: "an email address or phone number is required".to_string() });
    }
    if handle.starts_with("mailto:") || handle.starts_with("tel:") {
        return Ok(handle.to_string());
    }
    if handle.contains('@') {
        return Ok(format!("mailto:{handle}"));
    }
    if handle.chars().all(|character| character.is_ascii_digit() || matches!(character, '+' | ' ' | '-' | '(' | ')')) {
        let number = handle.chars().filter(|character| character.is_ascii_digit() || *character == '+').collect::<String>();
        if number.chars().any(|character| character.is_ascii_digit()) {
            return Ok(format!("tel:{number}"));
        }
    }
    Err(UError::InvalidArgument { reason: "enter a valid email address or phone number".to_string() })
}

fn native_shared_albums(
    state: &SharedPushState,
) -> Result<rustpush::sharedstreams::SyncManager<DefaultAnisetteProvider, api::MyFilePackager>, UError> {
    state
        .icloud_services
        .as_ref()
        .and_then(|services| services.sharedstreams.clone())
        .ok_or_else(|| UError::NotReady { reason: "iCloud Shared Albums unavailable".to_string() })
}

#[uniffi::export(async_runtime = "tokio")]
impl NativePushState {
    /// Pull the Passwords, Wi-Fi, and CreditCards Keychain zones and refresh
    /// shared password groups before Kotlin reads the local vault cache.
    pub async fn sync_passwords(&self) -> Result<(), UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let manager = native_password_manager(&state)?;
            api::sync_passwords(&manager, &state.conn)
                .await
                .map_err(|error| UError::Failed {
                    reason: format!("failed to sync iCloud Passwords: {error}"),
                })
        }).await
    }

    pub async fn add_password_totp(
        &self,
        site: String,
        username: String,
        setup: String,
        group_id: Option<String>,
    ) -> Result<(), UError> {
        let site = site.trim().to_string();
        let username = username.trim().to_string();
        if site.is_empty() || username.is_empty() {
            return Err(UError::InvalidArgument { reason: "website and username are required".to_string() });
        }
        let totp = rustpush::passwords::parse_totp_setup(&setup, &site, &username)
            .map_err(|error| UError::InvalidArgument { reason: error.to_string() })?;
        let state = self.shared_arc();
        drive_ffi(async move {
            let manager = native_password_manager(&state)?;
            let criteria = rustpush::passwords::PasswordCriteria { site, account: username };
            manager.set_password_totp(&criteria, totp, group_id).await.map_err(|error| UError::Failed {
                reason: format!("failed to save verification code: {error}"),
            })
        }).await
    }

    pub async fn invite_password_group_member(&self, group_id: String, handle: String) -> Result<(), UError> {
        let handle = normalize_password_share_handle(&handle)?;
        let state = self.shared_arc();
        drive_ffi(async move {
            let manager = native_password_manager(&state)?;
            let (_, groups, _) = api::get_groups(&manager).await.map_err(|error| UError::Failed {
                reason: format!("failed to load password group: {error}"),
            })?;
            let group = groups.get(&group_id).ok_or_else(|| UError::InvalidArgument {
                reason: "password group no longer exists".to_string(),
            })?;
            if !group.is_owner {
                return Err(UError::InvalidArgument { reason: "only the group owner can invite members".to_string() });
            }
            if group.members.iter().any(|member| member.handle == handle) {
                return Err(UError::InvalidArgument { reason: "that person is already in this group".to_string() });
            }
            let available = api::query_handle(&manager, handle.clone()).await.map_err(|error| UError::Failed {
                reason: format!("failed to verify password-sharing recipient: {error}"),
            })?;
            if !available {
                return Err(UError::InvalidArgument { reason: "that address cannot receive iCloud Passwords invitations".to_string() });
            }
            api::invite_user(&manager, group_id, handle).await.map_err(|error| UError::Failed {
                reason: format!("failed to invite password group member: {error}"),
            })
        }).await
    }

    pub async fn remove_password_group_member(&self, group_id: String, handle: String) -> Result<(), UError> {
        let handle = normalize_password_share_handle(&handle)?;
        let state = self.shared_arc();
        drive_ffi(async move {
            let manager = native_password_manager(&state)?;
            let (current_user_id, groups, _) = api::get_groups(&manager).await.map_err(|error| UError::Failed {
                reason: format!("failed to load password group: {error}"),
            })?;
            let group = groups.get(&group_id).ok_or_else(|| UError::InvalidArgument {
                reason: "password group no longer exists".to_string(),
            })?;
            if !group.is_owner {
                return Err(UError::InvalidArgument { reason: "only the group owner can remove members".to_string() });
            }
            let member = group.members.iter().find(|member| member.handle == handle).ok_or_else(|| UError::InvalidArgument {
                reason: "password group member no longer exists".to_string(),
            })?;
            if member.user_id.as_deref() == Some(current_user_id.as_str()) {
                return Err(UError::InvalidArgument { reason: "the owner cannot be removed from the group".to_string() });
            }
            api::remove_user(&manager, group_id, handle).await.map_err(|error| UError::Failed {
                reason: format!("failed to remove password group member: {error}"),
            })
        }).await
    }
}

#[uniffi::export]
impl NativePushState {
    pub fn list_passwords(&self, kind: UVaultItemKind) -> Result<Vec<UVaultItem>, UError> {
        let manager = native_password_manager(self.shared())?;
        let mut items: Vec<UVaultItem> = match kind {
            UVaultItemKind::Password => RUNTIME.block_on(api::get_passwords(&manager))
                .into_iter()
                .map(|(id, (group_id, password))| UVaultItem {
                    id,
                    kind: UVaultItemKind::Password,
                    title: password.srvr,
                    username: Some(password.acct),
                    group_id,
                    modified_at_ms: password.mdat,
                })
                .collect(),
            UVaultItemKind::Code => RUNTIME.block_on(api::get_passwords_meta(&manager))
                .into_iter()
                .filter_map(|(id, (group_id, metadata))| {
                    let data = metadata.get_password_data().ok()?;
                    let totp = data.totp?;
                    Some(UVaultItem {
                        id,
                        kind: UVaultItemKind::Code,
                        title: totp.issuer.unwrap_or_else(|| metadata.srvr.clone()),
                        username: totp.account_name.or(Some(metadata.acct)),
                        group_id,
                        modified_at_ms: metadata.mdat,
                    })
                })
                .collect(),
            UVaultItemKind::Passkey => RUNTIME.block_on(api::get_passkeys(&manager))
                .into_iter()
                .map(|(id, (group_id, passkey))| UVaultItem {
                    id,
                    kind: UVaultItemKind::Passkey,
                    title: passkey.labl,
                    username: None,
                    group_id,
                    modified_at_ms: passkey.mdat,
                })
                .collect(),
            UVaultItemKind::Wifi => RUNTIME.block_on(api::get_wifi_passwords(&manager))
                .into_iter()
                .map(|(id, (group_id, wifi))| UVaultItem {
                    id,
                    kind: UVaultItemKind::Wifi,
                    title: wifi.acct,
                    username: None,
                    group_id,
                    modified_at_ms: wifi.mdat,
                })
                .collect(),
        };
        items.sort_by_key(|item| item.title.to_lowercase());
        Ok(items)
    }

    pub fn reveal_password(&self, id: String, kind: UVaultItemKind) -> Result<UVaultSecret, UError> {
        let manager = native_password_manager(self.shared())?;
        match kind {
            UVaultItemKind::Password => {
                let entries = RUNTIME.block_on(api::get_passwords(&manager));
                let (_, entry) = entries.get(&id).ok_or_else(|| UError::InvalidArgument {
                    reason: "password no longer exists".to_string(),
                })?;
                Ok(UVaultSecret {
                    value: String::from_utf8_lossy(&entry.data).into_owned(),
                    expires_at_s: None,
                })
            }
            UVaultItemKind::Wifi => {
                let entries = RUNTIME.block_on(api::get_wifi_passwords(&manager));
                let (_, entry) = entries.get(&id).ok_or_else(|| UError::InvalidArgument {
                    reason: "Wi-Fi password no longer exists".to_string(),
                })?;
                Ok(UVaultSecret {
                    value: String::from_utf8_lossy(&entry.data).into_owned(),
                    expires_at_s: None,
                })
            }
            UVaultItemKind::Code => {
                let entries = RUNTIME.block_on(api::get_passwords_meta(&manager));
                let (_, entry) = entries.get(&id).ok_or_else(|| UError::InvalidArgument {
                    reason: "verification code no longer exists".to_string(),
                })?;
                let data = entry.get_password_data().map_err(|error| UError::Failed {
                    reason: format!("failed to decode verification code: {error}"),
                })?;
                let totp = data.totp.ok_or_else(|| UError::InvalidArgument {
                    reason: "credential has no verification code".to_string(),
                })?;
                let (value, expires_at_s) = totp.generate_otp().map_err(|error| UError::Failed {
                    reason: format!("failed to generate verification code: {error}"),
                })?;
                Ok(UVaultSecret {
                    value: format!("{:0width$}", value, width = totp.digits as usize),
                    expires_at_s: Some(expires_at_s),
                })
            }
            UVaultItemKind::Passkey => Err(UError::InvalidArgument {
                reason: "passkey private keys cannot be revealed".to_string(),
            }),
        }
    }

    pub fn create_password(
        &self,
        site: String,
        username: String,
        password: String,
        group_id: Option<String>,
    ) -> Result<(), UError> {
        if site.trim().is_empty() || username.trim().is_empty() || password.is_empty() {
            return Err(UError::InvalidArgument {
                reason: "site, username, and password are required".to_string(),
            });
        }
        let manager = native_password_manager(self.shared())?;
        let criteria = rustpush::passwords::PasswordCriteria { site, account: username };
        RUNTIME.block_on(async {
            manager
                .modify_password_entry::<rustpush::passwords::PasswordRawEntry>(
                    &criteria,
                    |entry| entry.data = password.as_bytes().to_vec(),
                    group_id.clone(),
                )
                .await?;
            manager
                .modify_password_entry::<rustpush::passwords::PasswordManagerMeta>(
                    &criteria,
                    |entry| {
                        if let Ok(mut data) = entry.get_password_data() {
                            data.set_last_used(std::time::SystemTime::now());
                            data.change_password(password.clone());
                            if let Ok(encoded) = rustpush::passwords::PasswordManagerMeta::get_data(&data) {
                                entry.data = encoded;
                            }
                        }
                    },
                    group_id,
                )
                .await
        }).map_err(|error| UError::Failed { reason: format!("failed to save password: {error}") })
    }

    pub fn list_password_groups(&self) -> Result<Vec<UVaultGroup>, UError> {
        let manager = native_password_manager(self.shared())?;
        let (current_user_id, groups, _) = RUNTIME.block_on(api::get_groups(&manager)).map_err(|error| UError::Failed {
            reason: format!("failed to list password groups: {error}"),
        })?;
        let mut result = groups.into_iter().map(|(id, group)| {
            let mut members = group.members.into_iter().map(|member| UVaultGroupMember {
                name: member.name,
                handle: member.handle,
                joined: member.is_joined,
                current_user: member.user_id.as_deref() == Some(current_user_id.as_str()),
            }).collect::<Vec<_>>();
            members.sort_by_key(|member| (!member.current_user, member.name.clone().unwrap_or_else(|| member.handle.clone()).to_lowercase()));
            UVaultGroup {
                id,
                name: group.display_name,
                owner: group.is_owner,
                member_count: members.len() as u64,
                members,
            }
        }).collect::<Vec<_>>();
        result.sort_by_key(|group| group.name.to_lowercase());
        Ok(result)
    }

    pub fn list_password_group_invites(&self) -> Result<Vec<UVaultInvite>, UError> {
        let manager = native_password_manager(self.shared())?;
        let (_, _, invites) = RUNTIME.block_on(api::get_groups(&manager)).map_err(|error| UError::Failed {
            reason: format!("failed to list password group invitations: {error}"),
        })?;
        Ok(invites.into_iter().map(|(id, invite)| UVaultInvite {
            id,
            group_name: invite.group_name,
            inviter: invite.invitee_handle,
        }).collect())
    }

    pub fn create_password_group(&self, name: String) -> Result<String, UError> {
        let manager = native_password_manager(self.shared())?;
        RUNTIME.block_on(api::create_group(&manager, name)).map_err(|error| UError::Failed {
            reason: format!("failed to create password group: {error}"),
        })
    }

    pub fn accept_password_group_invite(&self, invite_id: String) -> Result<(), UError> {
        let manager = native_password_manager(self.shared())?;
        RUNTIME.block_on(api::accept_invite(&manager, invite_id)).map_err(|error| UError::Failed {
            reason: format!("failed to accept password group invitation: {error}"),
        })
    }

    pub fn decline_password_group_invite(&self, invite_id: String) -> Result<(), UError> {
        let manager = native_password_manager(self.shared())?;
        RUNTIME.block_on(api::decline_invite(&manager, invite_id)).map_err(|error| UError::Failed {
            reason: format!("failed to decline password group invitation: {error}"),
        })
    }

    pub fn rename_password_group(&self, group_id: String, name: String) -> Result<(), UError> {
        if name.trim().is_empty() {
            return Err(UError::InvalidArgument { reason: "a group name is required".to_string() });
        }
        let manager = native_password_manager(self.shared())?;
        RUNTIME.block_on(api::rename_group(&manager, group_id, name)).map_err(|error| UError::Failed {
            reason: format!("failed to rename password group: {error}"),
        })
    }

    /// Delete an owned group (removes the CloudKit zone) or leave a shared one
    /// (removes our participation). `rustpush` picks the right operation from
    /// whether this device owns the group.
    pub fn delete_password_group(&self, group_id: String) -> Result<(), UError> {
        let manager = native_password_manager(self.shared())?;
        RUNTIME.block_on(api::delete_group(&manager, group_id)).map_err(|error| UError::Failed {
            reason: format!("failed to delete password group: {error}"),
        })
    }

    /// Delete a saved vault item. For a password this removes both the
    /// `com.apple.cfnetwork` credential and its paired
    /// `com.apple.password-manager` metadata, matching how the Passwords app
    /// deletes a login. Deleting a code clears only the TOTP so the saved
    /// password survives; passkeys and Wi-Fi entries remove the single record.
    pub fn delete_password(
        &self,
        id: String,
        kind: UVaultItemKind,
        group_id: Option<String>,
    ) -> Result<(), UError> {
        let manager = native_password_manager(self.shared())?;
        RUNTIME.block_on(async {
            match kind {
                UVaultItemKind::Password => {
                    // Capture (site, account) before the delete so the matching
                    // password-manager metadata record can be removed too.
                    let target = api::get_passwords(&manager)
                        .await
                        .get(&id)
                        .map(|(_, entry)| (entry.srvr.clone(), entry.acct.clone()));
                    api::delete_password(&manager, id, group_id.clone()).await?;
                    if let Some((srvr, acct)) = target {
                        for (meta_id, (meta_group, meta)) in api::get_passwords_meta(&manager).await {
                            if meta.srvr == srvr && meta.acct == acct && meta_group == group_id {
                                api::delete_password_meta(&manager, meta_id, meta_group).await?;
                            }
                        }
                    }
                    Ok(())
                }
                UVaultItemKind::Passkey => api::delete_passkey(&manager, id, group_id).await,
                UVaultItemKind::Wifi => api::delete_wifi_password(&manager, id, group_id).await,
                UVaultItemKind::Code => {
                    let target = api::get_passwords_meta(&manager)
                        .await
                        .get(&id)
                        .map(|(group, meta)| (group.clone(), meta.srvr.clone(), meta.acct.clone()));
                    if let Some((group, srvr, acct)) = target {
                        let criteria = rustpush::passwords::PasswordCriteria { site: srvr, account: acct };
                        manager
                            .modify_password_entry::<rustpush::passwords::PasswordManagerMeta>(
                                &criteria,
                                |entry| {
                                    if let Ok(mut data) = entry.get_password_data() {
                                        data.totp = None;
                                        if let Ok(encoded) =
                                            rustpush::passwords::PasswordManagerMeta::get_data(&data)
                                        {
                                            entry.data = encoded;
                                        }
                                    }
                                },
                                group,
                            )
                            .await?;
                    }
                    Ok(())
                }
            }
        })
        .map_err(|error| UError::Failed { reason: format!("failed to delete item: {error}") })
    }

    pub fn list_shared_albums(&self, refresh: bool) -> Result<Vec<USharedAlbum>, UError> {
        let manager = native_shared_albums(self.shared())?;
        let ((albums, syncing), (statuses, failure)) = RUNTIME.block_on(async {
            Ok::<_, anyhow::Error>((
                api::get_albums(&manager, refresh).await?,
                api::get_syncstatus(&manager).await?,
            ))
        }).map_err(|error| UError::Failed { reason: format!("failed to list Shared Albums: {error}") })?;
        let syncing = syncing.into_iter().collect::<std::collections::HashSet<_>>();
        let failure = failure.map(|(reason, _)| reason);
        let mut result = albums.into_iter().map(|album| {
            let sync_status = statuses.get(&album.albumguid).map(|status| match status {
                api::SyncStatus::Synced => "Synced".to_string(),
                api::SyncStatus::Syncing => "Syncing".to_string(),
                api::SyncStatus::Downloading { progress, total } => format!("Downloading {progress} of {total}"),
                api::SyncStatus::Uploading { progress, total } => format!("Uploading {progress} of {total}"),
            }).or_else(|| failure.clone());
            USharedAlbum {
                id: album.albumguid.clone(),
                name: album.name.unwrap_or_else(|| "Shared Album".to_string()),
                owner_name: album.fullname,
                owner_email: album.email,
                location: album.albumlocation,
                asset_count: album.assets.len() as u64,
                invitation: album.sharingtype == "pending",
                syncing: syncing.contains(&album.albumguid),
                sync_status,
            }
        }).collect::<Vec<_>>();
        result.sort_by_key(|album| album.name.to_lowercase());
        Ok(result)
    }

    pub fn accept_shared_album(&self, album_id: String) -> Result<(), UError> {
        let manager = native_shared_albums(self.shared())?;
        RUNTIME.block_on(api::subscribe(&manager, album_id)).map(|_| ()).map_err(|error| UError::Failed {
            reason: format!("failed to accept Shared Album: {error}"),
        })
    }

    pub fn accept_shared_album_token(&self, token: String) -> Result<(), UError> {
        let manager = native_shared_albums(self.shared())?;
        RUNTIME.block_on(api::subscribe_token(&manager, token)).map(|_| ()).map_err(|error| UError::Failed {
            reason: format!("failed to accept Shared Album invitation: {error}"),
        })
    }

    pub fn set_shared_album_sync(&self, album_id: String, folder: Option<String>) -> Result<(), UError> {
        let manager = native_shared_albums(self.shared())?;
        match folder {
            Some(folder) if !folder.trim().is_empty() => {
                RUNTIME.block_on(api::add_album(&manager, album_id, folder)).map(|_| ()).map_err(|error| {
                    UError::Failed { reason: format!("failed to enable Shared Album sync: {error}") }
                })
            }
            _ => RUNTIME.block_on(api::remove_album(&manager, album_id)).map(|_| ()).map_err(|error| {
                UError::Failed { reason: format!("failed to disable Shared Album sync: {error}") }
            }),
        }
    }

    pub fn sync_shared_albums(&self) -> Result<(), UError> {
        let manager = native_shared_albums(self.shared())?;
        RUNTIME.block_on(api::sync_now(&manager)).map_err(|error| UError::Failed {
            reason: format!("failed to sync Shared Albums: {error}"),
        })
    }

    pub fn list_shared_album_assets(&self, album_id: String) -> Result<Vec<USharedAlbumAsset>, UError> {
        let manager = native_shared_albums(self.shared())?;
        RUNTIME.block_on(async {
            let ids = manager.client.get_album_summary(&album_id).await?;
            let assets = manager.client.get_assets(&album_id, &ids).await?;
            Ok::<_, anyhow::Error>(assets.into_iter().map(|asset| USharedAlbumAsset {
                id: asset.assetguid,
                filename: asset.filename,
            }).collect())
        }).map_err(|error| UError::Failed { reason: format!("failed to list Shared Album assets: {error}") })
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
    /// Stop the current receive loop and close its live Apple resources
    /// without deleting registration or account state. The caller may restore
    /// a fresh state afterward (battery-saver transitions and service reloads).
    pub fn stop_loop(&self) {
        let state = self.shared();
        api::cancel_poll(&state.cancel_poll);
        api::close_client(&state.client);
        if let Some(sharedstreams) = state
            .icloud_services
            .as_ref()
            .and_then(|services| services.sharedstreams.as_ref())
        {
            api::close_syncmanager(sharedstreams);
        }
        api::close_aps(&state.conn);
    }

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

    /// Identity of the emulated hardware this device presents to Apple:
    /// the Mac model name, serial number, and OS version. Surfaced in
    /// Settings so the user can match this device against the entry that
    /// appears in their iCloud Keychain / trusted-device list on real
    /// Apple devices.
    pub fn device_info(&self) -> Result<UDeviceInfo, UError> {
        let info = api::get_device_info(&self.shared().os_config)
            .map_err(|e| UError::Failed { reason: e.to_string() })?;
        Ok(UDeviceInfo { name: info.name, serial: info.serial, os_version: info.os_version })
    }

    /// All handles (emails + phone numbers) registered for this account.
    /// The intake layer uses these to decide `isFromMe`.
    pub fn get_handles(&self) -> Result<Vec<String>, UError> {
        RUNTIME
            .block_on(api::get_handles(&self.shared().client))
            .map_err(|e| UError::Failed { reason: e.to_string() })
    }

    /// Authenticated headers for the account's iCloud CardDAV endpoint. This
    /// reuses the on-device Apple session; Kotlin never receives the password
    /// or long-lived account credentials, only the short-lived request values
    /// the original OpenBubbles CardDAV client used.
    pub fn get_contacts_headers(&self) -> Result<std::collections::HashMap<String, String>, UError> {
        let state = self.shared();
        let services = state.icloud_services.as_ref().ok_or_else(|| {
            UError::NotReady { reason: "iCloud contacts unavailable: no Apple account".to_string() }
        })?;
        RUNTIME
            .block_on(api::get_contacts_headers(
                state.conf_dir.clone(),
                &state.anisette,
                &services.token_provider,
                &state.os_config,
            ))
            .map_err(|e| UError::Failed { reason: format!("iCloud contacts authentication failed: {e}") })
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
        if let Some(error) = api::account_reauth_error(&self.shared().conf_dir) {
            return Ok(URegisterState::Failed {
                retry_wait: None,
                error: error.to_string(),
            });
        }
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
async fn upload_attachment_task(
    conn: rustpush::APSConnection,
    file_path: String,
    mime: String,
    uti: String,
    name: Option<String>,
    progress: Option<Arc<dyn UProgressCallback>>,
) -> Result<Attachment, UError> {
    let path = Path::new(&file_path);
    let mut file = std::fs::File::open(path)
        .map_err(|e| UError::InvalidArgument { reason: format!("cannot open {}: {e}", path.display()) })?;
    let prepared = MMCSFile::prepare_put(&mut file)
        .await
        .map_err(|e| UError::Failed { reason: format!("failed to prepare attachment: {e}") })?;
    file.rewind()
        .map_err(|e| UError::Failed { reason: format!("failed to rewind {}: {e}", path.display()) })?;
    let name = name.unwrap_or_else(|| {
        path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_else(|| "attachment".to_string())
    });
    Attachment::new_mmcs(&conn, &prepared, file, &mime, &uti, &name, progress_cb(progress))
        .await
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
        let ext = ip
            .ext_json
            .map(|json| {
                serde_json::from_str::<PartExtension>(&json).map_err(|e| {
                    UError::InvalidArgument {
                        reason: format!("bad part extension json: {e}"),
                    }
                })
            })
            .transpose()?;
        out.push(IndexedMessagePart {
            part,
            idx: ip.idx.map(|i| i as usize),
            ext,
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

#[uniffi::export(async_runtime = "tokio")]
impl NativePushState {
    /// Download an incoming attachment to `dest_path` (Kotlin chose the
    /// path; parent directories are created). Mirrors the api.rs
    /// `download_attachment` sink loop, including inline attachments (bytes
    /// written straight to the file).
    pub async fn download_attachment(
        &self,
        attachment: Arc<UAttachment>,
        dest_path: String,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<(), UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let mut file = create_dest(&dest_path)?;
            attachment.inner.get_attachment(&state.conn, &mut file, progress_cb(progress))
                .await
                .map_err(|e| UError::Failed { reason: format!("attachment download failed: {e}") })?;
            file.flush().map_err(|e| UError::Failed { reason: format!("failed to flush {dest_path}: {e}") })?;
            Ok(())
        }).await
    }

    /// Download a bare MMCS file (e.g. a group icon from
    /// `UMessage.IconChange.icon_xml`) to `dest_path`.
    pub async fn download_mmcs(
        &self,
        mmcs_xml: String,
        dest_path: String,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<(), UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let mmcs = mmcs_from_xml(&mmcs_xml)?;
            let mut file = create_dest(&dest_path)?;
            mmcs.get_attachment(&state.conn, &mut file, progress_cb(progress))
                .await
                .map_err(|e| UError::Failed { reason: format!("mmcs download failed: {e}") })?;
            file.flush().map_err(|e| UError::Failed { reason: format!("failed to flush {dest_path}: {e}") })?;
            Ok(())
        }).await
    }

    /// Upload a local file to MMCS without sending a message (api.rs
    /// `upload_attachment`). Persist the result XML before sending if the
    /// send may be retried after a restart.
    pub async fn upload_attachment(
        &self,
        file_path: String,
        mime: String,
        uti: String,
        name: Option<String>,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<Arc<UAttachment>, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            Ok(Arc::new(UAttachment {
                inner: upload_attachment_task(state.conn.clone(), file_path, mime, uti, name, progress).await?,
            }))
        }).await
    }

    /// Upload a local file and send it as an attachment message in one call
    /// (the Dart `sendAttachment` flow). `text` is an optional caption part
    /// sent before the attachment. Returns the staged MessageInst; `id` is
    /// the staging GUID to persist.
    pub async fn send_attachment(
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
        let state = self.shared_arc();
        drive_ffi(async move {
            let attachment =
                upload_attachment_task(state.conn.clone(), file_path, mime, uti, name, progress).await?;
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
            let inst = api::new_msg(
                back_conversation(conversation),
                sender,
                Message::Message(normal),
            ).await;
            send_inst_on(&state, inst).await
        }).await
    }

    /// Multi-attachment variant of [`send_attachment`]: uploads every file in
    /// `file_paths` and sends them as the parts of a single message (how
    /// iMessage ships a multi-photo send). `text` is an optional caption part
    /// sent before the attachments. `mimes`/`utis`/`names` are parallel arrays
    /// with one entry per file. The progress callback fires per file, in
    /// order; each upload's counters restart at zero. Returns the staged
    /// MessageInst; `id` is the staging GUID to persist.
    pub async fn send_attachments(
        &self,
        conversation: UConversation,
        sender: String,
        file_paths: Vec<String>,
        text: Option<String>,
        mimes: Vec<String>,
        utis: Vec<String>,
        names: Vec<Option<String>>,
        reply_guid: Option<String>,
        reply_part: Option<String>,
        effect: Option<String>,
        subject: Option<String>,
        voice: bool,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<UMessageInst, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let count = file_paths.len();
            if count == 0 {
                return Err(UError::Failed { reason: "send_attachments requires at least one file".into() });
            }
            if mimes.len() != count || utis.len() != count || names.len() != count {
                return Err(UError::Failed { reason: "send_attachments metadata arrays must match file_paths".into() });
            }
            let mut parts: Vec<IndexedMessagePart> = Vec::with_capacity(count + 1);
            if let Some(text) = text.filter(|t| !t.is_empty()) {
                parts.push(IndexedMessagePart {
                    part: MessagePart::Text(text, TextFormat::default()),
                    idx: None,
                    ext: None,
                });
            }
            for (index, file_path) in file_paths.into_iter().enumerate() {
                let attachment = upload_attachment_task(
                    state.conn.clone(),
                    file_path,
                    mimes[index].clone(),
                    utis[index].clone(),
                    names[index].clone(),
                    progress.clone(),
                ).await?;
                parts.push(IndexedMessagePart {
                    part: MessagePart::Attachment(attachment),
                    idx: None,
                    ext: None,
                });
            }
            let mut normal = NormalMessage::new(String::new(), MessageType::IMessage);
            normal.parts = MessageParts(parts);
            normal.reply_guid = reply_guid;
            normal.reply_part = reply_part;
            normal.effect = effect;
            normal.subject = subject;
            normal.voice = voice;
            let inst = api::new_msg(
                back_conversation(conversation),
                sender,
                Message::Message(normal),
            ).await;
            send_inst_on(&state, inst).await
        }).await
    }

    /// Edit a previously-sent message part (Dart `edit`). `to_uuid` is the
    /// original message GUID, `edit_part` the part index being replaced,
    /// `new_parts` the full replacement part list (text/mention parts with
    /// optional formatting; attachment parts reference an already-uploaded
    /// attachment via their `xml`). No progress callback: nothing is
    /// transferred.
    pub async fn edit_message(
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
        drive_ffi(send_msg_on(self.shared_arc(), conversation, sender, msg)).await
    }

    /// Unsend (remove for everyone) a previously-sent message part
    /// (Dart `unsend`). `to_uuid` is the original message GUID, `edit_part`
    /// the part index to retract.
    pub async fn unsend_message(
        &self,
        conversation: UConversation,
        sender: String,
        to_uuid: String,
        edit_part: u64,
    ) -> Result<UMessageInst, UError> {
        let msg = Message::Unsend(UnsendMessage { tuuid: to_uuid, edit_part });
        drive_ffi(send_msg_on(self.shared_arc(), conversation, sender, msg)).await
    }

    /// Rename a group chat (Dart `renameChat`).
    pub async fn rename_chat(
        &self,
        conversation: UConversation,
        sender: String,
        new_name: String,
    ) -> Result<UMessageInst, UError> {
        let msg = Message::RenameMessage(RenameMessage { new_name });
        drive_ffi(send_msg_on(self.shared_arc(), conversation, sender, msg)).await
    }

    /// Set the full participant list of a group (add/remove inferred by
    /// comparison, exactly like rustpush/Dart `chatParticipant`). Pass every
    /// participant including `sender`, formatted+prefixed
    /// (`tel:+1...` / `mailto:...`). Bump `group_version` by one.
    pub async fn change_participants(
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
        drive_ffi(send_msg_on(self.shared_arc(), conversation, sender, msg)).await
    }

    /// Leave a group chat: sends ChangeParticipants with `sender` removed
    /// (Dart `leaveChat`). The removal matches the sender with or without
    /// its `tel:`/`mailto:` prefix.
    pub async fn leave_chat(
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
        drive_ffi(send_msg_on(self.shared_arc(), conversation, sender, msg)).await
    }

    /// Set the group photo: uploads the local image to MMCS (Dart
    /// `setChatIcon`, api.rs `upload_mmcs`) and sends the IconChange
    /// message. The file should be a 570x570 PNG.
    pub async fn set_group_icon(
        &self,
        conversation: UConversation,
        sender: String,
        file_path: String,
        group_version: u64,
        progress: Option<Arc<dyn UProgressCallback>>,
    ) -> Result<UMessageInst, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let path = Path::new(&file_path);
            let mut file = std::fs::File::open(path)
                .map_err(|e| UError::InvalidArgument { reason: format!("cannot open {}: {e}", path.display()) })?;
            let prepared = MMCSFile::prepare_put(&mut file)
                .await
                .map_err(|e| UError::Failed { reason: format!("failed to prepare group icon: {e}") })?;
            file.rewind()
                .map_err(|e| UError::Failed { reason: format!("failed to rewind {}: {e}", path.display()) })?;
            let mmcs = MMCSFile::new(&state.conn, &prepared, file, progress_cb(progress))
                .await
                .map_err(|e| UError::Failed { reason: format!("group icon upload failed: {e}") })?;
            let msg = Message::IconChange(IconChangeMessage { file: Some(mmcs), group_version });
            let inst = api::new_msg(back_conversation(conversation), sender, msg).await;
            send_inst_on(&state, inst).await
        }).await
    }

    /// Remove the group photo (Dart `deleteChatIcon`): IconChange with no
    /// attached file.
    pub async fn remove_group_icon(
        &self,
        conversation: UConversation,
        sender: String,
        group_version: u64,
    ) -> Result<UMessageInst, UError> {
        let msg = Message::IconChange(IconChangeMessage { file: None, group_version });
        drive_ffi(send_msg_on(self.shared_arc(), conversation, sender, msg)).await
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

use rustpush::cloud_messages::{
    CloudAttachment, CloudChat, CloudMessage, CloudMessagesClient, MessageSummaryInfo,
};
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

/// One trusted-device escrow bottle that can admit this device to the
/// account's end-to-end encrypted iCloud Keychain clique. The protobuf stays
/// opaque to Kotlin and is handed back unchanged to
/// [`NativePushState::join_clique_with_bottle`].
#[derive(uniffi::Record)]
pub struct UViableBottle {
    pub escrow_data: Vec<u8>,
    pub numeric_length: u64,
    pub device_name: String,
    pub model_class: String,
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
    /// A group-photo asset rides on the record. Download it with
    /// [`NativePushState::download_group_photo`].
    pub has_group_photo: bool,
}

/// Mirror of the `MessageEncryptedv3` CloudKit record with the gzipped
/// `msgProto` already decoded: flattened text (plain field or attributed
/// body), attachment guids (converted to the local `<msgGuid>_<part>` form),
/// thread/association/receipt fields.
#[derive(uniffi::Record)]
pub struct UTranscriptBackground {
    pub version: u64,
    pub chat_id: Option<String>,
    pub remove: bool,
    pub mmcs_xml: Option<String>,
}

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
    /// Apple LinkPresentation payload serialized to JSON for URL balloons.
    pub link_json: Option<String>,
    /// An app balloon payload is attached (raw payload decode is a later
    /// batch; the flag preserves `hasApplePayloadData`).
    pub has_payload_data: bool,
    /// Parsed type-138 transcript-background update from `payloadData`.
    pub transcript_background: Option<UTranscriptBackground>,
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

/// Display and download metadata from one `attachmentManateeZone` record.
#[derive(uniffi::Record)]
pub struct UCloudAttachment {
    /// Local-form guid (`at_<part>_<messageGuid>` -> `<messageGuid>_<part>`).
    pub guid: String,
    /// Message guid encoded into the cloud attachment guid, when present.
    pub message_guid: Option<String>,
    pub uti: Option<String>,
    pub mime_type: Option<String>,
    pub is_outgoing: bool,
    pub transfer_name: Option<String>,
    pub total_bytes: i64,
}

/// One chat-zone change: `chat == None` is a tombstone.
#[derive(uniffi::Record)]
pub struct UChatChange {
    pub record_id: String,
    pub chat: Option<UCloudChat>,
    /// Re-uploadable record payload (binary plist of the rustpush
    /// `CloudChat`). Persist alongside the local row; feed back through
    /// `upload_chats` to push local modifications. Empty for tombstones.
    pub blob: Vec<u8>,
}

/// One message-zone change: `message == None` is a tombstone.
#[derive(uniffi::Record)]
pub struct UMessageChange {
    pub record_id: String,
    pub message: Option<UCloudMessage>,
    /// Re-uploadable record payload (batch-8 blob format of the rustpush
    /// `CloudMessage`). Persist alongside the local row; feed back through
    /// `upload_messages`. Empty for tombstones.
    pub blob: Vec<u8>,
}

/// One attachment-zone change: `attachment == None` is a tombstone.
#[derive(uniffi::Record)]
pub struct UAttachmentChange {
    pub record_id: String,
    pub attachment: Option<UCloudAttachment>,
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

/// A page from the attachment zone (`sync_attachments`).
#[derive(uniffi::Record)]
pub struct UAttachmentSyncPage {
    pub records: Vec<UAttachmentChange>,
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
    Chat { record_id: String, chat: Option<UCloudChat>, blob: Vec<u8> },
    Message { record_id: String, message: Option<UCloudMessage>, blob: Vec<u8> },
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

fn keychain_client(
    state: &SharedPushState,
) -> Result<Arc<rustpush::keychain::KeychainClient<DefaultAnisetteProvider>>, UError> {
    state
        .icloud_services
        .as_ref()
        .and_then(|services| services.keychain.clone())
        .ok_or_else(|| UError::NotReady { reason: "no iCloud Keychain on this state".to_string() })
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

/// Dart `convertAttachmentGuid`, preserving message guids that contain `_`:
/// cloud `at_<part>_<msgGuid>` -> local `<msgGuid>_<part>`.
fn attachment_guid_parts(guid: &str) -> (String, Option<String>) {
    if let Some(rest) = guid.strip_prefix("at_") {
        if let Some((part, message_guid)) = rest.split_once('_') {
            return (
                format!("{message_guid}_{part}"),
                Some(message_guid.to_string()),
            );
        }
    }
    (guid.to_string(), None)
}

fn convert_attachment_guid(guid: &str) -> String {
    attachment_guid_parts(guid).0
}

fn conv_cloud_attachment(c: &CloudAttachment) -> UCloudAttachment {
    let meta = &c.cm.0;
    let (guid, message_guid) = attachment_guid_parts(&meta.guid);
    UCloudAttachment {
        guid,
        message_guid,
        uti: meta.uti.clone(),
        mime_type: meta.mime_type.clone(),
        is_outgoing: meta.is_outgoing,
        transfer_name: meta.transfer_name.clone(),
        total_bytes: meta.total_bytes,
    }
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
    let link_json = match (
        proto.balloon_bundle_id.as_deref(),
        proto.payload_data.as_deref(),
    ) {
        (Some("com.apple.messages.URLBalloonProvider"), Some(payload_data)) =>
            LinkMeta::from_payload_data(payload_data).ok().map(|link| j(&link)),
        _ => None,
    };
    let transcript_background = decode_cloud_transcript_background(
        c.r#type,
        proto.payload_data.as_deref(),
    );

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
        link_json,
        has_payload_data: proto.payload_data.is_some(),
        transcript_background,
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

const TRANSCRIPT_BACKGROUND_MESSAGE_TYPE: i64 = 138;

fn decode_cloud_transcript_background(
    msg_type: i64,
    payload_data: Option<&[u8]>,
) -> Option<UTranscriptBackground> {
    if msg_type != TRANSCRIPT_BACKGROUND_MESSAGE_TYPE {
        return None;
    }
    let background = plist::from_bytes::<SetTranscriptBackgroundMessage>(payload_data?).ok()?;
    Some(match &background {
        SetTranscriptBackgroundMessage::Remove { bid, chat_id, .. } => UTranscriptBackground {
            version: *bid,
            chat_id: chat_id.clone(),
            remove: true,
            mmcs_xml: None,
        },
        SetTranscriptBackgroundMessage::Set { bid, chat_id, .. } => UTranscriptBackground {
            version: *bid,
            chat_id: chat_id.clone(),
            remove: false,
            mmcs_xml: background
                .to_mmcs()
                .and_then(|file| to_plist_xml(&file).ok()),
        },
    })
}

#[cfg(test)]
mod transcript_background_tests {
    use super::*;

    #[test]
    fn decodes_cloud_transcript_background_payload() {
        let message = SetTranscriptBackgroundMessage::from_mmcs(
            Some(MMCSFile {
                signature: vec![1, 2, 3],
                object: "object-id".to_string(),
                url: "https://example.invalid/background".to_string(),
                key: vec![4, 5, 6],
                size: 42,
            }),
            9,
            Some("iMessage;+;family".to_string()),
        );
        let mut payload = Vec::new();
        plist::to_writer_binary(&mut payload, &message).unwrap();

        let decoded = decode_cloud_transcript_background(138, Some(&payload)).unwrap();

        assert_eq!(decoded.version, 9);
        assert_eq!(decoded.chat_id.as_deref(), Some("iMessage;+;family"));
        assert!(!decoded.remove);
        let mmcs: MMCSFile = plist::from_reader_xml(
            std::io::Cursor::new(decoded.mmcs_xml.unwrap()),
        ).unwrap();
        assert_eq!(mmcs.key, vec![4, 5, 6]);
        assert_eq!(mmcs.size, 42);
    }
}

fn sync_err(e: impl std::fmt::Display) -> UError {
    UError::Failed { reason: format!("cloudkit sync failed: {e}") }
}

fn pairing_code(code: String, label: &str) -> Result<String, UError> {
    let code = code.trim().to_string();
    if code.len() != 6 || !code.chars().all(|character| character.is_ascii_digit()) {
        return Err(UError::InvalidArgument {
            reason: format!("{label} must contain exactly 6 digits"),
        });
    }
    Ok(code)
}

#[uniffi::export(async_runtime = "tokio")]
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
        let keychain = keychain_client(self.shared())?;
        Ok(RUNTIME.block_on(api::is_in_clique(&keychain)))
    }

    /// Trusted-device escrow bottles available for non-destructive iCloud
    /// Keychain recovery. Empty means the account has no recoverable bottle;
    /// callers must not silently reset encrypted iCloud data in that case.
    pub fn get_viable_bottles(&self) -> Result<Vec<UViableBottle>, UError> {
        let keychain = keychain_client(self.shared())?;
        let bottles = RUNTIME.block_on(api::get_bottles(&keychain)).map_err(sync_err)?;
        Ok(bottles
            .into_iter()
            .map(|bottle| UViableBottle {
                escrow_data: bottle.escrow.encode_to_vec(),
                numeric_length: bottle.numeric_length,
                device_name: bottle.device_name,
                model_class: bottle.model_class,
            })
            .collect())
    }

    /// Join the end-to-end encrypted iCloud Keychain clique with a selected
    /// trusted-device bottle. `password` is that device's passcode;
    /// `device_password` is a newly generated recovery code for this device.
    pub fn join_clique_with_bottle(
        &self,
        escrow_data: Vec<u8>,
        password: String,
        device_password: String,
    ) -> Result<(), UError> {
        let bottle = rustpush::cloudkit_proto::EscrowData::decode(escrow_data.as_slice())
            .map_err(|e| UError::InvalidArgument {
                reason: format!("invalid iCloud escrow bottle: {e}"),
            })?;
        let keychain = keychain_client(self.shared())?;
        RUNTIME
            .block_on(api::join_clique_with_bottle(
                &keychain,
                &bottle,
                password,
                device_password,
            ))
            .map_err(sync_err)
    }

    /// Start Octagon proximity pairing and return the BLE service UUID that
    /// Android must advertise while a trusted Apple device approves access.
    pub async fn start_clique_pairing(&self) -> Result<String, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            api::start_clique_pairing(&state)
                .await
                .map_err(|error| UError::Failed {
                    reason: format!("failed to start nearby iCloud Keychain approval: {error}"),
                })
        }).await
    }

    /// Submit the six-digit code displayed by the trusted Apple device and
    /// finish Octagon trust establishment. `device_password` becomes this
    /// device's locally stored recovery code for future escrow recovery.
    pub async fn complete_clique_pairing(
        &self,
        code: String,
        device_password: String,
    ) -> Result<(), UError> {
        let code = pairing_code(code, "approval code")?;
        let device_password = pairing_code(device_password, "device recovery code")?;
        let state = self.shared_arc();
        drive_ffi(async move {
            api::complete_clique_pairing(&state, code, device_password)
                .await
                .map_err(|error| UError::Failed {
                    reason: format!("nearby iCloud Keychain approval failed: {error}"),
                })
        }).await
    }

    /// Cancel any active Octagon proximity-pairing request.
    pub async fn cancel_clique_pairing(&self) -> Result<(), UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            api::cancel_clique_pairing(&state)
                .await
                .map_err(|error| UError::Failed {
                    reason: format!("failed to cancel nearby iCloud Keychain approval: {error}"),
                })
        }).await
    }

    /// Pull one page of chat changes (`sync_chats`). Pass the previous
    /// page's `next_cursor` (none for the first page); persist the returned
    /// cursor after applying the records. `more == false` ends the zone.
    pub async fn sync_chats_page(&self, cursor: Option<Vec<u8>>) -> Result<UChatSyncPage, UError> {
        let client = cloud_messages_client(self.shared())?;
        drive_ffi(async move {
            let (next, items, status) =
                api::sync_chats(&client, cursor).await.map_err(sync_err)?;
            Ok(UChatSyncPage {
                records: items
                    .into_iter()
                    .map(|(record_id, chat)| UChatChange {
                        blob: chat.as_ref().map(chat_blob).unwrap_or_default(),
                        record_id,
                        chat: chat.as_ref().map(conv_chat),
                    })
                    .collect(),
                next_cursor: next,
                more: status != 3,
                status,
            })
        }).await
    }

    /// Pull one page of message changes (`sync_messages`). Same cursor
    /// contract as `sync_chats_page`.
    pub async fn sync_messages_page(&self, cursor: Option<Vec<u8>>) -> Result<UMessageSyncPage, UError> {
        let client = cloud_messages_client(self.shared())?;
        drive_ffi(async move {
            let (next, items, status) =
                api::sync_messages(&client, cursor).await.map_err(sync_err)?;
            Ok(UMessageSyncPage {
                records: items
                    .into_iter()
                    .map(|(record_id, message)| UMessageChange {
                        blob: message.as_ref().map(message_blob).unwrap_or_default(),
                        record_id,
                        message: message.as_ref().map(conv_cloud_message),
                    })
                    .collect(),
                next_cursor: next,
                more: status != 3,
                status,
            })
        }).await
    }

    /// Query CloudKit for type-138 transcript-background records, ignoring
    /// the incremental change cursor. Incremental sync never re-emits a
    /// wallpaper it already walked past.
    pub async fn query_transcript_backgrounds(&self) -> Result<Vec<UMessageChange>, UError> {
        let client = cloud_messages_client(self.shared())?;
        drive_ffi(async move {
            let items = api::query_transcript_backgrounds(&client)
                .await
                .map_err(sync_err)?;
            Ok(items
                .into_iter()
                .map(|(record_id, message)| UMessageChange {
                    blob: message_blob(&message),
                    record_id,
                    message: Some(conv_cloud_message(&message)),
                })
                .collect())
        }).await
    }

    /// Pull one page of attachment metadata. Payload bytes stay remote until
    /// `download_cloud_attachment` is called for a visible attachment.
    pub async fn sync_attachments_page(
        &self,
        cursor: Option<Vec<u8>>,
    ) -> Result<UAttachmentSyncPage, UError> {
        let client = cloud_messages_client(self.shared())?;
        drive_ffi(async move {
            let (next, items, status) =
                api::sync_attachments(&client, cursor).await.map_err(sync_err)?;
            Ok(UAttachmentSyncPage {
                records: items
                    .into_iter()
                    .map(|(record_id, attachment)| UAttachmentChange {
                        record_id,
                        attachment: attachment.as_ref().map(conv_cloud_attachment),
                    })
                    .collect(),
                next_cursor: next,
                more: status != 3,
                status,
            })
        }).await
    }

    /// Download one Messages-in-iCloud attachment asset directly to `path`.
    pub async fn download_cloud_attachment(
        &self,
        record_id: String,
        path: String,
    ) -> Result<(), UError> {
        let client = cloud_messages_client(self.shared())?;
        drive_ffi(async move {
            api::download_cloud_attachments(&client, vec![(path, record_id)])
                .await
                .map_err(sync_err)
        }).await
    }

    /// Download one Messages-in-iCloud group-photo asset (`CloudChat.group_photo`)
    /// from the chat zone directly to `path`.
    pub async fn download_group_photo(
        &self,
        record_id: String,
        path: String,
    ) -> Result<(), UError> {
        let client = cloud_messages_client(self.shared())?;
        drive_ffi(async move {
            api::download_cloud_group_photos(&client, vec![(path, record_id)])
                .await
                .map_err(sync_err)
        }).await
    }

    /// Push local deletions to iCloud BEFORE pulling (`delete_chats`);
    /// otherwise the pull resurrects rows the user removed. Flushes the
    /// caller's pending-delete queues like Dart's `chatDeletionIds-1`.
    pub async fn delete_chats_remote(&self, record_ids: Vec<String>) -> Result<(), UError> {
        let client = cloud_messages_client(self.shared())?;
        drive_ffi(async move {
            api::delete_chats(&client, &record_ids).await.map_err(sync_err)
        }).await
    }

    /// Push local message deletions to iCloud (`delete_messages`).
    pub async fn delete_messages_remote(&self, record_ids: Vec<String>) -> Result<(), UError> {
        let client = cloud_messages_client(self.shared())?;
        drive_ffi(async move {
            api::delete_messages(&client, &record_ids).await.map_err(sync_err)
        }).await
    }

    /// Push local attachment deletions to iCloud (`delete_attachments`).
    pub async fn delete_attachments_remote(&self, record_ids: Vec<String>) -> Result<(), UError> {
        let client = cloud_messages_client(self.shared())?;
        drive_ffi(async move {
            api::delete_attachments(&client, &record_ids).await.map_err(sync_err)
        }).await
    }

    /// Coarse driver: pull both zones (chats, then messages) to completion,
    /// streaming every page's records + running counts through `on_page`.
    /// `mode` picks the start cursors — `Full` ignores the passed cursors,
    /// `Incremental` resumes from them. Runs on the engine's blocking pool
    /// (the Kotlin caller suspends for the duration); cooperative
    /// cancellation is checked between pages via `keep_going`. Returns the
    /// summary and the cursors reached — persist them either way, treating
    /// an EMPTY cursor as "zone never pulled a page" (i.e. keep the
    /// previously stored one). Per-record failures are the callback's
    /// concern — the Rust loop only aborts on transport errors.
    pub async fn sync_history(
        &self,
        chat_cursor: Option<Vec<u8>>,
        message_cursor: Option<Vec<u8>>,
        mode: USyncMode,
        on_page: Arc<dyn USyncPageCallback>,
    ) -> Result<USyncOutcome, UError> {
        let client = cloud_messages_client(self.shared())?;
        drive_ffi(async move {
        let started = std::time::Instant::now();
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
                api::sync_chats(&client, chat_cursor.clone()).await.map_err(sync_err)?;
            chat_cursor = Some(next);
            let mut records = Vec::with_capacity(items.len());
            for (record_id, chat) in items {
                match chat {
                    Some(c) => {
                        summary.chats_done += 1;
                        records.push(USyncRecord::Chat {
                            blob: chat_blob(&c),
                            record_id,
                            chat: Some(conv_chat(&c)),
                        });
                    }
                    None => {
                        summary.chat_tombstones += 1;
                        records.push(USyncRecord::Chat { record_id, chat: None, blob: Vec::new() });
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
                let (next, items, status) =
                    api::sync_messages(&client, message_cursor.clone()).await.map_err(sync_err)?;
                message_cursor = Some(next);
                let mut records = Vec::with_capacity(items.len());
                for (record_id, message) in items {
                    match message {
                        Some(m) => {
                            summary.messages_done += 1;
                            records.push(USyncRecord::Message {
                                blob: message_blob(&m),
                                record_id,
                                message: Some(conv_cloud_message(&m)),
                            });
                        }
                        None => {
                            summary.message_tombstones += 1;
                            records.push(USyncRecord::Message { record_id, message: None, blob: Vec::new() });
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
        }).await
    }
}

#[cfg(test)]
mod clique_pairing_tests {
    use super::pairing_code;

    #[test]
    fn pairing_codes_are_six_ascii_digits() {
        assert_eq!(pairing_code(" 012345 ".to_string(), "approval code").unwrap(), "012345");
        assert!(pairing_code("12345".to_string(), "approval code").is_err());
        assert!(pairing_code("１２３４５６".to_string(), "approval code").is_err());
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
/// Deletes only this device's iCloud service state (keychain, CloudKit,
/// passwords, Find My, FaceTime, shared streams) while keeping the Apple
/// session, IDS registration, and hardware identity. Recovery for state
/// corrupted before writes were atomic: stop the push service first, call
/// this, then sign in again — the login flow refetches Apple delegates and
/// recreates every file — and finally re-join iCloud Keychain.
#[uniffi::export]
pub fn repair_icloud_services(dir: String) {
    api::reset_icloud_services(&dir);
}

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

/// Provision from an encoded HwInfo blob — the QR pairing payload after
/// the `OABS` magic + sharing flag ("Share Mac" on a real Mac). Carries the
/// full config incl. version strings, so no HwExtra needed.
#[uniffi::export]
pub fn provision_from_encoded(dir: String, encoded: Vec<u8>) -> Result<(), UError> {
    let config = api::config_from_encoded(encoded)
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

// ---------------------------------------------------------------------------
// Batch 7: typed FaceTime surface (replaces the batch-1 debug-string
// variant). Drives the ported call UI: Ring -> notification + activity,
// sessions resolve caller identity (Dart getSessionName parity).
// ---------------------------------------------------------------------------

#[derive(uniffi::Record)]
pub struct UFtMember {
    pub nickname: Option<String>,
    pub handle: String,
}

/// Active FaceTime session projection — what Kotlin needs for caller
/// resolution and group calls.
#[derive(uniffi::Record)]
pub struct UFtSession {
    pub group_id: String,
    pub my_handles: Vec<String>,
    pub members: Vec<UFtMember>,
    pub start_time: Option<u64>,
}

#[derive(uniffi::Enum)]
pub enum UFtMessage {
    LetMeInRequest {
        shared_secret: Vec<u8>,
        pseud: String,
        requestor: String,
        nickname: Option<String>,
        token: Vec<u8>,
        delegation_uuid: Option<String>,
        usage: Option<String>,
    },
    LinkChanged { guid: String },
    JoinEvent { guid: String, participant: u64, handle: String, ring: bool },
    AddMembers { guid: String, members: Vec<UFtMember>, ring: bool },
    RemoveMembers { guid: String, members: Vec<UFtMember> },
    LeaveEvent { guid: String, participant: u64, handle: String },
    Ring { guid: String },
    Decline { guid: String },
    RespondedElsewhere { guid: String },
}

fn conv_ft(msg: &rustpush::facetime::FTMessage) -> UFtMessage {
    use rustpush::facetime::FTMessage as M;
    let member = |m: &rustpush::facetime::FTMember| UFtMember {
        nickname: m.nickname.clone(),
        handle: m.handle.clone(),
    };
    match msg {
        M::LetMeInRequest(r) => UFtMessage::LetMeInRequest {
            shared_secret: r.shared_secret.clone(),
            pseud: r.pseud.clone(),
            requestor: r.requestor.clone(),
            nickname: r.nickname.clone(),
            token: r.token.clone(),
            delegation_uuid: r.delegation_uuid.clone(),
            usage: r.usage.clone(),
        },
        M::LinkChanged { guid } => UFtMessage::LinkChanged { guid: guid.clone() },
        M::JoinEvent { guid, participant, handle, ring } => UFtMessage::JoinEvent {
            guid: guid.clone(),
            participant: *participant,
            handle: handle.clone(),
            ring: *ring,
        },
        M::AddMembers { guid, members, ring } => UFtMessage::AddMembers {
            guid: guid.clone(),
            members: members.iter().map(member).collect(),
            ring: *ring,
        },
        M::RemoveMembers { guid, members } => UFtMessage::RemoveMembers {
            guid: guid.clone(),
            members: members.iter().map(member).collect(),
        },
        M::LeaveEvent { guid, participant, handle } => UFtMessage::LeaveEvent {
            guid: guid.clone(),
            participant: *participant,
            handle: handle.clone(),
        },
        M::Ring { guid } => UFtMessage::Ring { guid: guid.clone() },
        M::Decline { guid } => UFtMessage::Decline { guid: guid.clone() },
        M::RespondedElsewhere { guid } => UFtMessage::RespondedElsewhere { guid: guid.clone() },
    }
}

#[uniffi::export]
impl NativePushState {
    /// Active + known FaceTime sessions (caller resolution for the UI).
    pub fn ft_sessions(&self) -> Result<Vec<UFtSession>, UError> {
        RUNTIME
            .block_on(async {
                let sessions = api::ft_sessions(&self.shared().ft_client).await?;
                Ok(sessions
                    .iter()
                    .map(|s| UFtSession {
                        group_id: s.group_id.clone(),
                        my_handles: s.my_handles.clone(),
                        members: s.members.iter().map(|m| UFtMember {
                            nickname: m.nickname.clone(),
                            handle: m.handle.clone(),
                        }).collect(),
                        start_time: s.start_time,
                    })
                    .collect::<Vec<_>>())
            })
            .map_err(|e: anyhow::Error| UError::Failed { reason: e.to_string() })
    }

    /// FaceTime link for a usage slot ("incomingcall" / "nextincomingcall").
    pub fn get_ft_link(&self, usage: String) -> Result<String, UError> {
        RUNTIME
            .block_on(api::get_ft_link(&self.shared().ft_client, usage))
            .map_err(|e| UError::Failed { reason: e.to_string() })
    }

    /// Dart rotateIncomingLink parity: preserve the current link as old,
    /// promote nextincomingcall to current, then mint a fresh next link.
    pub fn rotate_incoming_links(&self) -> Result<(), UError> {
        let client = &self.shared().ft_client;
        RUNTIME
            .block_on(async {
                api::use_link_for(client, "incomingcall".to_string(), "incomingcall-old".to_string()).await?;
                api::use_link_for(client, "nextincomingcall".to_string(), "incomingcall".to_string()).await?;
                api::get_ft_link(client, "nextincomingcall".to_string()).await?;
                Ok(())
            })
            .map_err(|e: anyhow::Error| UError::Failed { reason: e.to_string() })
    }

    /// Validate every peer, reserve the next FaceTime link, rotate it into
    /// the active slot, and create the outgoing session as one native action.
    /// Returning the reserved link prevents Kotlin from racing link rotation
    /// against session creation.
    pub fn start_facetime_call(
        &self,
        uuid: String,
        handle: String,
        participants: Vec<String>,
    ) -> Result<String, UError> {
        if participants.is_empty() {
            return Err(UError::InvalidArgument { reason: "FaceTime needs at least one participant".to_string() });
        }
        let shared = self.shared();
        RUNTIME
            .block_on(async {
                let supported = api::validate_targets_facetime(
                    &shared.client,
                    participants.clone(),
                    handle.clone(),
                ).await?;
                if supported.len() != participants.len() {
                    anyhow::bail!("One or more participants do not support FaceTime");
                }

                let link = api::get_ft_link(&shared.ft_client, "next".to_string()).await?;
                api::use_link_for(&shared.ft_client, "current".to_string(), "current-old".to_string()).await?;
                api::use_link_for(&shared.ft_client, "next".to_string(), "current".to_string()).await?;
                api::get_ft_link(&shared.ft_client, "next".to_string()).await?;
                api::create_facetime(&shared.ft_client, uuid, handle, participants).await?;
                Ok(link)
            })
            .map_err(|e: anyhow::Error| UError::Failed { reason: e.to_string() })
    }

    /// Start an outgoing call.
    pub fn create_facetime(&self, uuid: String, handle: String, participants: Vec<String>) -> Result<(), UError> {
        RUNTIME
            .block_on(api::create_facetime(&self.shared().ft_client, uuid, handle, participants))
            .map_err(|e| UError::Failed { reason: e.to_string() })
    }

    /// Cancel/hang up a call by guid.
    pub fn cancel_facetime(&self, guid: String) -> Result<(), UError> {
        RUNTIME
            .block_on(api::cancel_facetime(&self.shared().ft_client, guid))
            .map_err(|e| UError::Failed { reason: e.to_string() })
    }

    /// Approve a knock-to-join request (answer_ft_request).
    pub fn approve_let_me_in(
        &self,
        shared_secret: Vec<u8>,
        pseud: String,
        requestor: String,
        nickname: Option<String>,
        token: Vec<u8>,
        delegation_uuid: Option<String>,
        usage: Option<String>,
        approved_group: Option<String>,
    ) -> Result<(), UError> {
        let request = rustpush::facetime::LetMeInRequest {
            shared_secret,
            pseud,
            requestor,
            nickname,
            token,
            delegation_uuid,
            usage,
        };
        RUNTIME
            .block_on(api::answer_ft_request(&self.shared().ft_client, request, approved_group))
            .map_err(|e| UError::Failed { reason: e.to_string() })
    }
}

// ---------------------------------------------------------------------------
// Batch 8a: FindMy — devices, followed friends, beacon items
// ---------------------------------------------------------------------------
//
// Mirrors the FRB surface in api.rs (`make_find_my_phone` / `get_devices` /
// `refresh_devices`, `get_background_following` / `refresh_background_following`
// over the fmfd daemon, `get_beacon_items` / `accept_beacon_share` /
// `delete_beacon_share` / `update_beacon_name`). The Dart app created the
// phone client once at setup (`makeFindMyPhone`) and held it; here the client
// is cached process-wide and rebuilt when the config dir changes (fresh
// login/teardown). Friends + items go through `icloud_services.fmfd`
// (the persistent FindMy daemon client) — NotReady before iCloud setup.
// There is no FRB "play sound" function upstream, so none is exposed.

use std::collections::HashMap;

use rustpush::findmy::{BeaconNamingRecord, FindMyClient, FindMyPhoneClient};

/// Mirror of rustpush findmy `Address` (reverse-geocode of a location).
#[derive(uniffi::Record)]
pub struct UFmAddress {
    pub administrative_area: Option<String>,
    pub country: String,
    pub country_code: String,
    pub formatted_address_lines: Option<Vec<String>>,
    pub locality: Option<String>,
    pub state_code: Option<String>,
    pub street_address: Option<String>,
    pub street_name: Option<String>,
}

/// Mirror of rustpush findmy `Location` (shared by devices and friends).
#[derive(uniffi::Record)]
pub struct UFmLocation {
    pub address: Option<UFmAddress>,
    pub altitude: f64,
    pub floor_level: i64,
    pub horizontal_accuracy: f64,
    pub is_inaccurate: bool,
    pub latitude: f64,
    pub location_id: Option<String>,
    /// Ms since the Apple epoch (2001-01-01), like every fmf timestamp.
    pub location_timestamp: Option<i64>,
    pub longitude: f64,
    pub secure_location_ts: i64,
    pub timestamp: i64,
    pub vertical_accuracy: f64,
    pub position_type: Option<String>,
    pub is_old: Option<bool>,
    pub location_finished: Option<bool>,
}

/// Mirror of rustpush findmy `FoundDevice` — every field, exactly as the
/// Dart FindMy UI consumed them.
#[derive(uniffi::Record)]
pub struct UFmDevice {
    pub device_model: Option<String>,
    pub low_power_mode: Option<bool>,
    pub passcode_length: Option<i64>,
    pub id: Option<String>,
    pub battery_status: Option<String>,
    pub lost_mode_capable: Option<bool>,
    /// 0.0 - 1.0.
    pub battery_level: Option<f64>,
    pub location_enabled: Option<bool>,
    pub is_considered_accessory: Option<bool>,
    pub location: Option<UFmLocation>,
    pub model_display_name: Option<String>,
    pub device_color: Option<String>,
    pub activation_locked: Option<bool>,
    pub rm2_state: Option<i64>,
    pub loc_found_enabled: Option<bool>,
    pub nwd: Option<bool>,
    pub device_status: Option<String>,
    pub fmly_share: Option<bool>,
    pub features: HashMap<String, bool>,
    pub this_device: Option<bool>,
    pub lost_mode_enabled: Option<bool>,
    pub device_display_name: Option<String>,
    pub name: Option<String>,
    pub can_wipe_after_lock: Option<bool>,
    pub is_mac: Option<bool>,
    pub raw_device_model: Option<String>,
    pub ba_uuid: Option<String>,
    pub device_discovery_id: Option<String>,
    pub scd: Option<bool>,
    pub location_capable: Option<bool>,
    pub wipe_in_progress: Option<bool>,
    pub dark_wake: Option<bool>,
    pub device_with_you: Option<bool>,
    pub max_msg_char: Option<i64>,
    pub device_class: Option<String>,
}

/// Mirror of rustpush findmy `Follow` (a friend-sharing relationship),
/// including the last known location when present.
#[derive(uniffi::Record)]
pub struct UFmFriend {
    pub create_timestamp: i64,
    pub expires: i64,
    pub id: String,
    pub invitation_accepted_handles: Vec<String>,
    pub invitation_from_handles: Vec<String>,
    pub is_from_messages: bool,
    pub offer_id: Option<String>,
    pub only_in_event: bool,
    pub person_id_hash: String,
    pub secure_locations_capable: bool,
    pub shallow_or_live_secure_locations_capable: bool,
    pub source: String,
    pub tk_permission: bool,
    pub update_timestamp: i64,
    pub fallback_to_legacy_allowed: Option<bool>,
    pub opted_not_to_share: Option<bool>,
    pub last_location: Option<UFmLocation>,
    pub locate_in_progress: bool,
}

/// Mirror of rustpush findmy `LocationReport` (FindMy-item beacon ping).
#[derive(uniffi::Record)]
pub struct UFmReport {
    pub lat: f32,
    pub long: f32,
    pub horizontal_accuracy: u8,
    pub status: u8,
    pub confidence: u8,
    /// Ms since the Unix epoch.
    pub timestamp_ms: u64,
    pub key_index: u64,
}

/// Mirror of api.rs `DartBeacon` (own + shared FindMy items), with the
/// naming record flattened and the optional share info inlined.
#[derive(uniffi::Record)]
pub struct UFmItem {
    pub emoji: String,
    pub name: String,
    pub associated_beacon: String,
    pub role_id: i64,
    pub last_report: Option<UFmReport>,
    pub product_id: i64,
    pub battery_level: Option<i64>,
    pub vendor_id: i64,
    pub model: String,
    pub system_version: String,
    /// Stable beacon identifier (record key).
    pub id: String,
    /// Present for items shared TO this account.
    pub share_id: Option<String>,
    pub acceptance_state: Option<i64>,
    pub owner_handle: Option<String>,
}

/// Naming update payload for `update_beacon_name` (mirror of rustpush
/// `BeaconNamingRecord`).
#[derive(uniffi::Record)]
pub struct UFmNaming {
    pub emoji: String,
    pub name: String,
    pub associated_beacon: String,
    pub role_id: i64,
}

fn conv_addr(a: &rustpush::findmy::Address) -> UFmAddress {
    UFmAddress {
        administrative_area: a.administrative_area.clone(),
        country: a.country.clone(),
        country_code: a.country_code.clone(),
        formatted_address_lines: a.formatted_address_lines.clone(),
        locality: a.locality.clone(),
        state_code: a.state_code.clone(),
        street_address: a.street_address.clone(),
        street_name: a.street_name.clone(),
    }
}

fn conv_location(l: &rustpush::findmy::Location) -> UFmLocation {
    UFmLocation {
        address: l.address.as_ref().map(conv_addr),
        altitude: l.altitude,
        floor_level: l.floor_level,
        horizontal_accuracy: l.horizontal_accuracy,
        is_inaccurate: l.is_inaccurate,
        latitude: l.latitude,
        location_id: l.location_id.clone(),
        location_timestamp: l.location_timestamp,
        longitude: l.longitude,
        secure_location_ts: l.secure_location_ts,
        timestamp: l.timestamp,
        vertical_accuracy: l.vertical_accuracy,
        position_type: l.position_type.clone(),
        is_old: l.is_old,
        location_finished: l.location_finished,
    }
}

fn conv_device(d: &rustpush::findmy::FoundDevice) -> UFmDevice {
    UFmDevice {
        device_model: d.device_model.clone(),
        low_power_mode: d.low_power_mode,
        passcode_length: d.passcode_length,
        id: d.id.clone(),
        battery_status: d.battery_status.clone(),
        lost_mode_capable: d.lost_mode_capable,
        battery_level: d.battery_level,
        location_enabled: d.location_enabled,
        is_considered_accessory: d.is_considered_accessory,
        location: d.location.as_ref().map(conv_location),
        model_display_name: d.model_display_name.clone(),
        device_color: d.device_color.clone(),
        activation_locked: d.activation_locked,
        rm2_state: d.rm2_state,
        loc_found_enabled: d.loc_found_enabled,
        nwd: d.nwd,
        device_status: d.device_status.clone(),
        fmly_share: d.fmly_share,
        features: d.features.clone(),
        this_device: d.this_device,
        lost_mode_enabled: d.lost_mode_enabled,
        device_display_name: d.device_display_name.clone(),
        name: d.name.clone(),
        can_wipe_after_lock: d.can_wipe_after_lock,
        is_mac: d.is_mac,
        raw_device_model: d.raw_device_model.clone(),
        ba_uuid: d.ba_uuid.clone(),
        device_discovery_id: d.device_discovery_id.clone(),
        scd: d.scd,
        location_capable: d.location_capable,
        wipe_in_progress: d.wipe_in_progress,
        dark_wake: d.dark_wake,
        device_with_you: d.device_with_you,
        max_msg_char: d.max_msg_char,
        device_class: d.device_class.clone(),
    }
}

fn conv_follow(f: &rustpush::findmy::Follow) -> UFmFriend {
    UFmFriend {
        create_timestamp: f.create_timestamp,
        expires: f.expires,
        id: f.id.clone(),
        invitation_accepted_handles: f.invitation_accepted_handles.clone(),
        invitation_from_handles: f.invitation_from_handles.clone(),
        is_from_messages: f.is_from_messages,
        offer_id: f.offer_id.clone(),
        only_in_event: f.only_in_event,
        person_id_hash: f.person_id_hash.clone(),
        secure_locations_capable: f.secure_locations_capable,
        shallow_or_live_secure_locations_capable: f.shallow_or_live_secure_locations_capable,
        source: f.source.clone(),
        tk_permission: f.tk_permission,
        update_timestamp: f.update_timestamp,
        fallback_to_legacy_allowed: f.fallback_to_legacy_allowed,
        opted_not_to_share: f.opted_not_to_share,
        last_location: f.last_location.as_ref().map(conv_location),
        locate_in_progress: f.locate_in_progress,
    }
}

fn conv_beacon(b: &api::DartBeacon) -> UFmItem {
    UFmItem {
        emoji: b.naming.emoji.clone(),
        name: b.naming.name.clone(),
        associated_beacon: b.naming.associated_beacon.clone(),
        role_id: b.naming.role_id,
        last_report: b.last_report.as_ref().map(|r| UFmReport {
            lat: r.lat,
            long: r.long,
            horizontal_accuracy: r.horizontal_accuracy,
            status: r.status,
            confidence: r.confidence,
            timestamp_ms: r
                .timestamp
                .duration_since(std::time::SystemTime::UNIX_EPOCH)
                .map(|d| d.as_millis() as u64)
                .unwrap_or(0),
            key_index: r.key_index as u64,
        }),
        product_id: b.product_id,
        battery_level: b.battery_level,
        vendor_id: b.vendor_id,
        model: b.model.clone(),
        system_version: b.system_version.clone(),
        id: b.id.clone(),
        share_id: b.shared.as_ref().map(|s| s.share_id.clone()),
        acceptance_state: b.shared.as_ref().map(|s| s.acceptance_state),
        owner_handle: b.shared.as_ref().map(|s| s.owner_handle.clone()),
    }
}

/// Process-wide FindMy-phone client cache (the Dart wizard held its FRB
/// object for the app's lifetime). Keyed by config dir so a re-login
/// rebuilds it; `RUNTIME.block_on` drives the async constructor.
static FMI_PHONE: std::sync::LazyLock<std::sync::Mutex<Option<(String, FindMyPhoneClient<DefaultAnisetteProvider>)>>> =
    std::sync::LazyLock::new(|| std::sync::Mutex::new(None));

fn fmfd_client(state: &SharedPushState) -> Result<Arc<FindMyClient<DefaultAnisetteProvider>>, UError> {
    state
        .icloud_services
        .as_ref()
        .and_then(|s| s.fmfd.clone())
        .ok_or_else(|| UError::NotReady {
            reason: "FindMy friends/items unavailable: no iCloud account or keychain on this state".to_string(),
        })
}

/// Borrow the cached FindMy-phone client for `state`, (re)creating it via
/// `make_find_my_phone` when missing or stale. The guard is held across the
/// `block_on` bodies of the callers — single-threaded per call site, no
/// re-entrancy.
fn with_fmi_phone<T>(
    state: &SharedPushState,
    f: impl FnOnce(&mut FindMyPhoneClient<DefaultAnisetteProvider>) -> Result<T, UError>,
) -> Result<T, UError> {
    let services = state.icloud_services.as_ref().ok_or_else(|| {
        UError::NotReady { reason: "FindMy devices unavailable: finish iCloud login first".to_string() }
    })?;
    let mut locked = FMI_PHONE.lock().expect("findmy phone cache poisoned");
    let needs_rebuild = match locked.as_ref() {
        Some((dir, _)) => *dir != state.conf_dir,
        None => true,
    };
    if needs_rebuild {
        let client = RUNTIME
            .block_on(api::make_find_my_phone(
                state.conf_dir.clone(),
                &state.os_config,
                &state.conn,
                &state.anisette,
                &services.token_provider,
            ))
            .map_err(|e| UError::NotReady { reason: format!("failed to start FindMy devices: {e}") })?;
        *locked = Some((state.conf_dir.clone(), client));
    }
    f(&mut locked.as_mut().expect("just ensured").1)
}

#[uniffi::export]
impl NativePushState {
    /// Devices on this Apple ID, from cache (creates the client on first
    /// call — its constructor already fetches the device list).
    pub fn get_devices(&self) -> Result<Vec<UFmDevice>, UError> {
        with_fmi_phone(self.shared(), |client| {
            Ok(RUNTIME.block_on(api::get_devices(client)).iter().map(conv_device).collect())
        })
    }

    /// Devices on this Apple ID, after a server refresh (`refreshClient`).
    pub fn refresh_devices(&self) -> Result<Vec<UFmDevice>, UError> {
        let config = self.shared().os_config.clone();
        with_fmi_phone(self.shared(), move |client| {
            let devices = RUNTIME
                .block_on(api::refresh_devices(&config, client))
                .map_err(|e| UError::Failed { reason: format!("findmy refresh failed: {e}") })?;
            Ok(devices.iter().map(conv_device).collect())
        })
    }

    /// Friends this account follows, from the fmfd daemon cache (may be
    /// empty before the first refresh).
    pub fn get_following(&self) -> Result<Vec<UFmFriend>, UError> {
        let fmfd = fmfd_client(self.shared())?;
        Ok(RUNTIME.block_on(api::get_background_following(&fmfd)).iter().map(conv_follow).collect())
    }

    /// Friends this account follows, after a server refresh.
    pub fn refresh_following(&self) -> Result<Vec<UFmFriend>, UError> {
        let fmfd = fmfd_client(self.shared())?;
        let config = self.shared().os_config.clone();
        let following = RUNTIME
            .block_on(api::refresh_background_following(&fmfd, &config))
            .map_err(|e| UError::Failed { reason: format!("findmy friends refresh failed: {e}") })?;
        Ok(following.iter().map(conv_follow).collect())
    }

    /// Own + shared FindMy items (AirTags etc.), syncing positions first
    /// (api.rs `get_beacon_items`).
    pub fn get_beacon_items(&self) -> Result<Vec<UFmItem>, UError> {
        let fmfd = fmfd_client(self.shared())?;
        let items = RUNTIME
            .block_on(api::get_beacon_items(&fmfd))
            .map_err(|e| UError::Failed { reason: format!("findmy items failed: {e}") })?;
        Ok(items.iter().map(conv_beacon).collect())
    }

    /// Last persisted own + shared Find My items without a network refresh.
    pub fn get_cached_beacon_items(&self) -> Result<Vec<UFmItem>, UError> {
        let fmfd = fmfd_client(self.shared())?;
        let items = RUNTIME.block_on(api::get_cached_beacon_items(&fmfd));
        Ok(items.iter().map(conv_beacon).collect())
    }

    /// Accept a pending item share (the `BeaconShared` push payload's id).
    pub fn accept_beacon_share(&self, share_id: String) -> Result<(), UError> {
        let fmfd = fmfd_client(self.shared())?;
        RUNTIME
            .block_on(api::accept_beacon_share(&fmfd, share_id))
            .map_err(|e| UError::Failed { reason: format!("accept beacon share failed: {e}") })
    }

    /// Delete a shared item.
    pub fn delete_beacon_share(&self, share_id: String) -> Result<(), UError> {
        let fmfd = fmfd_client(self.shared())?;
        RUNTIME
            .block_on(api::delete_beacon_share(&fmfd, share_id))
            .map_err(|e| UError::Failed { reason: format!("delete beacon share failed: {e}") })
    }

    /// Rename / re-emoji an item (`update_beacon_name`).
    pub fn update_beacon_name(&self, naming: UFmNaming) -> Result<(), UError> {
        let fmfd = fmfd_client(self.shared())?;
        let record = BeaconNamingRecord {
            emoji: naming.emoji,
            name: naming.name,
            associated_beacon: naming.associated_beacon,
            role_id: naming.role_id,
        };
        RUNTIME
            .block_on(api::update_beacon_name(&fmfd, &record))
            .map_err(|e| UError::Failed { reason: format!("update beacon name failed: {e}") })
    }
}

// ---------------------------------------------------------------------------
// Batch 8b: contact posters (transcript + incoming-call) and profiles
// ---------------------------------------------------------------------------
//
// Mirrors the FRB poster fns in api.rs: `parse_transcript_poster` /
// `pack_transcript_poster` (zip payload <-> SimplifiedTranscriptPoster),
// `parse_poster` / `from_poster` (IMessagePosterRecord <->
// SimplifiedIncomingCallPoster) and the binary-plist save/restore round
// trips (`transcript_poster_save`, `from_poster_save`...). The parsed
// posters are opaque objects with typed accessors (the UAttachment
// pattern): full fidelity survives `to_payload` / `to_record` because the
// upstream value is kept, not re-mirrored.

use rustpush::posterkit::{
    PosterType, SimplifiedIncomingCallPoster, SimplifiedPoster,
    SimplifiedTranscriptPoster, WallpaperMetadata,
};
use rustpush::name_photo_sharing::{IMessageNameRecord, IMessageNicknameRecord, IMessagePosterRecord};

/// RGBA color as used by poster backgrounds / text.
#[derive(uniffi::Record)]
pub struct UPosterColor {
    pub alpha: f64,
    pub blue: f64,
    pub green: f64,
    pub red: f64,
}

/// Watch-background half of a transcript poster.
#[derive(uniffi::Record)]
pub struct UWatchBackground {
    pub is_high_key: bool,
    pub luminance: f64,
    /// Raw image bytes for the chat-background wallpaper.
    pub background_image: Vec<u8>,
    pub extension_identifier: String,
}

/// One file inside a photo-poster asset (the actual image layers).
#[derive(uniffi::Record)]
pub struct UPosterFile {
    pub filename: String,
    pub data: Vec<u8>,
}

/// Which poster flavor this is, with the cheap display fields inline.
#[derive(uniffi::Enum)]
pub enum UPosterKind {
    Photo { asset_count: u64 },
    Monogram {
        initials: String,
        background: UPosterColor,
        top_background: UPosterColor,
        monogram_supported_for_name: bool,
    },
    Memoji { background: UPosterColor, has_body: bool },
    TranscriptDynamic { identifier: String },
    TranscriptGradient { colors: Vec<UPosterColor> },
}

fn conv_color(c: &rustpush::posterkit::PosterColor) -> UPosterColor {
    UPosterColor { alpha: c.alpha, blue: c.blue, green: c.green, red: c.red }
}

fn poster_kind(poster: &SimplifiedPoster) -> UPosterKind {
    match &poster.r#type {
        PosterType::Photo { assets } => UPosterKind::Photo { asset_count: assets.len() as u64 },
        PosterType::Monogram { data, background } => UPosterKind::Monogram {
            initials: data.initials.clone(),
            background: conv_color(background),
            top_background: conv_color(&data.top_background_color_description),
            monogram_supported_for_name: data.monogram_supported_for_name,
        },
        PosterType::Memoji { data, background } => {
            UPosterKind::Memoji { background: conv_color(background), has_body: data.has_body }
        }
        PosterType::TranscriptDynamic { data } => {
            UPosterKind::TranscriptDynamic { identifier: data.identifier.clone() }
        }
        PosterType::TranscriptGradient { colors } => {
            UPosterKind::TranscriptGradient { colors: colors.iter().map(conv_color).collect() }
        }
    }
}

/// Text styling of an incoming-call poster (WallpaperMetadata).
#[derive(uniffi::Record)]
pub struct UWallpaperMetadata {
    pub background_color: Option<UPosterColor>,
    pub font_color: UPosterColor,
    pub font_name: String,
    pub font_size: f32,
    pub font_weight: f32,
    pub is_vertical: bool,
    pub type_key: String,
}

fn conv_wallpaper(w: &WallpaperMetadata) -> UWallpaperMetadata {
    UWallpaperMetadata {
        background_color: w.background_color_key.as_ref().map(conv_color),
        font_color: conv_color(&w.font_color_key),
        font_name: w.font_name_key.clone(),
        font_size: w.font_size_key,
        font_weight: w.font_weight_key,
        is_vertical: w.is_vertical_key,
        type_key: w.type_key.clone(),
    }
}

/// Raw iCloud poster record (`IMessagePosterRecord`) — what
/// `fetch_profile` returns and `set_profile` accepts; parse it with
/// `parse_call_poster` to render.
#[derive(uniffi::Record)]
pub struct UPosterRecord {
    pub low_res_poster: Vec<u8>,
    pub package: Vec<u8>,
    pub meta: Vec<u8>,
}

fn back_poster_record(r: UPosterRecord) -> IMessagePosterRecord {
    IMessagePosterRecord {
        low_res_poster: r.low_res_poster,
        package: r.package,
        meta: r.meta,
    }
}

/// Parsed transcript (chat-background) poster. Parse from the zip payload
/// bytes carried on `SetTranscriptBackground` messages; `to_payload`
/// packs a (possibly unchanged) poster back into sendable bytes.
#[derive(uniffi::Object)]
pub struct UTranscriptPoster {
    inner: SimplifiedTranscriptPoster,
}

/// api.rs `parse_transcript_poster` — decode a transcript-background zip
/// payload.
#[uniffi::export]
pub fn parse_poster(data: Vec<u8>) -> Result<Arc<UTranscriptPoster>, UError> {
    let inner = SimplifiedTranscriptPoster::parse_payload(&data)
        .map_err(|e| UError::InvalidArgument { reason: format!("failed to parse transcript poster: {e}") })?;
    Ok(Arc::new(UTranscriptPoster { inner }))
}

/// api.rs `from_transcript_poster_save` — restore a poster persisted via
/// `UTranscriptPoster.save` (binary plist).
#[uniffi::export]
pub fn restore_transcript_poster_save(data: Vec<u8>) -> Result<Arc<UTranscriptPoster>, UError> {
    let inner: SimplifiedTranscriptPoster = plist::from_bytes(&data)
        .map_err(|e| UError::InvalidArgument { reason: format!("invalid saved transcript poster: {e}") })?;
    Ok(Arc::new(UTranscriptPoster { inner }))
}

#[uniffi::export]
impl UTranscriptPoster {
    /// api.rs `pack_transcript_poster` — serialize back to the zip payload.
    pub fn to_payload(&self) -> Result<Vec<u8>, UError> {
        let mut inner = self.inner.clone();
        inner
            .to_payload()
            .map_err(|e| UError::Failed { reason: format!("failed to pack transcript poster: {e}") })
    }

    /// api.rs `transcript_poster_save` — binary plist for persistence.
    pub fn save(&self) -> Result<Vec<u8>, UError> {
        to_plist_bin(&self.inner)
    }

    /// The watch/chat background half (contains the wallpaper image bytes).
    pub fn watch(&self) -> UWatchBackground {
        UWatchBackground {
            is_high_key: self.inner.watch.is_high_key,
            luminance: self.inner.watch.luminance,
            background_image: self.inner.watch.background_image_data.clone(),
            extension_identifier: self.inner.watch.extension_identifier.clone(),
        }
    }

    /// Which poster flavor this is (colors / initials / identifiers).
    pub fn kind(&self) -> UPosterKind {
        poster_kind(&self.inner.poster)
    }

    /// Title luminance (0..1) — pick contrasting text color against it.
    pub fn title_luminance(&self) -> f64 {
        self.inner.poster.title_configuration.contents_luminence
    }

    /// All files of the idx'th photo asset (empty for non-photo posters).
    pub fn photo_files(&self, asset_index: u64) -> Vec<UPosterFile> {
        match &self.inner.poster.r#type {
            PosterType::Photo { assets } => assets.get(asset_index as usize).map(|a| {
                a.files
                    .iter()
                    .map(|(name, data)| UPosterFile { filename: name.clone(), data: data.clone() })
                    .collect()
            }).unwrap_or_default(),
            _ => Vec::new(),
        }
    }
}

/// Parsed incoming-call / contact poster (`SimplifiedIncomingCallPoster`).
#[derive(uniffi::Object)]
pub struct UCallPoster {
    inner: SimplifiedIncomingCallPoster,
}

/// api.rs `parse_poster` — decode a raw `IMessagePosterRecord` (from
/// `fetch_profile` or a saved record) into a renderable poster.
#[uniffi::export]
pub fn parse_call_poster(record: UPosterRecord) -> Result<Arc<UCallPoster>, UError> {
    let raw = back_poster_record(record);
    let inner = SimplifiedIncomingCallPoster::from_poster(&raw)
        .map_err(|e| UError::InvalidArgument { reason: format!("failed to parse call poster: {e}") })?;
    Ok(Arc::new(UCallPoster { inner }))
}

/// api.rs `from_poster_save` (with its tolerant fallback for the older
/// save format) — restore a poster persisted via `UCallPoster.save`.
#[uniffi::export]
pub fn restore_call_poster_save(data: Vec<u8>) -> Result<Arc<UCallPoster>, UError> {
    let inner = match plist::from_bytes::<SimplifiedIncomingCallPoster>(&data) {
        Ok(poster) => poster,
        Err(_) => {
            #[derive(serde::Deserialize)]
            struct Extras {
                text_metadata: WallpaperMetadata,
                low_res: plist::Data,
            }
            let poster: SimplifiedPoster = plist::from_bytes(&data)
                .map_err(|e| UError::InvalidArgument { reason: format!("invalid saved call poster: {e}") })?;
            let extras: Extras = plist::from_bytes(&data)
                .map_err(|e| UError::InvalidArgument { reason: format!("invalid saved call poster extras: {e}") })?;
            SimplifiedIncomingCallPoster {
                poster,
                text_metadata: extras.text_metadata,
                low_res: extras.low_res.into(),
            }
        }
    };
    Ok(Arc::new(UCallPoster { inner }))
}

#[uniffi::export]
impl UCallPoster {
    /// api.rs `from_poster` — rebuild the raw record (for `set_profile`).
    pub fn to_record(&self) -> Result<UPosterRecord, UError> {
        let mut inner = self.inner.clone();
        let record = inner
            .to_poster()
            .map_err(|e| UError::Failed { reason: format!("failed to pack call poster: {e}") })?;
        Ok(UPosterRecord {
            low_res_poster: record.low_res_poster,
            package: record.package,
            meta: record.meta,
        })
    }

    /// api.rs `parse_poster_save` — binary plist for persistence.
    pub fn save(&self) -> Result<Vec<u8>, UError> {
        to_plist_bin(&self.inner)
    }

    /// Text styling (font color/size, background color, type).
    pub fn text_metadata(&self) -> UWallpaperMetadata {
        conv_wallpaper(&self.inner.text_metadata)
    }

    /// Low-res preview image bytes.
    pub fn low_res_image(&self) -> Vec<u8> {
        self.inner.low_res.clone()
    }

    /// Which poster flavor this is.
    pub fn kind(&self) -> UPosterKind {
        poster_kind(&self.inner.poster)
    }

    /// All files of the idx'th photo asset (empty for non-photo posters).
    pub fn photo_files(&self, asset_index: u64) -> Vec<UPosterFile> {
        match &self.inner.poster.r#type {
            PosterType::Photo { assets } => assets.get(asset_index as usize).map(|a| {
                a.files
                    .iter()
                    .map(|(name, data)| UPosterFile { filename: name.clone(), data: data.clone() })
                    .collect()
            }).unwrap_or_default(),
            _ => Vec::new(),
        }
    }
}

/// Result of `fetch_profile` (api.rs `IMessageNicknameRecord`): the name a
/// contact shared, an optional avatar image, and an optional raw poster
/// record (parse with `parse_call_poster`).
#[derive(uniffi::Record)]
pub struct UNicknameRecord {
    pub name: String,
    pub first: String,
    pub last: String,
    pub image: Option<Vec<u8>>,
    pub poster: Option<UPosterRecord>,
}

fn decode_share_profile(profile_json: &str) -> Result<Option<ShareProfileMessage>, UError> {
    if let Ok(message) = serde_json::from_str::<ShareProfileMessage>(profile_json) {
        return Ok(Some(message));
    }
    serde_json::from_str::<UpdateProfileMessage>(profile_json)
        .map(|message| message.profile)
        .map_err(|e| UError::InvalidArgument {
            reason: format!("invalid profile message json: {e}"),
        })
}

#[uniffi::export(async_runtime = "tokio")]
impl NativePushState {
    /// api.rs `fetch_profile` — resolve a `ShareProfileMessage` (the JSON
    /// from `UMessage.ShareProfile` / `UpdateProfile` payloads) to the
    /// sender's shared name + avatar + poster.
    pub async fn fetch_profile(&self, profile_json: String) -> Result<UNicknameRecord, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let services = state.icloud_services.as_ref().ok_or_else(|| {
                UError::NotReady { reason: "profiles unavailable: no iCloud account".to_string() }
            })?;
            let message = decode_share_profile(&profile_json)?.ok_or_else(|| UError::InvalidArgument {
                reason: "profile update did not include a profile".to_string(),
            })?;
            let record = api::fetch_profile(&services.profiles_client, &message)
                .await
                .map_err(|e| UError::Failed { reason: format!("fetch profile failed: {e}") })?;
            Ok(UNicknameRecord {
                name: record.name.name,
                first: record.name.first,
                last: record.name.last,
                image: record.image,
                poster: record.poster.map(|p| UPosterRecord {
                    low_res_poster: p.low_res_poster,
                    package: p.package,
                    meta: p.meta,
                }),
            })
        }).await
    }

    /// api.rs `set_profile` — publish this account's shared name/image/
    /// poster. `existing_json` is the previously returned profile JSON
    /// (kept across runs, like Dart's `shareProfileMessage` setting).
    /// Returns the new `ShareProfileMessage` JSON: persist it, and send it
    /// to contacts with `send_profile`.
    pub async fn set_profile(
        &self,
        name: String,
        first: String,
        last: String,
        image: Option<Vec<u8>>,
        poster: Option<UPosterRecord>,
        existing_json: Option<String>,
    ) -> Result<String, UError> {
        let state = self.shared_arc();
        drive_ffi(async move {
            let services = state.icloud_services.as_ref().ok_or_else(|| {
                UError::NotReady { reason: "profiles unavailable: no iCloud account".to_string() }
            })?;
            let existing = match existing_json {
                Some(json) => Some(serde_json::from_str::<ShareProfileMessage>(&json)
                    .map_err(|e| UError::InvalidArgument { reason: format!("invalid existing profile json: {e}") })?),
                None => None,
            };
            let record = IMessageNicknameRecord {
                name: IMessageNameRecord { name, first, last },
                image,
                poster: poster.map(back_poster_record),
            };
            let message = api::set_profile(&services.profiles_client, record, existing)
                .await
                .map_err(|e| UError::Failed { reason: format!("set profile failed: {e}") })?;
            serde_json::to_string(&message)
                .map_err(|e| UError::Failed { reason: format!("failed to serialize profile message: {e}") })
        }).await
    }

    /// Send a `ShareProfileMessage` (the JSON from `set_profile`) into a
    /// conversation — the "share name and photo" message.
    pub async fn send_profile(
        &self,
        conversation: UConversation,
        sender: String,
        profile_json: String,
    ) -> Result<UMessageInst, UError> {
        let message: ShareProfileMessage = serde_json::from_str(&profile_json)
            .map_err(|e| UError::InvalidArgument { reason: format!("invalid profile message json: {e}") })?;
        drive_ffi(send_msg_on(self.shared_arc(), conversation, sender, Message::ShareProfile(message))).await
    }

    pub fn report_spam(
        &self,
        handle: String,
        messages: Vec<UReportMessage>,
    ) -> Result<(), UError> {
        let reports = messages
            .into_iter()
            .map(|message| {
                Ok(ReportMessage {
                    guid: message.guid,
                    sender: message.sender,
                    conversation_size: message.conversation_size,
                    parts: back_parts(message.parts)?,
                    time_of_message: message.time_of_message,
                })
            })
            .collect::<Result<Vec<_>, UError>>()?;
        RUNTIME
            .block_on(api::report_messages(&self.shared().client, handle, reports))
            .map_err(|e| UError::Failed { reason: format!("report spam failed: {e}") })
    }
}

#[derive(uniffi::Record)]
pub struct UReportMessage {
    pub guid: String,
    pub sender: String,
    pub conversation_size: u32,
    pub parts: Vec<UIndexedPart>,
    pub time_of_message: f64,
}

// ---------------------------------------------------------------------------
// Batch 8c: CloudKit upload half (save chats / messages / attachments,
// group photos) + the blob round trip that feeds it
// ---------------------------------------------------------------------------
//
// Mirrors the Dart `uploadMessages` / chat-save half of
// doCloudKitSyncPrivate (lib/services/rustpush/rustpush_service.dart):
// - `save_chats` / `save_messages` take the full rustpush records, which
//   Kotlin cannot construct. Instead every record pulled by the batch-4
//   sync now carries a re-uploadable `blob` (binary plist of `CloudChat`;
//   a plist wrapping of `CloudMessage`'s fields with the protobuf halves
//   kept as the exact gzipped wire bytes). Persist the blob next to the
//   local row (ckRecordId) and feed it back through `upload_chats` /
//   `upload_messages` to push updates. Constructing brand-new cloud
//   records from purely local rows (Dart's `Message.toCloud`) stays a
//   later batch — this is the re-sync/update half.
// - `upload_attachments` mirrors uploadCloudAttachments + saveAttachments
//   in one call: bytes go up, the resulting Asset is folded into a
//   `CloudAttachment` record with the caller-supplied meta.
// - `upload_group_photo` mirrors uploadGroupPhoto + saveChats: uploads the
//   image, grafts the Asset onto the restored chat record, saves it.

use rustpush::cloud_messages::{AttachmentMeta, MessageFlags};
use rustpush::cloudkit_proto::{Asset, CloudKitBytes};
use rustpush::cloud_messages::cloudmessagesp::{MessageProto, MessageProto2, MessageProto3, MessageProto4};
use rustpush::cloud_messages::GZipWrapper;

use futures::FutureExt;

/// Run an upload future, converting panics and anyhow errors to UError
/// (api.rs unwraps on inconsistent server responses).
fn ck_run<T>(fut: impl std::future::Future<Output = anyhow::Result<T>>) -> Result<T, UError> {
    RUNTIME
        .block_on(std::panic::AssertUnwindSafe(fut).catch_unwind())
        .map_err(ck_panic)?
        .map_err(sync_err)
}

fn to_plist_bin<T: Serialize>(value: &T) -> Result<Vec<u8>, UError> {
    let mut buf = Vec::new();
    plist::to_writer_binary(&mut buf, value)
        .map_err(|e| UError::Failed { reason: format!("failed to serialize binary plist: {e}") })?;
    Ok(buf)
}

/// Binary-plist serialization of a `CloudChat` (same format api.rs
/// `save_cloud_chat` produces — blobs are interchangeable with FRB's).
fn chat_blob(c: &CloudChat) -> Vec<u8> {
    to_plist_bin(c).unwrap_or_default()
}

/// Serializable stand-in for `CloudMessage` (which carries no serde
/// derives upstream): the protobuf fields keep their exact CloudKit wire
/// form (gzipped prost bytes) so the round trip is lossless.
#[derive(Serialize, serde::Deserialize)]
struct CkMessageBlob {
    utm_ms: Option<u64>,
    msg_type: i64,
    error: i64,
    chat_id: String,
    sender: String,
    time: i64,
    msg_proto_2: Option<Vec<u8>>,
    destination_caller_id: String,
    msg_proto: Vec<u8>,
    flags_bits: i64,
    guid: String,
    msg_proto_3: Option<Vec<u8>>,
    service: String,
    msg_proto_4: Option<Vec<u8>>,
}

impl CkMessageBlob {
    fn from_message(m: &CloudMessage) -> Self {
        Self {
            utm_ms: m.utm.map(|t| {
                t.duration_since(std::time::SystemTime::UNIX_EPOCH)
                    .map(|d| d.as_millis() as u64)
                    .unwrap_or(0)
            }),
            msg_type: m.r#type,
            error: m.error,
            chat_id: m.chat_id.clone(),
            sender: m.sender.clone(),
            time: m.time,
            msg_proto_2: m.msg_proto_2.as_ref().map(|p| p.to_bytes()),
            destination_caller_id: m.destination_caller_id.clone(),
            msg_proto: m.msg_proto.to_bytes(),
            flags_bits: m.flags.bits(),
            guid: m.guid.clone(),
            msg_proto_3: m.msg_proto_3.as_ref().map(|p| p.to_bytes()),
            service: m.service.clone(),
            msg_proto_4: m.msg_proto_4.as_ref().map(|p| p.to_bytes()),
        }
    }

    fn into_message(self) -> CloudMessage {
        CloudMessage {
            utm: self.utm_ms.map(|ms| std::time::SystemTime::UNIX_EPOCH + std::time::Duration::from_millis(ms)),
            r#type: self.msg_type,
            error: self.error,
            chat_id: self.chat_id,
            sender: self.sender,
            time: self.time,
            msg_proto_2: self.msg_proto_2.map(GZipWrapper::<MessageProto2>::from_bytes),
            destination_caller_id: self.destination_caller_id,
            msg_proto: GZipWrapper::<MessageProto>::from_bytes(self.msg_proto),
            flags: MessageFlags::from_bits_truncate(self.flags_bits),
            guid: self.guid,
            msg_proto_3: self.msg_proto_3.map(GZipWrapper::<MessageProto3>::from_bytes),
            service: self.service,
            msg_proto_4: self.msg_proto_4.map(GZipWrapper::<MessageProto4>::from_bytes),
        }
    }
}

fn message_blob(m: &CloudMessage) -> Vec<u8> {
    to_plist_bin(&CkMessageBlob::from_message(m)).unwrap_or_default()
}

/// One record to (re-)upload: the CloudKit record id plus the blob pulled
/// from a `UChatChange` / `UMessageChange` during sync.
#[derive(uniffi::Record)]
pub struct UCkBlob {
    pub record_id: String,
    pub blob: Vec<u8>,
}

/// Per-record outcome of an upload call.
#[derive(uniffi::Record)]
pub struct UCkSaveResult {
    pub record_id: String,
    pub ok: bool,
    pub error: Option<String>,
}

/// One attachment upload: local file + target record id + the
/// `AttachmentMeta` JSON (rustpush field keys: "mimet", "sdt", "tb",
/// "st", "is", "aguid", "ha", "ui", "fn", "ig", "tn", "vers", "t", "cdt",
/// "pathc", "mdh", "aui" — same map Dart's `getAttachmentMeta` built).
#[derive(uniffi::Record)]
pub struct UCkAttachmentUpload {
    pub file_path: String,
    pub record_id: String,
    pub meta_json: String,
}

fn ck_result(record_id: String, ok: bool) -> UCkSaveResult {
    UCkSaveResult { record_id, ok, error: if ok { None } else { Some("save rejected by CloudKit".to_string()) } }
}

/// The upload fns unwind-panic inside api.rs on inconsistent server
/// responses (signature unwraps); keep the process alive and surface it.
fn ck_panic(e: Box<dyn std::any::Any + Send>) -> UError {
    let reason = match e.downcast_ref::<&'static str>() {
        Some(s) => *s,
        None => match e.downcast_ref::<String>() {
            Some(s) => s.as_str(),
            None => "unknown",
        },
    };
    UError::Failed { reason: format!("cloudkit upload panicked: {reason}") }
}

#[uniffi::export]
impl NativePushState {
    /// api.rs `save_chats` — push chat records back to iCloud. Each entry
    /// is a `UChatChange.blob` (restored to a `CloudChat`); restore
    /// failures are reported per record without aborting the batch.
    pub fn upload_chats(&self, records: Vec<UCkBlob>) -> Result<Vec<UCkSaveResult>, UError> {
        let client = cloud_messages_client(self.shared())?;
        let mut map: HashMap<String, CloudChat> = HashMap::new();
        let mut restore_errors: Vec<UCkSaveResult> = Vec::new();
        for record in records {
            match plist::from_bytes::<CloudChat>(&record.blob) {
                Ok(chat) => {
                    map.insert(record.record_id, chat);
                }
                Err(e) => restore_errors.push(UCkSaveResult {
                    record_id: record.record_id,
                    ok: false,
                    error: Some(format!("invalid chat blob: {e}")),
                }),
            }
        }
        let saved = ck_run(api::save_chats(&client, map))?;
        let mut results: Vec<UCkSaveResult> = saved
            .into_iter()
            .map(|(id, ok)| ck_result(id, ok))
            .collect();
        results.extend(restore_errors);
        Ok(results)
    }

    /// api.rs `save_messages` — push message records back to iCloud, from
    /// their `UMessageChange.blob` payloads. Same per-record contract as
    /// `upload_chats`.
    pub fn upload_messages(&self, records: Vec<UCkBlob>) -> Result<Vec<UCkSaveResult>, UError> {
        let client = cloud_messages_client(self.shared())?;
        let mut map: HashMap<String, CloudMessage> = HashMap::new();
        let mut restore_errors: Vec<UCkSaveResult> = Vec::new();
        for record in records {
            match plist::from_bytes::<CkMessageBlob>(&record.blob) {
                Ok(blob) => {
                    map.insert(record.record_id, blob.into_message());
                }
                Err(e) => restore_errors.push(UCkSaveResult {
                    record_id: record.record_id,
                    ok: false,
                    error: Some(format!("invalid message blob: {e}")),
                }),
            }
        }
        let saved = ck_run(api::save_messages(&client, map))?;
        let mut results: Vec<UCkSaveResult> = saved
            .into_iter()
            .map(|(id, ok)| ck_result(id, ok))
            .collect();
        results.extend(restore_errors);
        Ok(results)
    }

    /// api.rs `upload_cloud_attachments` + `save_attachments` in one call
    /// (the attachment half of Dart `uploadMessages`): uploads each local
    /// file, folds the resulting asset into a `CloudAttachment` record
    /// with the given meta, and saves the records. Restore/parse failures
    /// are per-record; a transport failure fails the call.
    pub fn upload_attachments(&self, uploads: Vec<UCkAttachmentUpload>) -> Result<Vec<UCkSaveResult>, UError> {
        let client = cloud_messages_client(self.shared())?;
        let mut files: Vec<(String, String)> = Vec::with_capacity(uploads.len());
        let mut metas: HashMap<String, AttachmentMeta> = HashMap::new();
        let mut results: Vec<UCkSaveResult> = Vec::with_capacity(uploads.len());
        for upload in uploads {
            match serde_json::from_str::<AttachmentMeta>(&upload.meta_json) {
                Ok(meta) => {
                    metas.insert(upload.record_id.clone(), meta);
                    files.push((upload.file_path, upload.record_id));
                }
                Err(e) => results.push(UCkSaveResult {
                    record_id: upload.record_id,
                    ok: false,
                    error: Some(format!("invalid attachment meta json: {e}")),
                }),
            }
        }
        if files.is_empty() {
            return Ok(results);
        }
        let assets: HashMap<String, Asset> =
            ck_run(api::upload_cloud_attachments(&client, files.clone()))?;
        let mut records: HashMap<String, CloudAttachment> = HashMap::new();
        for (file, record_id) in &files {
            let Some(asset) = assets.get(record_id) else {
                results.push(UCkSaveResult {
                    record_id: record_id.clone(),
                    ok: false,
                    error: Some(format!("no asset returned for {file}")),
                });
                continue;
            };
            let meta = metas.remove(record_id).unwrap_or_default();
            records.insert(record_id.clone(), CloudAttachment { cm: GZipWrapper(meta), lqa: asset.clone() });
        }
        let saved = ck_run(api::save_attachments(&client, records))?;
        for (id, ok) in saved {
            results.push(ck_result(id, ok));
        }
        Ok(results)
    }

    /// api.rs `upload_group_photo` + `save_chats` (Dart `uploadChats`'s
    /// photo step): uploads the image file, grafts the asset onto the
    /// chat record restored from `chat_blob` (a `UChatChange.blob`), and
    /// saves the chat back to iCloud.
    pub fn upload_group_photo(
        &self,
        file_path: String,
        chat_record_id: String,
        chat_blob: Vec<u8>,
    ) -> Result<UCkSaveResult, UError> {
        let client = cloud_messages_client(self.shared())?;
        let mut chat: CloudChat = plist::from_bytes(&chat_blob)
            .map_err(|e| UError::InvalidArgument { reason: format!("invalid chat blob: {e}") })?;
        let assets: HashMap<String, Asset> = ck_run(api::upload_group_photo(
            &client,
            vec![(file_path.clone(), chat_record_id.clone())],
        ))?;
        let Some(asset) = assets.get(&chat_record_id) else {
            return Ok(UCkSaveResult {
                record_id: chat_record_id,
                ok: false,
                error: Some(format!("no asset returned for {file_path}")),
            });
        };
        chat.group_photo = Some(asset.clone());
        let saved = ck_run(api::save_chats(
            &client,
            HashMap::from([(chat_record_id.clone(), chat)]),
        ))?;
        let ok = saved.get(&chat_record_id).copied().unwrap_or(false);
        Ok(ck_result(chat_record_id, ok))
    }
}

// ---------------------------------------------------------------------------
// Batch 8d: SMS helpers — relay-service send + routing targets
// ---------------------------------------------------------------------------
//
// Dart built outgoing SMS as NormalMessage with
// `MessageType.sms(isPhone: chat.shouldRoute(), usingNumber:
// chat.ensureHandle(), fromHandle: <forwarded sender or null>)`; batch 1's
// `send_text` hard-codes IMessage, so SMS needs its own entry point.
// `sms_targets_for` wraps api.rs `get_sms_targets` (the device list the
// forwarding / "send as SMS" UI picks from).

/// Mirror of rustpush `PrivateDeviceInfo` — an SMS-capable device on the
/// account (used by relay routing).
#[derive(uniffi::Record)]
pub struct USmsTarget {
    pub uuid: Option<String>,
    pub device_name: Option<String>,
    pub token: Vec<u8>,
    pub is_hsa_trusted: bool,
    pub identities: Vec<String>,
    pub sub_services: Vec<String>,
}

#[uniffi::export]
impl NativePushState {
    /// api.rs `get_sms_targets` — SMS relay targets for a handle.
    /// `refresh` forces an IDS re-lookup.
    pub fn sms_targets_for(&self, handle: String, refresh: bool) -> Result<Vec<USmsTarget>, UError> {
        let targets = RUNTIME
            .block_on(api::get_sms_targets(&self.shared().client, handle, refresh))
            .map_err(|e| UError::Failed { reason: format!("sms target lookup failed: {e}") })?;
        Ok(targets
            .iter()
            .map(|t| USmsTarget {
                uuid: t.uuid.clone(),
                device_name: t.device_name.clone(),
                token: t.token.clone(),
                is_hsa_trusted: t.is_hsa_trusted,
                identities: t.identites.clone(),
                sub_services: t.sub_services.clone(),
            })
            .collect())
    }

    /// Send a text over the SMS relay (`MessageType::SMS`). `using_number`
    /// is the tel:-prefixed number of mine to route through (when None,
    /// the first registered phone handle is used); `from_handle` marks a
    /// forwarded message (the original sender). Other params mirror
    /// `send_text`.
    pub fn send_sms(
        &self,
        conversation: UConversation,
        sender: String,
        text: String,
        using_number: Option<String>,
        from_handle: Option<String>,
        reply_guid: Option<String>,
        reply_part: Option<String>,
        effect: Option<String>,
        subject: Option<String>,
    ) -> Result<UMessageInst, UError> {
        let using_number = match using_number {
            Some(n) => n,
            None => {
                let handles = RUNTIME
                    .block_on(api::get_my_phone_handles(&self.shared().client))
                    .map_err(|e| UError::NotReady { reason: format!("no phone handle for SMS: {e}") })?;
                handles
                    .into_iter()
                    .next()
                    .ok_or_else(|| UError::NotReady { reason: "no registered phone handle for SMS".to_string() })?
            }
        };
        let mut normal = NormalMessage::new(text, MessageType::SMS {
            is_phone: true,
            using_number,
            from_handle,
        });
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
}
