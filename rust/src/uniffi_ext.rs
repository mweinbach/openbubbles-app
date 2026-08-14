//! UniFFI extension surface for the native (no-Dart) OpenBubbles clients.
//!
//! Batch 1 (M0.2): queue consumption (journal + pointer queues), and the
//! core send path (text/typing/read/reaction) off the live push state.
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
}

impl std::fmt::Display for UError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            UError::SendFailed { reason } => write!(f, "send failed: {reason}"),
            UError::InvalidArgument { reason } => write!(f, "invalid argument: {reason}"),
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
