use std::collections::HashMap;

pub use rustpush::name_photo_sharing::{IMessageNameRecord, IMessagePosterRecord, IMessageNicknameRecord};
pub use rustpush::{DeleteTarget, MoveToRecycleBinMessage, OperatedChat};
pub use rustpush::{SetTranscriptBackgroundMessage, ShareProfileMessage, SharedPoster, UpdateProfileSharingMessage, UpdateProfileMessage, NSArrayClass, TextFlags, TextEffect, TextFormat, ScheduleMode, SupportAction, NSArray, SupportAlert, PrivateDeviceInfo, PermanentDeleteMessage, NormalMessage, MessageType, UpdateExtensionMessage, ErrorMessage, UnsendMessage, EditMessage, PartExtension, IconChangeMessage, RichLinkImageAttachmentSubstitute, ChangeParticipantMessage, ReactMessage, Reaction, ReactMessageType, RenameMessage, LPLinkMetadata, LPSpecializationMetadata, NSURL, LPIconMetadata, LPImageMetadata, LinkMeta, ExtensionApp, NSDictionaryClass, BalloonLayout, Balloon, IndexedMessagePart, AttachmentType, macos::MacOSConfig, Message, MessageTarget, macos::HardwareConfig, APSConnection, APSConnectionResource, APSState, Attachment, AuthPhone, IDSUserIdentity, MMCSFile, MessageInst, MessagePart, MessageParts, OSConfig, RelayConfig, ResourceState};
pub use rustpush::{TypingApp, ApsData, ApsAlert, AkData, IdmsCircleMessage, IdmsRequestedSignIn, TeardownSignIn, IdmsMessage, CertifiedContext, PushError, IDSUser, IMClient, ConversationData, ReportMessage, register};
pub use rustpush::{LoginState, AppleAccount, VerifyBody, TrustedPhoneNumber};
pub use rustpush::findmy::{Follow, Address, Location, FoundDevice};
pub use rustpush::facetime::{FTSession, FTMode, FTParticipant, FTMember, LetMeInRequest, FTMessage};
pub use rustpush::facetime::facetimep::{ConversationParticipant, ConversationLink};
pub use rustpush::statuskit::{StatusKitPersonalConfig, StatusKitMessage};
pub use rustpush::posterkit::{TranscriptDynamicUserData, WatchBackground, SimplifiedTranscriptPoster, SimplifiedIncomingCallPoster, PosterRole, UIColor, PRPosterContentMaterialStyle, PosterAsset, PRPosterSystemTimeFontConfiguration, PRPosterColor, PRPosterTitleStyleConfiguration, WallpaperMetadata, PosterColor, PhotoPosterContentsFrame, PhotoPosterContentsSize, PhotoPosterLayer, PhotoPosterLayout, PhotoPosterProperties, PhotoPosterContents, MonogramData, MemojiData, PosterType, SimplifiedPoster};
pub use rustpush::findmy::BeaconAttributes;
pub use rustpush::UpdateAccountFinish;

#[frb(non_opaque, mirror(PRPosterContentMaterialStyle))]
pub enum DartPRPosterContentMaterialStyle {
    PRPosterContentDiscreteColorsStyle {
        variation: f32,
        colors: Vec<UIColor>,
        vibrant: bool,
        supports_variation: bool,
        needs_to_resolve_variation: bool,
    },
    PRPosterContentVibrantMaterialStyle,
    PRPosterContentGradientStyle {
        gradient_type: u32,
        colors: Vec<UIColor>,
        start_point: String,
        locations: Vec<f64>,
        end_point: String,
    }
}

#[frb(mirror(ReportMessage))]
pub struct DartReportMessage {
    pub guid: String,
    pub sender: String,
    pub conversation_size: u32,
    pub parts: MessageParts,
    pub time_of_message: f64,
}

#[frb(mirror(PRPosterSystemTimeFontConfiguration))]
pub struct DartPRPosterSystemTimeFontConfiguration {
    pub is_system_item: bool,
    #[frb(non_final)]
    pub time_font_identifier: String,
    #[frb(non_final)]
    pub weight: f64,
}

#[frb(mirror(UIColor))]
pub enum DartUIColor { // no uid items
    RGBAColorSpace {
        color_components: u32,
        green: f64,
        blue: f64,
        red: f64,
        green_dbl: Option<f64>,
        blue_dbl: Option<f64>,
        red_dbl: Option<f64>,
        alpha_dbl: Option<f64>,
        alpha: f64,
        rgb: Vec<u8>,
        color_space: u32, // 2
        class: String,
    },
    GrayscaleAlphaColorSpace {
        color_components: u32,
        white: f64,
        alpha: f64,
        bin: Vec<u8>,
        color_space: u32, // 4
        class: String,
    }
}

#[frb(mirror(BeaconAttributes))]
pub struct DartBeaconAttributes {
    pub name: String,
    pub role_id: i64,
    pub emoji: String,
    pub system_version: String,
    pub serial_number: String,
}

#[frb(mirror(PRPosterColor))]
pub struct DartPRPosterColor {
    pub preferred_style: u32,
    pub identifier: String,
    pub suggested: bool, // not uid
    pub color: UIColor,
}

#[frb(mirror(PRPosterTitleStyleConfiguration))]
pub struct DartPRPosterTitleStyleConfiguration {
    pub alternate_date_enabled: bool,
    pub contents_luminence: f64,
    pub group_name: String,
    pub preferred_title_alignment: u32,
    pub preferred_title_layout: u32,
    pub time_font_configuration: PRPosterSystemTimeFontConfiguration,
    pub time_numbering_system: Option<Vec<u8>>,
    #[frb(non_final)]
    pub title_color: PRPosterColor,
    pub title_content_style: Vec<u8>,
    pub user_configured: bool,
    #[frb(non_final)]
    pub title_style: Option<PRPosterContentMaterialStyle>,
}

#[frb(mirror(PosterColor))]
pub struct DartPosterColor {
    pub alpha: f64,
    pub blue: f64,
    pub green: f64,
    pub red: f64,
}

#[frb(mirror(PhotoPosterContentsFrame))]
pub struct DartPhotoPosterContentsFrame {
    pub width: f64,
    pub height: f64,
    pub x: f64,
    pub y: f64,
}

#[frb(mirror(PhotoPosterContentsSize))]
pub struct DartPhotoPosterContentsSize {
    pub width: f64,
    pub height: f64,
}

#[frb(mirror(PhotoPosterLayer))]
pub struct DartPhotoPosterLayer {
    #[frb(non_final)]
    pub frame: PhotoPosterContentsFrame,
    #[frb(non_final)]
    pub filename: String,
    pub z_position: f32,
    pub identifier: String,
}



#[frb(mirror(ApsAlert))]
pub struct DartApsAlert {
    pub title: String,
    pub body: String,
    pub sbdy: String,
    pub defbtn: String,
    pub albtn: String,
}

#[frb(mirror(ApsData))]
pub struct DartApsData {
    pub alert: ApsAlert,
}

#[frb(mirror(AkData))]
pub struct DartAkData {
    pub lat: f32,
    pub lng: f32,
}

#[frb(mirror(IdmsRequestedSignIn))]
pub struct DartIdmsRequestedSignIn {
    pub aps: ApsData,
    pub txnid: String,
    pub akdata: AkData,
    pub adsid: String,
}

#[frb(mirror(TeardownSignIn))]
pub struct DartTeardownSignIn {
    pub prevtxnid: String,
}

#[frb(mirror(IdmsCircleMessage))]
pub struct DartIdmsCircleMessage {
    pub step: u32,
    pub atxnid: String,
    pub pake: Option<String>,
    pub ec: Option<i32>,
    pub idmsdata: String,
}

#[frb(mirror(IdmsMessage))]
pub enum DartIdmsMessage {
    RequestedSignIn(IdmsRequestedSignIn),
    TeardownSignIn(TeardownSignIn),
    CircleRequest(IdmsCircleMessage, Option<IdmsRequestedSignIn>),
}

#[frb(mirror(PhotoPosterLayout))]
pub struct DartPhotoPosterLayout {
    pub clock_intersection: u32,
    pub device_resolution: PhotoPosterContentsSize,
    #[frb(non_final)]
    pub visible_frame: PhotoPosterContentsFrame,
    pub time_frame: PhotoPosterContentsFrame,
    pub clock_layer_order: String,
    #[frb(non_final)]
    pub has_top_edge_contact: bool,
    pub inactive_frame: PhotoPosterContentsFrame,
    pub image_size: PhotoPosterContentsSize,
    pub parallax_padding: PhotoPosterContentsSize,
}

#[frb(mirror(PhotoPosterProperties))]
pub struct DartPhotoPosterProperties {
    pub portrait_layout: PhotoPosterLayout,
    pub settling_effect_enabled: bool,
    pub depth_enabled: bool,
    pub clock_area_luminance: f64,
    pub parallax_disabled: bool,
}

#[frb(mirror(PhotoPosterContents))]
pub struct DartPhotoPosterContents {
    pub version: u32,
    #[frb(non_final)]
    pub layers: Vec<PhotoPosterLayer>,
    pub properties: PhotoPosterProperties,
}

#[frb(mirror(MonogramData))]
pub struct DartMonogramData {
    #[frb(non_final)]
    pub top_background_color_description: PosterColor,
    #[frb(non_final)]
    pub background_color_description: PosterColor,
    #[frb(non_final)]
    pub initials: String,
    pub monogram_supported_for_name: bool,
}

#[frb(mirror(MemojiData))]
pub struct DartMemojiData {
    #[frb(non_final)]
    pub background_color_description: PosterColor,
    pub avatar_record_data: Vec<u8>,
    pub avatar_pose_data: Vec<u8>,
    pub has_body: bool,
    #[frb(non_final)]
    pub avatar_image_data: Vec<u8>,
}

#[frb(mirror(PosterAsset))]
pub struct DartPosterAsset {
    pub contents: PhotoPosterContents,
    #[frb(non_final)]
    pub files: HashMap<String, Vec<u8>>,
    pub uuid: String,
}

#[frb(mirror(TranscriptDynamicUserData))]
pub struct DartTranscriptDynamicUserData {
    pub identifier: String,
}

#[frb(type_64bit_int, mirror(SetTranscriptBackgroundMessage))]
pub enum DartSetTranscriptBackgroundMessage {
    Remove {
        aid: u32,
        bid: u64, // sequence number
        #[frb(non_final)]
        chat_id: Option<String>,

        remove: bool,
    },
    Set {
        aid: u32,
        bid: u64, // sequence number
        #[frb(non_final)]
        chat_id: Option<String>,
        
        object_id: String,
        payload_version: u32,
        background_id: String,
        url: String,
        signature: String,
        key: String,
        file_size: usize,
    }
}

#[frb(mirror(PosterType))]
pub enum DartPosterType {
    // com.apple.PhotosUIPrivate.PhotosPosterProvider
    Photo {
        assets: Vec<PosterAsset>,
    },
    Monogram {
        data: MonogramData,
        background: PosterColor,
    },
    Memoji {
        data: MemojiData,
        background: PosterColor,
    },
    TranscriptDynamic {
        data: TranscriptDynamicUserData,
    },
    TranscriptGradient {
        colors: Vec<PosterColor>,
    },
}

#[frb(mirror(WallpaperMetadata))]
pub struct DartWallpaperMetadata {
    pub background_color_key: Option<PosterColor>,
    pub font_color_key: PosterColor,
    #[frb(non_final)]
    pub font_name_key: String,
    pub font_size_key: f32,
    #[frb(non_final)]
    pub font_weight_key: f32,
    pub is_vertical_key: bool,
    pub type_key: String,
}

#[frb(mirror(PosterRole))]
pub enum DartPosterRole {
    PRPosterRoleBackdrop,
    PRPosterRoleIncomingCall,
}

#[frb(non_opaque, mirror(SimplifiedIncomingCallPoster))]
pub struct DartSimplifiedIncomingCallPoster {
    pub poster: SimplifiedPoster,
    pub text_metadata: WallpaperMetadata,
    #[frb(non_final)]
    pub low_res: Vec<u8>,
}

#[frb(non_opaque, mirror(WatchBackground))]
pub struct DartWatchBackground {
    pub is_high_key: bool,
    pub luminance: f64,
    pub background_image_data: Vec<u8>,
    pub extension_identifier: String,
}

#[frb(non_opaque, mirror(SimplifiedTranscriptPoster))]
pub struct DartSimplifiedTranscriptPoster {
    #[frb(non_final)]
    pub watch: WatchBackground,
    pub poster: SimplifiedPoster,
}

#[frb(non_opaque, mirror(SimplifiedPoster))]
pub struct DartSimplifiedPoster {
    pub title_configuration: PRPosterTitleStyleConfiguration,
    #[frb(non_final)]
    pub r#type: PosterType,
    pub role: PosterRole,
}

#[repr(C)]
#[frb(mirror(SupportAction))]
pub struct DartSupportAction {
    pub url: String,
    pub button: String,
}

#[frb(mirror(StatusKitPersonalConfig))]
pub struct DartStatusKitPersonalConfig {
    pub allowed_modes: Vec<String>,
}

#[repr(C)]
#[frb(mirror(SupportAlert))]
pub struct DartSupportAlert {
    pub title: String,
    pub body: String,
    pub action: Option<SupportAction>,
}

#[frb(mirror(ConversationData))]
#[repr(C)]
pub struct DartConversationData {
    #[frb(non_final)]
    pub participants: Vec<String>,
    #[frb(non_final)]
    pub cv_name: Option<String>,
    #[frb(non_final)]
    pub sender_guid: Option<String>,
    #[frb(non_final)]
    pub after_guid: Option<String>,
}


#[frb(non_opaque, mirror(LoginState))]
#[repr(C)]
pub enum DartLoginState {
    LoggedIn,
    // NeedsSMS2FASent(Send2FAToDevices),
    NeedsDevice2FA,
    Needs2FAVerification,
    NeedsSMS2FA,
    NeedsSMS2FAVerification(VerifyBody),
    NeedsExtraStep(String),
    NeedsLogin,
}

#[repr(C)]
#[frb(type_64bit_int, mirror(MMCSFile))]
pub struct DartMMCSFile {
    pub signature: Vec<u8>,
    pub object: String,
    pub url: String,
    pub key: Vec<u8>,
    pub size: usize
}

#[frb(mirror(SharedAlbum))]
pub struct DartSharedAlbum {
    pub name: Option<String>,
    pub fullname: Option<String>,
    pub email: Option<String>,
    pub albumguid: String,
    pub sharingtype: String, //TODO
    pub subscriptiondate: Option<String>,
    pub albumlocation: Option<String>,
    pub assets: Vec<String>,
    pub delete: Option<String>,
}

#[frb(mirror(SyncStatus))]
pub enum DartSyncStatus {
    Synced,
    Downloading {
        progress: usize,
        total: usize,
    },
    Uploading {
        progress: usize,
        total: usize,
    },
    Syncing, // deleting remote/local
}

pub use rustpush::cloud_messages::{CloudChat, CloudMessageSummary, CloudProp, CloudParticipant, GZipWrapper, MMCSAttachmentMeta, AttachmentMeta, NumOrString, AttachmentMetaExtra, CloudProp001, CloudMessage, CloudAttachment, MessageFlags, cloudmessagesp::{ChatProto, MessageProto, MessageProto2, MessageProto3, MessageProto4}};
pub use rustpush::cloudkit_proto::Asset;
pub use plist::Date;
#[frb(mirror(CloudProp))]
pub struct DartCloudProp {
    #[frb(non_final)]
    pub gpufc: Option<u32>, // 2
    #[frb(non_final)]
    pub pv: Option<u32>,
    #[frb(non_final)]
    pub number_of_times_respondedto_thread: Option<u32>,
    #[frb(non_final)]
    pub should_force_to_sms: Option<bool>,
    #[frb(non_final)]
    pub last_seen_message_guid: Option<String>,
    #[frb(non_final)]
    pub message_handshake_state: Option<u32>,
    #[frb(non_final)]
    pub legacy_group_identifiers: Vec<String>,
    #[frb(non_final)]
    pub group_photo_guid: Option<String>,
    #[frb(non_final)]
    pub last_modification_date: Option<plist::Date>, // not actually optional, just to get around default trait
}

#[frb(mirror(CloudMessageSummary))]
pub struct DartCloudMessageSummary {
    pub messages_summary: Vec<i64>,
    pub chat_summary: Vec<i64>,
    pub attachment_summary: Vec<i64>,
}

#[frb(mirror(CloudParticipant))]
pub struct DartCloudParticipant {
    pub uri: String,
}

#[frb(mirror(CloudProp001))]
pub struct DartCloudProp001 {
    pub syndication_type: u32, // guess
}

#[frb(mirror(ChatProto))]
pub struct DartChatProto {
    pub unk1: Option<u32>,
}

pub use rustpush::{NSAttributedString, NSDictionaryTypedCoder, StCollapsedValue, NSNumber, NSString};
#[frb(mirror(NSDictionaryTypedCoder))]
pub struct DartNSDictionaryTypedCoder(pub HashMap<String, StCollapsedValue>);

#[frb(mirror(NSAttributedString))]
pub struct DartNSAttributedString {
    pub text: String,
    pub ranges: Vec<(u32, NSDictionaryTypedCoder)>,
}

#[frb(external)]
impl NSAttributedString {
    #[frb(sync)]
    pub fn decode(val: &StCollapsedValue) -> Self {}
    #[frb(sync)]
    pub fn encode(&self) -> StCollapsedValue {}
}

#[frb(mirror(PasswordManagerMeta), type_64bit_int)]
pub struct DartPasswordManagerMeta {
    pub cdat: u64,
    pub mdat: u64,
    pub srvr: String,
    pub acct: String,
    pub agrp: String,
    pub data: Vec<u8>,
}

pub use rustpush::passwords::{PasswordManagerMeta, ShareInviteContentData, PasswordRawEntry, WifiPassword, PasswordManagerMetaDataFormerlyShared, PasswordManagerMetaData, PasswordManagerMetaChange, PasswordManagerAltDomain, PasswordManagerTotp, PasswordManagerMetaDataCtx, Passkey};

#[frb(mirror(PasswordManagerMetaDataFormerlyShared))]
pub struct DartPasswordManagerMetaDataFormerlyShared {
    pub group_name: Option<String>,
    pub password_manager_credential_identifier: Option<String>,
}

#[frb(mirror(PasswordManagerMetaData))]
pub struct DartPasswordManagerMetaData {
    pub history: Vec<PasswordManagerMetaChange>,
    pub alt_domains: Vec<PasswordManagerAltDomain>,
    pub totp: Option<PasswordManagerTotp>,
    pub ctxt: HashMap<String, PasswordManagerMetaDataCtx>,
    pub title: Option<Vec<u8>>,
    pub notes: Option<Vec<u8>>,
    pub formerly_shared: Option<PasswordManagerMetaDataFormerlyShared>,
    pub ocpid: Option<Vec<u8>>,
}

#[frb(external)]
impl PasswordManagerMeta {
    #[frb(sync)]
    pub fn get_password_data(&self) -> Result<PasswordManagerMetaData, PushError> { }
    #[frb(sync)]
    pub fn get_data(data: &PasswordManagerMetaData) -> Result<Vec<u8>, PushError> { }
}

#[frb(mirror(PasswordManagerMetaChange), type_64bit_int)]
pub struct DartPasswordManagerMetaChange {
    pub date: u64,
    pub password: Option<String>,
    pub old_password: Option<String>,
    pub group_name: Option<String>,
    pub group_id: Option<String>,
    pub share_type: Option<String>,
    pub id: String,
    pub typ: String,
}

#[frb(mirror(ShareInviteContentData), non_opaque)]
pub struct DartShareInviteContentData {
    pub invitation_token: Vec<u8>,
    pub group_id: String,
    pub sent_time: Date,
    pub group_name: String,
    pub share_url: String,
    pub invitee_handle: String,
}

#[frb(mirror(PasswordManagerAltDomain))]
pub struct DartPasswordManagerAltDomain {
    pub domain: String,
}

#[frb(mirror(PasswordManagerTotp), type_64bit_int)]
pub struct DartPasswordManagerTotp {
    pub secret: Vec<u8>,
    pub digits: u32,
    pub issuer: Option<String>,
    pub period: u32,
    pub initial_date: u64,
    pub algorithm: u32,
    pub account_name: Option<String>,
    pub original_url: Option<String>,
}

#[frb(mirror(PasswordRawEntry), type_64bit_int)]
pub struct DartPasswordRawEntry {
    pub cdat: u64,
    pub mdat: u64,
    pub srvr: String,
    pub acct: String,
    pub agrp: String,
    pub data: Vec<u8>,
}

#[frb(external)]
impl PasswordManagerTotp {
    #[frb(sync)]
    pub fn generate_otp(&self) -> Result<(u32, u64), PushError> {}
}

#[frb(mirror(PasswordManagerMetaDataCtx))]
pub struct DartPasswordManagerMetaDataCtx {
    pub last_used: f64,
}

#[frb(mirror(Passkey), type_64bit_int)]
pub struct DartPasskey {
    pub cdat: u64,
    pub mdat: u64,
    pub agrp: String,
    pub labl: String, // site
    pub data: Vec<u8>, // key
    pub atag: Vec<u8>, // tag
    pub klbl: Vec<u8>, // credential ID
}

#[frb(mirror(WifiPassword), type_64bit_int)]
pub struct DartWifiPassword {
    pub cdat: u64,
    pub mdat: u64,
    pub acct: String,
    pub svce: String,
    pub data: Vec<u8>,
}


#[frb(mirror(NSNumber))]
pub struct DartNSNumber(pub u32);

#[frb(external)]
impl NSNumber {
    #[frb(sync)]
    pub fn decode(val: &StCollapsedValue) -> Self {}
    #[frb(sync)]
    pub fn encode(&self) -> StCollapsedValue {}
}

#[frb(mirror(NSString))]
pub struct DartNSString(pub String);

#[frb(external)]
impl NSString {
    #[frb(sync)]
    pub fn decode(val: &StCollapsedValue) -> Self {}
    #[frb(sync)]
    pub fn encode(&self) -> StCollapsedValue {}
}

#[frb(mirror(CloudChat))]
pub struct DartCloudChat {
    #[frb(non_final)]
    pub style: i64,
    #[frb(non_final)]
    pub is_filtered: i64,
    #[frb(non_final)]
    pub successful_query: i64,
    #[frb(non_final)]
    pub state: i64,
    #[frb(non_final)]
    pub chat_identifier: String,
    #[frb(non_final)]
    pub group_id: String,
    #[frb(non_final)]
    pub service_name: String,
    #[frb(non_final)]
    pub original_group_id: String,
    #[frb(non_final)]
    pub properties: Option<CloudProp>,
    #[frb(non_final)]
    pub participants: Vec<CloudParticipant>,
    #[frb(non_final)]
    pub prop001: CloudProp001,
    #[frb(non_final)]
    pub last_read_message_timestamp: i64,
    #[frb(non_final)]
    pub last_addressed_handle: String,
    #[frb(non_final)]
    pub guid: String,
    #[frb(non_final)]
    pub display_name: Option<String>,
    #[frb(non_final)]
    pub proto001: Option<GZipWrapper<ChatProto>>,
    #[frb(non_final)]
    pub group_photo_guid: Option<String>,
    #[frb(non_final)]
    pub group_photo: Option<Asset>,
}

#[frb(sync)]
pub fn decode_chatproto(wrapped: &GZipWrapper<ChatProto>) -> ChatProto {
    (**wrapped).clone()
}

#[frb(sync)]
pub fn encode_chatproto(chat: &ChatProto) -> GZipWrapper<ChatProto> {
    GZipWrapper(chat.clone())
}

pub use rustpush::cloud_messages::{MessageEdit, MessageEditRange, MessageSummaryInfo};
#[frb(non_opaque, mirror(MessageEdit))]
pub struct DartMessageEdit {
    pub t: Vec<u8>, // this is a streamtyped
    pub d: f64,
    pub bcg: Option<String>, // uuid, refers to something
}

#[frb(non_opaque, mirror(MessageEditRange))]
pub struct DartMessageEditRange {
    pub lo: u32,
    pub le: u32,
}

#[frb(non_opaque, mirror(MessageSummaryInfo))]
pub struct DartMessageSummaryInfo {
    pub ams: Option<String>,
    pub ampt: Option<Vec<u8>>, // am part (full text part of ams)
    pub amc: Option<u32>,
    pub amb: Option<String>, // balloon id
    pub amd: Option<String>, // GamePigeon
    pub ec: HashMap<String, Vec<MessageEdit>>, // edit text
    pub ep: Vec<u32>, // edit part
    pub otr: HashMap<String, MessageEditRange>, // edit range maybe?
    pub ust: Option<bool>,
    pub rp: Vec<u32>,
    pub hbr: Option<bool>,
    pub oui: Option<String>,
    pub osn: Option<String>, // service (SMS)
    pub euh: Vec<String>, // list of handles
}

#[frb(non_opaque, mirror(CloudMessage))]
pub struct DartCloudMessage {
    pub utm: Option<SystemTime>, // option for default
    pub r#type: i64,
    pub error: i64,
    pub chat_id: String,
    pub sender: String,
    pub time: i64, // ns since apple epoch
    pub msg_proto_2: Option<GZipWrapper<MessageProto2>>, // always empty afaict??
    pub destination_caller_id: String,
    pub msg_proto: GZipWrapper<MessageProto>,
    pub flags: MessageFlags, // unk
    pub guid: String,
    pub msg_proto_3: Option<GZipWrapper<MessageProto3>>,
    pub service: String,
    pub msg_proto_4: Option<GZipWrapper<MessageProto4>>,
}

#[frb(sync)]
pub fn decode_messageproto(wrapped: &GZipWrapper<MessageProto>) -> MessageProto {
    (**wrapped).clone()
}

#[frb(sync)]
pub fn encode_messageproto(messageproto: &MessageProto) -> GZipWrapper<MessageProto> {
    GZipWrapper(messageproto.clone())
}

#[frb(sync)]
pub fn decode_messageproto2(wrapped: &GZipWrapper<MessageProto2>) -> MessageProto2 {
    (**wrapped).clone()
}

#[frb(sync)]
pub fn encode_messageproto2(messageproto2: &MessageProto2) -> GZipWrapper<MessageProto2> {
    GZipWrapper(messageproto2.clone())
}

#[frb(sync)]
pub fn decode_messageproto3(wrapped: &GZipWrapper<MessageProto3>) -> MessageProto3 {
    (**wrapped).clone()
}

#[frb(sync)]
pub fn encode_messageproto3(messageproto3: &MessageProto3) -> GZipWrapper<MessageProto3> {
    GZipWrapper(messageproto3.clone())
}

#[frb(sync)]
pub fn decode_messageproto4(wrapped: &GZipWrapper<MessageProto4>) -> MessageProto4 {
    (**wrapped).clone()
}

#[frb(sync)]
pub fn encode_messageproto4(messageproto4: &MessageProto4) -> GZipWrapper<MessageProto4> {
    GZipWrapper(messageproto4.clone())
}

#[frb(non_opaque, mirror(MessageProto), type_64bit_int)]
pub struct DartMessageProto {
    /// always 1, is finished or ck sync state
    pub unk1: u32,
    pub subject: Option<String>,
    pub text: Option<String>,
    pub attributed_body: Option<Vec<u8>>,
    pub balloon_bundle_id: Option<String>,
    pub payload_data: Option<Vec<u8>>,
    pub message_summary_info: Option<Vec<u8>>,
    pub effect: Option<String>,
    pub date_read: Option<u64>,
    pub unk10: Option<u32>,
    pub unk11: Option<u32>,
    pub date_delivered: Option<u64>,
    pub unk14: Option<u32>,
    pub associated_message_type: Option<u32>,
    pub associated_message_guid: Option<String>,
    pub associated_message_range_location: Option<u32>,
    pub associated_message_range_length: Option<u32>,
}

#[frb(non_opaque, mirror(AttachmentMetaExtra))]
pub struct DartAttachmentMetaExtra {
    pub preview_generation_state: Option<NumOrString>, // set to 1
}

#[frb(non_opaque, mirror(NumOrString))]
pub enum DartNumOrString {
    Num(u32),
    String(String),
    Bool(bool),
}

#[frb(non_opaque, mirror(MMCSAttachmentMeta))]
pub struct DartMMCSAttachmentMeta {
    // MMCS attachments
    pub mmcs_signature_hex: Option<String>,
    pub mmcs_owner: Option<String>,
    pub mmcs_url: Option<String>,
    pub decryption_key: Option<String>,

    // inline attachments
    pub inline_attachment: Option<String>,
    pub message_part: Option<String>,

    pub file_size: Option<NumOrString>,
    pub uti_type: Option<String>,
    pub mime_type: Option<String>,
    pub name: Option<String>,
}

#[frb(non_opaque, mirror(AttachmentMeta), type_64bit_int)]
pub struct DartAttachmentMeta {
    pub mime_type: Option<String>,
    pub start_date: i64,
    pub total_bytes: i64,
    pub transfer_state: i32,
    pub is_sticker: bool,
    pub guid: String,
    pub hide_attachment: bool,
    pub user_info: Option<MMCSAttachmentMeta>,
    pub filename: Option<String>, //path
    pub extras: Option<AttachmentMetaExtra>,
    pub is_outgoing: bool,
    pub transfer_name: Option<String>,
    pub version: i32, // set to 1
    pub uti: Option<String>, // uti type
    pub created_date: i64,
    pub pathc: Option<String>, // also transfer name
    pub md5: Option<String>, // first 8 bytes of md5 hash of file
}

#[frb(mirror(CloudAttachment), non_opaque)]
pub struct DartCloudAttachment {
    pub cm: GZipWrapper<AttachmentMeta>,
    pub lqa: Asset,
}

#[frb(sync)]
pub fn decode_attachmentmeta(wrapped: &GZipWrapper<AttachmentMeta>) -> AttachmentMeta {
    (**wrapped).clone()
}

#[frb(sync)]
pub fn encode_attachmentmeta(attachmentmeta: &AttachmentMeta) -> GZipWrapper<AttachmentMeta> {
    GZipWrapper(attachmentmeta.clone())
}

#[frb(non_opaque, mirror(MessageProto2))]
pub struct DartMessageProto2 {
    pub reply: Option<String>,
}

#[frb(non_opaque, mirror(MessageProto3))]
pub struct DartMessageProto3 {
    /// always 0
    pub unk2: Option<u32>,
    /// always 0
    pub unk3: Option<u32>,
}

#[frb(non_opaque, mirror(MessageProto4))]
pub struct DartMessageProto4 {
    pub associated_message_emoji: Option<String>,
    pub service: Option<String>,
    /// test is 0, unconfirmed
    pub schedule_type: Option<u32>,
    pub schedule_state: Option<u32>,
    pub group_id: Option<String>,
    /// test is 0, uncomfirmed
    pub sent_or_received_off_grid: Option<u32>,
}

#[repr(C)]
#[frb(mirror(AttachmentType))]
pub enum DartAttachmentType {
    Inline(Vec<u8>),
    MMCS(MMCSFile)
}

#[repr(C)]
#[frb(type_64bit_int, mirror(Attachment))]
pub struct DartAttachment {
    pub a_type: AttachmentType,
    pub part: u64,
    pub uti_type: String,
    pub mime: String,
    pub name: String,
    pub iris: bool
}

#[frb(external)]
impl Attachment {
    #[frb(type_64bit_int)]
    pub fn get_size(&self) -> usize { }
}

#[frb(mirror(TextFlags))]
pub struct DartTextFlags {
    bold: bool,
    italic: bool,
    underline: bool,
    strikethrough: bool,
}

#[frb(mirror(TextEffect))]
pub enum DartTextEffect {
    Big = 5,
    Small = 11,
    Shake = 9,
    Nod = 8,
    Explode = 12,
    Ripple = 4,
    Bloom = 6,
    Jitter = 10,
}

#[frb(mirror(TextFormat))]
pub enum DartTextFormat {
    Flags(TextFlags),
    Effect(TextEffect),
}

#[repr(C)]
#[frb(mirror(MessagePart))]
pub enum DartMessagePart {
    Text(String, TextFormat),
    Attachment(Attachment),
    Mention(String, String),
    Object(String),
}

#[repr(C)]
#[frb(type_64bit_int, mirror(PartExtension))]
pub enum DartPartExtension {
    Sticker {
        msg_width: f64,
        rotation: f64, // radians, -pi to +pi
        sai: u64,
        scale: f64,
        update: Option<bool>, // Some(false) for updates
        sli: u64,
        normalized_x: f64,
        normalized_y: f64,
        version: u64,
        hash: String,
        safi: u64,
        effect_type: i64,
        sticker_id: String,
    }
}

pub use rustpush::findmy::{BeaconNamingRecord, LocationReport};

#[frb(type_64bit_int, mirror(LocationReport))]
pub struct DartLocationReport {
    pub lat: f32,
    pub long: f32,
    pub horizontal_accuracy: u8,
    pub status: u8,
    pub confidence: u8,
    pub timestamp: SystemTime,
    pub key_index: usize,
}
#[frb(type_64bit_int, mirror(BeaconNamingRecord))]
pub struct DartBeaconNamingRecord {
    pub emoji: String,
    pub name: String,
    pub associated_beacon: String,
    pub role_id: i64,
}

#[repr(C)]
#[frb(type_64bit_int, mirror(IndexedMessagePart))]
pub struct DartIndexedMessagePart {
    pub part: MessagePart,
    pub idx: Option<usize>,
    pub ext: Option<PartExtension>,
}

#[frb(mirror(MessageParts))]
#[repr(C)]
pub struct DartMessageParts(pub Vec<IndexedMessagePart>);

#[frb(external)]
impl MessageParts {
    pub fn raw_text(&self) -> String { }
}


#[frb(mirror(MessageType))]
#[repr(C)]
#[derive(PartialEq, Clone)]
pub enum DartMessageType {
    IMessage,
    SMS {
        is_phone: bool,
        using_number: String, // prefixed with tel:
        from_handle: Option<String>,
    }
}

#[frb(mirror(UpdateAccountFinish))]
pub enum DartUpdateAccountFinish {
    MacOS,
    IOS {
        url: String,
    }
}

#[repr(C)]
#[frb(mirror(NSDictionaryClass))]
pub enum DartNSDictionaryClass {
    NSDictionary,
    NSMutableDictionary,
}

#[repr(C)]
#[frb(non_opaque, mirror(BalloonLayout))]
pub enum DartBalloonLayout {
    TemplateLayout {
        image_subtitle: String,
        image_title: String,
        caption: String,
        secondary_subcaption: String,
        tertiary_subcaption: String,
        subcaption: String,
        class: NSDictionaryClass,
    }
}

#[repr(C)]
#[frb(non_opaque, mirror(Balloon))]
pub struct DartBalloon {
    pub url: String,
    pub session: Option<String>, // UUID
    pub layout: Option<BalloonLayout>,
    pub ld_text: Option<String>,
    pub is_live: bool,

    pub icon: Option<Vec<u8>>,
}

#[repr(C)]
#[frb(type_64bit_int, mirror(ExtensionApp))]
pub struct DartExtensionApp {
    pub name: String,
    pub app_id: Option<u64>,
    pub bundle_id: String,

    pub balloon: Option<Balloon>,
}

#[frb(mirror(PermanentDeleteMessage))]
pub struct DartPermanentDeleteMessage {
    pub target: DeleteTarget,
    pub is_scheduled: bool,
}

#[frb(mirror(ScheduleMode), type_64bit_int)]
pub struct DartScheduleMode {
    pub ms: u64,
    pub schedule: bool,
}

#[frb(mirror(NormalMessage), type_64bit_int)]
#[repr(C)]
pub struct DartNormalMessage {
    #[frb(non_final)]
    pub parts: MessageParts,
    #[frb(non_final)]
    pub effect: Option<String>,
    #[frb(non_final)]
    pub reply_guid: Option<String>,
    #[frb(non_final)]
    pub reply_part: Option<String>,
    pub service: MessageType,
    #[frb(non_final)]
    pub subject: Option<String>,
    #[frb(non_final)]
    pub app: Option<ExtensionApp>,
    #[frb(non_final)]
    pub link_meta: Option<LinkMeta>,
    #[frb(non_final)]
    pub voice: bool,
    #[frb(non_final)]
    pub scheduled: Option<ScheduleMode>,
    #[frb(non_final)]
    pub embedded_profile: Option<ShareProfileMessage>,
}

#[repr(C)]
#[frb(mirror(NSURL))]
pub struct DartNSURL {
    pub base: String,
    pub relative: String,
}

#[repr(C)]
#[frb(mirror(LPImageMetadata))]
pub struct DartLPImageMetadata {
    pub size: String,
    pub url: NSURL,
    pub version: u8,
}

#[repr(C)]
#[frb(mirror(LPIconMetadata))]
pub struct DartLPIconMetadata {
    pub url: NSURL,
    pub version: u8,
}

#[repr(C)]
#[frb(mirror(RichLinkImageAttachmentSubstitute))]
pub struct DartRichLinkImageAttachmentSubstitute {
    pub mime_type: String,
    pub rich_link_image_attachment_substitute_index: u64,
}

#[repr(C)]
#[frb(mirror(NSArrayClass))]
pub enum DartNSArrayClass {
    NSArray,
    NSMutableArray,
}

// FRB doesn't support generics
// the things i do for this bridge...
#[repr(C)]
#[frb(non_opaque, mirror(NSArray<LPImageMetadata>))]
pub struct NSArrayImageArray {
    pub objects: Vec<LPImageMetadata>,
    pub class: NSArrayClass,
}

#[repr(C)]
#[frb(non_opaque, mirror(NSArray<LPIconMetadata>))]
pub struct NSArrayIconArray {
    pub objects: Vec<LPIconMetadata>,
    pub class: NSArrayClass,
}


#[repr(C)]
#[frb(mirror(LPLinkMetadata))]
pub struct DartLPLinkMetadata {
    pub image_metadata: Option<LPImageMetadata>,
    pub version: u8,
    pub icon_metadata: Option<LPIconMetadata>,
    pub original_url: Option<NSURL>,
    pub url: Option<NSURL>,
    pub title: Option<String>,
    pub summary: Option<String>,
    pub image: Option<RichLinkImageAttachmentSubstitute>,
    pub icon: Option<RichLinkImageAttachmentSubstitute>,
    pub images: Option<NSArray<LPImageMetadata>>,
    pub icons: Option<NSArray<LPIconMetadata>>,

    pub is_incomplete: Option<bool>,
    pub uses_activity_pub: Option<bool>,
    pub is_encoded_for_local_use: Option<bool>,
    pub collaboration_type: Option<u32>,
    pub specialization2: Option<LPSpecializationMetadata>,
}

#[frb(mirror(LPSpecializationMetadata))]
pub enum DartLPSpecializationMetadata {
    LPPasswordsInviteMetadata {
        group_name: String,
        url_parameters: String,
    }
}



#[frb(mirror(LinkMeta))]
#[repr(C)]
pub struct DartLinkMeta {
    pub data: LPLinkMetadata,
    pub attachments: Vec<Vec<u8>>,
}

#[repr(C)]
#[frb(mirror(RenameMessage))]
pub struct DartRenameMessage {
    pub new_name: String
}

#[repr(C)]
#[frb(type_64bit_int, mirror(ChangeParticipantMessage))]
pub struct DartChangeParticipantMessage {
    pub new_participants: Vec<String>,
    pub group_version: u64
}

#[repr(C)]
#[frb(mirror(Reaction))]
pub enum DartReaction {
    Heart,
    Like,
    Dislike,
    Laugh,
    Emphasize,
    Question,
    Emoji(String),
    Sticker {
        spec: Option<ExtensionApp>,
        body: MessageParts
    },
}

#[repr(C)]
#[frb(non_opaque, mirror(ReactMessageType))]
pub enum DartReactMessageType {
    React {
        reaction: Reaction,
        enable: bool,
    },
    Extension {
        spec: ExtensionApp,
        body: MessageParts,
        is_meta: bool,
    },
}

#[frb(mirror(IMessageNameRecord))]
pub struct DartIMessageNameRecord {
    pub name: String,
    pub first: String,
    pub last: String,
}

#[frb(mirror(IMessagePosterRecord))]
pub struct DartIMessagePosterRecord {
    pub low_res_poster: Vec<u8>,
    pub package: Vec<u8>,
    pub meta: Vec<u8>,
}

#[frb(mirror(IMessageNicknameRecord))]
pub struct DartIMessageNicknameRecord {
    pub name: IMessageNameRecord,
    pub image: Option<Vec<u8>>,
    pub poster: Option<IMessagePosterRecord>,
}

#[repr(C)]
#[frb(type_64bit_int, mirror(ReactMessage))]
pub struct DartReactMessage {
    pub to_uuid: String,
    pub to_part: Option<u64>,
    pub reaction: ReactMessageType,
    pub to_text: String,
    pub embedded_profile: Option<ShareProfileMessage>,
}

#[repr(C)]
#[frb(type_64bit_int, mirror(UnsendMessage))]
pub struct DartUnsendMessage {
    pub tuuid: String,
    pub edit_part: u64,
}

#[repr(C)]
#[frb(type_64bit_int, mirror(EditMessage))]
pub struct DartEditMessage {
    pub tuuid: String,
    pub edit_part: u64,
    pub new_parts: MessageParts
}

#[repr(C)]
#[frb(type_64bit_int, mirror(IconChangeMessage))]
pub struct DartIconChangeMessage {
    pub file: Option<MMCSFile>,
    pub group_version: u64
}

#[repr(C)]
#[frb(mirror(UpdateExtensionMessage))]
pub struct DartUpdateExtensionMessage {
    pub for_uuid: String,
    pub ext: PartExtension,
}

#[repr(C)]
#[frb(mirror(ErrorMessage))]
pub struct DartErrorMessage {
    pub for_uuid: String,
    pub status: u64,
    pub status_str: String,
}


#[frb(mirror(OperatedChat))]
#[derive(Clone)]
pub struct DartOperatedChat {
    pub participants: Vec<String>,
    pub group_id: String,
    pub guid: String,
    pub delete_incoming_messages: Option<bool>,
    pub was_reported_as_junk: Option<bool>
}

#[frb(mirror(DeleteTarget))]
#[derive(Clone)]
pub enum DartDeleteTarget {
    Chat(OperatedChat),
    Messages(Vec<String>)
}

#[frb(type_64bit_int, mirror(MoveToRecycleBinMessage))]
#[derive(Clone)]
pub struct DartMoveToRecycleBinMessage {
    target: DeleteTarget,
    recoverable_delete_date: u64,
}

#[frb(mirror(UpdateProfileMessage))]
pub struct DartUpdateProfileMessage {
    pub profile: Option<ShareProfileMessage>,
    pub share_contacts: bool,
}

#[frb(mirror(SharedPoster))]
pub struct DartSharedPoster {
    pub low_res_wallpaper_tag: Vec<u8>,
    pub wallpaper_tag: Vec<u8>,
    pub message_tag: Vec<u8>, 
}

#[frb(mirror(ShareProfileMessage))]
pub struct DartShareProfileMessage {
    pub cloud_kit_decryption_record_key: Vec<u8>,
    pub cloud_kit_record_key: String,
    pub poster: Option<SharedPoster>,
}

#[frb(type_64bit_int, mirror(UpdateProfileSharingMessage))]
pub struct DartUpdateProfileSharingMessage {
    pub shared_dismissed: Vec<String>,
    pub shared_all: Vec<String>,
    pub version: u64,
}

#[frb(mirror(StatusKitMessage))]
pub enum DartStatusKitMessage {
    StatusChanged {
        user: String,
        mode: Option<String>,
        allowed: bool,
    }
}

#[frb(mirror(TypingApp))]
pub struct DartTypingApp {
    pub bundle_id: String,
    pub icon: Vec<u8>,
}

#[repr(C)]
#[frb(non_opaque, mirror(Message))]
pub enum DartMessage {
    Message(NormalMessage),
    RenameMessage(RenameMessage),
    ChangeParticipants(ChangeParticipantMessage),
    React(ReactMessage),
    Delivered,
    Read,
    Typing(bool, Option<TypingApp>), // chat guid
    Unsend(UnsendMessage),
    Edit(EditMessage),
    IconChange(IconChangeMessage),
    EnableSmsActivation(bool),
    MessageReadOnDevice,
    SmsConfirmSent(bool),
    MarkUnread, // send for last message from other participant
    PeerCacheInvalidate,
    UpdateExtension(UpdateExtensionMessage),
    Error(ErrorMessage),
    MoveToRecycleBin(MoveToRecycleBinMessage),
    RecoverChat(OperatedChat),
    PermanentDelete(PermanentDeleteMessage),
    Unschedule,
    UpdateProfile(UpdateProfileMessage),
    UpdateProfileSharing(UpdateProfileSharingMessage),
    ShareProfile(ShareProfileMessage),
    NotifyAnyways,
    SetTranscriptBackground(SetTranscriptBackgroundMessage),
}

#[frb(mirror(MessageTarget))]
#[repr(C)]
pub enum DartMessageTarget {
    Token(Vec<u8>),
    Uuid(String),
}

#[frb(mirror(CertifiedContext))]
pub struct DartCertifiedContext {
    pub version: u32,
    pub receipt: Vec<u8>,
    pub sender: String,
    pub target: String,
    pub uuid: Vec<u8>,
    pub token: Vec<u8>,
}

#[frb(type_64bit_int, mirror(MessageInst))]
#[repr(C)]
pub struct DartIMessage {
    #[frb(non_final)]
    pub id: String,
    #[frb(non_final)]
    pub sender: Option<String>,
    #[frb(non_final)]
    pub conversation: Option<ConversationData>,
    #[frb(non_final)]
    pub message: Message,
    #[frb(non_final)]
    pub sent_timestamp: u64,
    #[frb(non_final)]
    pub target: Option<Vec<MessageTarget>>,
    #[frb(non_final)]
    pub send_delivered: bool,
    #[frb(non_final)]
    pub verification_failed: bool,
    #[frb(non_final)]
    pub certified_context: Option<CertifiedContext>,
}

#[repr(C)]
#[frb(mirror(TrustedPhoneNumber))]
pub struct DartTrustedPhoneNumber {
    pub number_with_dial_code: String,
    pub last_two_digits: String,
    pub push_mode: String,
    pub id: u32
}


#[repr(C)]
#[frb(mirror(PrivateDeviceInfo))]
pub struct DartPrivateDeviceInfo {
    pub uuid: Option<String>,
    pub device_name: Option<String>,
    pub token: Vec<u8>,
    pub is_hsa_trusted: bool,
    pub identites: Vec<String>,
    pub sub_services: Vec<String>,
}


#[frb(mirror(Follow))]
pub struct DartFollow {
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
    pub last_location: Option<Location>,
    pub locate_in_progress: bool,
}


#[frb(type_64bit_int, mirror(FTParticipant))]
pub struct DartFTParticipant {
    pub token: Option<String>,
    pub handle: String,
    pub participant_id: u64,
    pub last_join_date: Option<u64>,
    pub active: Option<ConversationParticipant>,
}

#[frb(mirror(FTMember))]
pub struct DartFTMember {
    pub nickname: Option<String>,
    pub handle: String,
}

#[frb(mirror(LetMeInRequest))]

pub struct DartLetMeInRequest {
    pub shared_secret: Vec<u8>,
    pub pseud: String,
    pub requestor: String,
    pub nickname: Option<String>,
    pub token: Vec<u8>,
    pub delegation_uuid: Option<String>,
    pub usage: Option<String>,
}

#[frb(type_64bit_int, mirror(FTMessage))]
pub enum DartFTMessage {
    LetMeInRequest(LetMeInRequest),
    LinkChanged {
        guid: String,
    },
    JoinEvent {
        guid: String,
        participant: u64,
        handle: String,
        ring: bool,
    },
    AddMembers {
        guid: String,
        members: HashSet<FTMember>,
        ring: bool,
    },
    RemoveMembers {
        guid: String,
        members: HashSet<FTMember>,
    },
    LeaveEvent {
        guid: String,
        participant: u64,
        handle: String,
    },
    Ring {
        guid: String,
    },
    Decline {
        guid: String,
    },
    RespondedElsewhere {
        guid: String,
    },
}

#[frb(mirror(FTMode))]
pub enum DartFTMode {
    Outgoing,
    Incoming,
    Missed,
    MissedOutgoing,
}

#[frb(type_64bit_int, mirror(FTSession))]
pub struct DartFTSession {
    pub group_id: String,
    pub my_handles: Vec<String>,
    pub participants: HashMap<String /* participant ID */, FTParticipant>,
    pub link: Option<ConversationLink>,
    pub members: HashSet<FTMember>,
    pub report_id: String, // this is different from group_id because we are thinking different
    pub start_time: Option<u64>,
    pub last_rekey: Option<u64>, // ms since epoch
    pub is_propped: bool,
    pub is_ringing_inaccurate: bool,
    pub mode: Option<FTMode>,
    pub recent_member_adds: HashMap<String, u64>,
}

#[frb(mirror(Location))]
pub struct DartLocation {
    pub address: Option<Address>,
    pub altitude: f64,
    pub floor_level: i64,
    pub horizontal_accuracy: f64,
    pub is_inaccurate: bool,
    pub latitude: f64,
    pub location_id: Option<String>,
    pub location_timestamp: Option<i64>,
    pub longitude: f64,
    pub secure_location_ts: i64,
    pub timestamp: i64,
    pub vertical_accuracy: f64,
    pub position_type: Option<String>,
    pub is_old: Option<bool>,
    pub location_finished: Option<bool>,
}

#[frb(mirror(FoundDevice))]
pub struct DartFoundDevice {
    pub device_model: Option<String>,
    pub low_power_mode: Option<bool>,
    pub passcode_length: Option<i64>,
    pub id: Option<String>,
    pub battery_status: Option<String>,
    pub lost_mode_capable: Option<bool>,
    pub battery_level: Option<f64>,
    pub location_enabled: Option<bool>,
    pub is_considered_accessory: Option<bool>,
    pub location: Option<Location>,
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

#[frb(mirror(Address))]
pub struct DartAddress {
    pub administrative_area: Option<String>,
    pub country: String,
    pub country_code: String,
    pub formatted_address_lines: Option<Vec<String>>,
    pub locality: Option<String>,
    pub state_code: Option<String>,
    pub street_address: Option<String>,
    pub street_name: Option<String>,
}

