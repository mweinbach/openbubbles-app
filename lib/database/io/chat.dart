import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart' hide Chat_, Handle_, Message_, Attachment_;
import 'package:bluebubbles/generated/objectbox.g.dart';
import 'package:bluebubbles/models/models.dart' show HandleLookupKey, MessageSaveResult;
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/services/backend/interfaces/chat_interface.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:collection/collection.dart';
import 'package:dio/dio.dart';
import 'package:faker/faker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart' hide Response;
import 'package:mime_type/mime_type.dart';
// (needed when generating objectbox model code)
// ignore: unnecessary_import
import 'package:objectbox/objectbox.dart';
import 'package:universal_io/io.dart';

Future<String> getZenKey(String key) async {
  return await MethodChannelSvc.invokeMethod("zen-mode-uuid", {"key": key});
}

Future<api.StatusKitPersonalConfig> configForMask(int mask) async {
  final isStarredContact = ((mask >> 0) & 1) == 1;
  final isPriority = ((mask >> 1) & 1) == 1;

  return api.StatusKitPersonalConfig(allowedModes: [
    if (isStarredContact) await getZenKey("starred"),
    if (isPriority) await getZenKey("priority"),
    if (isStarredContact || isPriority) await getZenKey("starred_priority"),
  ]);
}

@Entity()
class Chat {
  int? id;

  @Index(type: IndexType.value)
  @Unique()
  String guid;

  String? chatIdentifier;
  bool? isArchived;
  String? muteType;
  String? muteArgs;
  bool? isPinned;
  bool? hasUnreadMessage;

  String? displayName;
  String? title;
  String get properTitle {
    if (SettingsSvc.settings.redactedMode.value && SettingsSvc.settings.hideContactInfo.value) {
      return getTitle();
    }
    return title ?? getTitle();
  }

  bool? autoSendReadReceipts;
  bool? autoSendTypingIndicators;
  String? textFieldText;
  String? textFieldAnnotations;
  List<String> textFieldAttachments = [];

  /// ObjectBox ToOne relation to the latest message for O(1) lookup.
  /// Keep [dbOnlyLatestMessageDate] in sync via [setLatestMessage].
  final dbLatestMessage = ToOne<Message>();

  @Property(uid: 526293286661780207)
  DateTime? dbOnlyLatestMessageDate;

  /// Update the latest-message relation and its sort-key in one step.
  /// Persists both fields to the DB asynchronously (fire-and-forget).
  void setLatestMessage(Message m) {
    dbLatestMessage.target = m;
    // Workaround because save doesn't do this, so, then when you set this it won't get the targetId when serializing it.
    dbLatestMessage.attach(Database.store);
    dbOnlyLatestMessageDate = m.dateCreated;
    unawaited(saveAsync(updateLatestMessage: true));
  }

  DateTime? dateDeleted;
  int? style;
  bool lockChatName;
  bool lockChatIcon;
  String? lastReadMessageGuid;
  int? groupVersion;
  String? apnTitle;

  Uint8List? cloudData;
  String? ckRecordId;
  String? cloudGuid;
  bool ckSyncState = false;
  String? photoAttachmentGuid;
  String? usingHandle;
  bool isRpSms;
  List<String> guidRefs = [];
  int? telephonyId;
  bool? shareZenMode;
  bool notifsSilenced = false;
  DateTime? dateNotifiedAnyways;
  int? zenModeIsShared;
  bool? senderIsKnown;
  bool isRoutingStub = false;
  String? transcriptPosterPath;
  int transcriptBackgroundVersion = 1;
  String? customThemeLight;
  String? customThemeDark;

  Message get sendLastMessage {
    var messages = Chat.getMessages(this, limit: 10, getDetails: true);
    return messages.firstWhereOrNull((msg) =>
            msg.stagingGuid != null ||
            (msg.guid != null && !msg.guid!.contains("temp") && !msg.guid!.contains("error"))) ??
        Message(
          dateCreated: DateTime.fromMillisecondsSinceEpoch(0),
          guid: guid,
        );
  }

  final RxnString _customAvatarPath = RxnString();
  String? get customAvatarPath => _customAvatarPath.value;
  set customAvatarPath(String? s) => _customAvatarPath.value = s;

  final RxnString _customBackgroundPath = RxnString();
  String? get customBackgroundPath => _customBackgroundPath.value;
  set customBackgroundPath(String? s) => _customBackgroundPath.value = s;

  final RxnInt _pinIndex = RxnInt();
  int? get pinIndex => _pinIndex.value;
  set pinIndex(int? i) => _pinIndex.value = i;

  @Transient()
  RxDouble sendProgress = 0.0.obs;

  void handlesChanged() {
    final cachedChat = cvc(this).chat;
    cachedChat.handles = handles;
  }

  var handles = ToMany<Handle>();

  // Do not use this field directly, use the handles ToMany relation instead.
  // This should only really be used for serialization/deserialization purposes.
  @Transient()
  List<Handle> participants = [];

  @Backlink('chat')
  final messages = ToMany<Message>();

  @Transient()
  String? _fakeName;

  @Transient()
  String get fakeName {
    if (_fakeName != null) return _fakeName!;
    final color = faker.color.color();
    final animal = faker.animal.name();
    _fakeName = "${color.capitalize} ${animal.capitalize}";
    return _fakeName!;
  }

  Chat({
    this.id,
    required this.guid,
    this.chatIdentifier, // how is this different from GUID?
    this.isArchived = false,
    this.isPinned = false,
    this.muteType,
    this.muteArgs,
    this.hasUnreadMessage = false,
    this.displayName,
    String? customAvatar,
    String? customBackground,
    int? pinnedIndex,
    Message? latestMessage,
    this.participants = const [],
    this.autoSendReadReceipts,
    this.autoSendTypingIndicators,
    this.textFieldText,
    this.textFieldAnnotations,
    this.textFieldAttachments = const [],
    this.dateDeleted,
    this.style,
    this.lockChatName = false,
    this.lockChatIcon = false,
    this.lastReadMessageGuid,
    this.apnTitle,
    this.usingHandle,
    this.isRpSms = false,
    List<String>? guidRefs,
    this.telephonyId,
    this.shareZenMode,
    this.notifsSilenced = false,
    this.dateNotifiedAnyways,
    this.zenModeIsShared,
    this.senderIsKnown = true,
    this.isRoutingStub = false,
    this.transcriptPosterPath,
    this.transcriptBackgroundVersion = 1,
    this.customThemeLight,
    this.customThemeDark,
  }) {
    this.guidRefs = guidRefs ?? [guid];
    customAvatarPath = customAvatar;
    customBackgroundPath = customBackground;
    pinIndex = pinnedIndex;
    if (textFieldAttachments.isEmpty) textFieldAttachments = [];
    if (latestMessage != null) dbOnlyLatestMessageDate ??= latestMessage.dateCreated;
  }

  factory Chat.fromMap(Map<String, dynamic> json) {
    final message = json['lastMessage'] != null ? Message.fromMap(json['lastMessage']!.cast<String, Object>()) : null;
    return Chat(
      id: json["ROWID"] ?? json["id"],
      guid: json["guid"],
      chatIdentifier: json["chatIdentifier"],
      participants:
          (json['participants'] as List? ?? []).map((e) => Handle.fromMap(e!.cast<String, Object>())).toList(),
      isArchived: json['isArchived'] ?? false,
      muteType: json["muteType"],
      muteArgs: json["muteArgs"],
      isPinned: json["isPinned"] ?? false,
      hasUnreadMessage: json["hasUnreadMessage"] ?? false,
      latestMessage: message,
      displayName: json["displayName"],
      customAvatar: json['_customAvatarPath'],
      customBackground: json['_customBackgroundPath'],
      pinnedIndex: json['_pinIndex'],
      autoSendReadReceipts: json["autoSendReadReceipts"],
      autoSendTypingIndicators: json["autoSendTypingIndicators"],
      dateDeleted: parseDate(json["dateDeleted"]),
      style: json["style"],
      lockChatName: json["lockChatName"] ?? false,
      lockChatIcon: json["lockChatIcon"] ?? false,
      lastReadMessageGuid: json["lastReadMessageGuid"],
      apnTitle: json["apnTitle"],
      usingHandle: json["usingHandle"],
      isRpSms: json["isRpSms"] ?? false,
      guidRefs: json["guidRefs"]?.cast<String>() ?? [],
      telephonyId: json["telephonyId"],
      shareZenMode: json["shareZenMode"],
      notifsSilenced: json["notifsSilenced"] ?? false,
      dateNotifiedAnyways: parseDate(json["dateNotifiedAnyways"]),
      zenModeIsShared: json["zenModeIsShared"],
      senderIsKnown: json["senderIsKnown"] ?? true,
      isRoutingStub: json["isRoutingStub"] ?? false,
      transcriptPosterPath: json["transcriptPosterPath"],
      transcriptBackgroundVersion: json["transcriptBackgroundVersion"] ?? 1,
      customThemeLight: json["customThemeLight"],
      customThemeDark: json["customThemeDark"],
      textFieldText: json["textFieldText"],
      textFieldAnnotations: json["textFieldAnnotations"],
      textFieldAttachments: (json["textFieldAttachments"] as List?)?.cast<String>() ?? const [],
    );
  }

  /// Save a chat to the DB asynchronously (non-blocking)
  Future<Chat> saveAsync({
    bool updateMuteType = false,
    bool updateMuteArgs = false,
    bool updateIsPinned = false,
    bool updatePinIndex = false,
    bool updateIsArchived = false,
    bool updateHasUnreadMessage = false,
    bool updateAutoSendReadReceipts = false,
    bool updateAutoSendTypingIndicators = false,
    bool updateCustomAvatarPath = false,
    bool updateCustomBackgroundPath = false,
    bool updateTextFieldText = false,
    bool updateTextFieldAnnotations = false,
    bool updateTextFieldAttachments = false,
    bool updateDisplayName = false,
    bool updateDateDeleted = false,
    bool updateLockChatName = false,
    bool updateLockChatIcon = false,
    bool updateLastReadMessageGuid = false,
    bool updateLatestMessage = false,
    bool updateGroupVersion = false,
    bool updateUsingHandle = false,
    bool updateIsSms = false,
    bool updateAPNTitle = false,
    bool updateGuidRefs = false,
    bool updateTelephonyId = false,
    bool updateNotifsSilenced = false,
    bool updateZenModeIsShared = false,
    bool updateShareZenMode = false,
    bool updateDateNotifiedAnyways = false,
    bool updateSenderIsKnown = false,
    bool updateTranscriptPosterPath = false,
    bool updateTranscriptBackgroundVersion = false,
    bool updateCkRecordId = false,
    bool updateCkSyncState = false,
    bool updateAttachmentGuid = false,
    bool updateCustomThemes = false,
  }) async {
    if (kIsWeb) return this;

    await ChatInterface.saveChat(
      guid: guid,
      chatData: toMap(),
      updateFlags: {
        'updateMuteType': updateMuteType,
        'updateMuteArgs': updateMuteArgs,
        'updateIsPinned': updateIsPinned,
        'updatePinIndex': updatePinIndex,
        'updateIsArchived': updateIsArchived,
        'updateHasUnreadMessage': updateHasUnreadMessage,
        'updateAutoSendReadReceipts': updateAutoSendReadReceipts,
        'updateAutoSendTypingIndicators': updateAutoSendTypingIndicators,
        'updateCustomAvatarPath': updateCustomAvatarPath,
        'updateCustomBackgroundPath': updateCustomBackgroundPath,
        'updateTextFieldText': updateTextFieldText,
        'updateTextFieldAnnotations': updateTextFieldAnnotations,
        'updateTextFieldAttachments': updateTextFieldAttachments,
        'updateDisplayName': updateDisplayName,
        'updateDateDeleted': updateDateDeleted,
        'updateLockChatName': updateLockChatName,
        'updateLockChatIcon': updateLockChatIcon,
        'updateLastReadMessageGuid': updateLastReadMessageGuid,
        'updateLatestMessage': updateLatestMessage,
        'updateGroupVersion': updateGroupVersion,
        'updateUsingHandle': updateUsingHandle,
        'updateIsSms': updateIsSms,
        'updateAPNTitle': updateAPNTitle,
        'updateGuidRefs': updateGuidRefs,
        'updateTelephonyId': updateTelephonyId,
        'updateNotifsSilenced': updateNotifsSilenced,
        'updateZenModeIsShared': updateZenModeIsShared,
        'updateShareZenMode': updateShareZenMode,
        'updateDateNotifiedAnyways': updateDateNotifiedAnyways,
        'updateSenderIsKnown': updateSenderIsKnown,
        'updateTranscriptPosterPath': updateTranscriptPosterPath,
        'updateTranscriptBackgroundVersion': updateTranscriptBackgroundVersion,
        'updateCkRecordId': updateCkRecordId,
        'updateCkSyncState': updateCkSyncState,
        'updateAttachmentGuid': updateAttachmentGuid,
        'updateCustomThemes': updateCustomThemes,
      },
    );

    return this;
  }

  Chat save({
    bool updateMuteType = false,
    bool updateMuteArgs = false,
    bool updateIsPinned = false,
    bool updatePinIndex = false,
    bool updateIsArchived = false,
    bool updateHasUnreadMessage = false,
    bool updateAutoSendReadReceipts = false,
    bool updateAutoSendTypingIndicators = false,
    bool updateCustomAvatarPath = false,
    bool updateCustomBackgroundPath = false,
    bool updateTextFieldText = false,
    bool updateTextFieldAnnotations = false,
    bool updateTextFieldAttachments = false,
    bool updateDisplayName = false,
    bool updateDateDeleted = false,
    bool updateLockChatName = false,
    bool updateLockChatIcon = false,
    bool updateLastReadMessageGuid = false,
    bool updateGroupVersion = false,
    bool updateUsingHandle = false,
    bool updateIsSms = false,
    bool updateAPNTitle = false,
    bool updateGuidRefs = false,
    bool updateTelephonyId = false,
    bool updateNotifsSilenced = false,
    bool updateZenModeIsShared = false,
    bool updateShareZenMode = false,
    bool updateDateNotifiedAnyways = false,
    bool updateSenderIsKnown = false,
    bool updateTranscriptPosterPath = false,
    bool updateTranscriptBackgroundVersion = false,
    bool updateCkRecordId = false,
    bool updateCkSyncState = false,
    bool updateAttachmentGuid = false,
  }) {
    if (kIsWeb) return this;

    Database.runInTransaction(TxMode.write, () {
      final existing = Chat.findOne(guid: guid);
      id = existing?.id ?? id;

      muteType = updateMuteType ? muteType : existing?.muteType ?? muteType;
      muteArgs = updateMuteArgs ? muteArgs : existing?.muteArgs ?? muteArgs;
      isPinned = updateIsPinned ? isPinned : existing?.isPinned ?? isPinned;
      pinIndex = updatePinIndex ? pinIndex : existing?.pinIndex ?? pinIndex;
      isArchived = updateIsArchived ? isArchived : existing?.isArchived ?? isArchived;
      hasUnreadMessage = updateHasUnreadMessage ? hasUnreadMessage : existing?.hasUnreadMessage ?? hasUnreadMessage;
      autoSendReadReceipts =
          updateAutoSendReadReceipts ? autoSendReadReceipts : existing?.autoSendReadReceipts ?? autoSendReadReceipts;
      autoSendTypingIndicators = updateAutoSendTypingIndicators
          ? autoSendTypingIndicators
          : existing?.autoSendTypingIndicators ?? autoSendTypingIndicators;
      customAvatarPath = updateCustomAvatarPath ? customAvatarPath : existing?.customAvatarPath ?? customAvatarPath;
      customBackgroundPath =
          updateCustomBackgroundPath ? customBackgroundPath : existing?.customBackgroundPath ?? customBackgroundPath;
      textFieldText = updateTextFieldText ? textFieldText : existing?.textFieldText ?? textFieldText;
      textFieldAnnotations =
          updateTextFieldAnnotations ? textFieldAnnotations : existing?.textFieldAnnotations ?? textFieldAnnotations;
      textFieldAttachments =
          updateTextFieldAttachments ? textFieldAttachments : existing?.textFieldAttachments ?? textFieldAttachments;
      displayName = updateDisplayName ? displayName : existing?.displayName ?? displayName;
      dateDeleted = updateDateDeleted ? dateDeleted : existing?.dateDeleted ?? dateDeleted;
      lockChatName = updateLockChatName ? lockChatName : existing?.lockChatName ?? lockChatName;
      lockChatIcon = updateLockChatIcon ? lockChatIcon : existing?.lockChatIcon ?? lockChatIcon;
      lastReadMessageGuid =
          updateLastReadMessageGuid ? lastReadMessageGuid : existing?.lastReadMessageGuid ?? lastReadMessageGuid;
      groupVersion = updateGroupVersion ? groupVersion : existing?.groupVersion ?? groupVersion;
      apnTitle = updateAPNTitle ? apnTitle : existing?.apnTitle ?? apnTitle;
      usingHandle = updateUsingHandle ? usingHandle : existing?.usingHandle ?? usingHandle;
      isRpSms = updateIsSms ? isRpSms : existing?.isRpSms ?? isRpSms;
      guidRefs = updateGuidRefs ? guidRefs : existing?.guidRefs ?? guidRefs;
      telephonyId = updateTelephonyId ? telephonyId : existing?.telephonyId ?? telephonyId;
      shareZenMode = updateShareZenMode ? shareZenMode : existing?.shareZenMode ?? shareZenMode;
      notifsSilenced = updateNotifsSilenced ? notifsSilenced : existing?.notifsSilenced ?? notifsSilenced;
      dateNotifiedAnyways =
          updateDateNotifiedAnyways ? dateNotifiedAnyways : existing?.dateNotifiedAnyways ?? dateNotifiedAnyways;
      zenModeIsShared = updateZenModeIsShared ? zenModeIsShared : existing?.zenModeIsShared ?? zenModeIsShared;
      senderIsKnown = updateSenderIsKnown ? senderIsKnown : existing?.senderIsKnown ?? senderIsKnown;
      transcriptPosterPath =
          updateTranscriptPosterPath ? transcriptPosterPath : existing?.transcriptPosterPath ?? transcriptPosterPath;
      transcriptBackgroundVersion = updateTranscriptBackgroundVersion
          ? transcriptBackgroundVersion
          : existing?.transcriptBackgroundVersion ?? transcriptBackgroundVersion;
      ckRecordId = updateCkRecordId ? ckRecordId : existing?.ckRecordId ?? ckRecordId;
      ckSyncState = updateCkSyncState ? ckSyncState : existing?.ckSyncState ?? ckSyncState;
      photoAttachmentGuid =
          updateAttachmentGuid ? photoAttachmentGuid : existing?.photoAttachmentGuid ?? photoAttachmentGuid;
      cloudData = existing?.cloudData ?? cloudData;
      cloudGuid = existing?.cloudGuid ?? cloudGuid;

      for (int i = 0; i < participants.length; i++) {
        participants[i] = participants[i].save();
      }

      dbOnlyLatestMessageDate ??= dbLatestMessage.target?.dateCreated;
      try {
        id = Database.chats.put(this);
        final stored = id == null ? null : Database.chats.get(id!);
        if (stored != null && participants.isNotEmpty) {
          stored.handles.clear();
          stored.handles.addAll(participants);
          stored.handles.applyToDb();
        }
      } on UniqueViolationException catch (_) {}
    });

    unawaited(saveAsync(
      updateMuteType: updateMuteType,
      updateMuteArgs: updateMuteArgs,
      updateIsPinned: updateIsPinned,
      updatePinIndex: updatePinIndex,
      updateIsArchived: updateIsArchived,
      updateHasUnreadMessage: updateHasUnreadMessage,
      updateAutoSendReadReceipts: updateAutoSendReadReceipts,
      updateAutoSendTypingIndicators: updateAutoSendTypingIndicators,
      updateCustomAvatarPath: updateCustomAvatarPath,
      updateCustomBackgroundPath: updateCustomBackgroundPath,
      updateTextFieldText: updateTextFieldText,
      updateTextFieldAnnotations: updateTextFieldAnnotations,
      updateTextFieldAttachments: updateTextFieldAttachments,
      updateDisplayName: updateDisplayName,
      updateDateDeleted: updateDateDeleted,
      updateLockChatName: updateLockChatName,
      updateLockChatIcon: updateLockChatIcon,
      updateLastReadMessageGuid: updateLastReadMessageGuid,
      updateGroupVersion: updateGroupVersion,
      updateUsingHandle: updateUsingHandle,
      updateIsSms: updateIsSms,
      updateAPNTitle: updateAPNTitle,
      updateGuidRefs: updateGuidRefs,
      updateTelephonyId: updateTelephonyId,
      updateNotifsSilenced: updateNotifsSilenced,
      updateZenModeIsShared: updateZenModeIsShared,
      updateShareZenMode: updateShareZenMode,
      updateDateNotifiedAnyways: updateDateNotifiedAnyways,
      updateSenderIsKnown: updateSenderIsKnown,
      updateTranscriptPosterPath: updateTranscriptPosterPath,
      updateTranscriptBackgroundVersion: updateTranscriptBackgroundVersion,
      updateCkRecordId: updateCkRecordId,
      updateCkSyncState: updateCkSyncState,
      updateAttachmentGuid: updateAttachmentGuid,
    ));
    return this;
  }

  Future<String> ensureHandle() async {
    if (usingHandle != null && isRpSms) {
      final acceptableHandles = isRoutingStub
          ? await api.getMyPhoneHandles(state: PushSvc.state!.client)
          : SettingsSvc.settings.smsForwardingTargets.keys.toList();
      if (!acceptableHandles.contains(usingHandle)) {
        usingHandle = null;
      }
    }

    if (usingHandle == null) {
      if (isRpSms) {
        usingHandle = isRoutingStub
            ? (await api.getMyPhoneHandles(state: PushSvc.state!.client)).first
            : SettingsSvc.settings.smsForwardingTargets.keys.firstOrNull;
      } else {
        usingHandle = await (BackendSvc as RustPushBackend).getDefaultHandle();
      }
      save(updateUsingHandle: true);
    }

    return usingHandle!;
  }

  Future<bool> shouldRoute() async {
    final myHandles = await api.getMyPhoneHandles(state: PushSvc.state!.client);
    return myHandles.contains(await ensureHandle());
  }

  Future<int> getPersonalConfig() async {
    if (participants.length > 1 || participants.isEmpty || !Platform.isAndroid) return 0;

    final isStarredContact = await MethodChannelSvc.invokeMethod("is-conversation-exempt", {
      "mode": "star",
      "contactId": participants.first.contactsV2.firstOrNull?.id,
    });

    final isPriority = await MethodChannelSvc.invokeMethod("is-conversation-exempt", {
      "mode": "priority",
      "guid": guid,
    });

    return ((isStarredContact == true ? 1 : 0) << 0) | ((isPriority == true ? 1 : 0) << 1);
  }

  void fixZenModeShared() async {
    if (!SettingsSvc.settings.enableShareZen.value) return;
    final participantContact = ContactV2.findOne(address: handles.firstOrNull?.address);
    final wantsZenMode = (shareZenMode ?? true) && participantContact != null && !participantContact.isSharedSuggestion;
    final config = wantsZenMode ? await getPersonalConfig() : null;
    if (config == zenModeIsShared) return;
    final statuskit = PushSvc.state?.icloudServices?.statuskitClient;
    if (statuskit == null) return;

    if (wantsZenMode) {
      await api.inviteToChannel(
        status: statuskit,
        handle: await ensureHandle(),
        to: {getRustHandlesExcludingMine()[0]: await configForMask(config!)},
      );
      zenModeIsShared = config;
      save(updateZenModeIsShared: true);
    } else {
      final query = Database.chats
          .query(Chat_.zenModeIsShared
              .notNull()
              .and(Chat_.dbOnlyLatestMessageDate.greaterThanDate(DateTime.now().subtract(const Duration(days: 7)))))
          .build();
      final results = query.find();
      query.close();

      final sendMap = <String, Map<String, api.StatusKitPersonalConfig>>{};
      for (final result in results) {
        if (result.guid == guid) continue;
        final handle = await result.ensureHandle();
        sendMap.putIfAbsent(handle, () => {});
        sendMap[handle]![result.getRustHandlesExcludingMine()[0]] = await configForMask(result.zenModeIsShared!);
      }

      await api.resetChannelKeys(status: statuskit);
      for (final entry in sendMap.entries) {
        await api.inviteToChannel(status: statuskit, handle: entry.key, to: entry.value);
      }
      zenModeIsShared = null;
      save(updateZenModeIsShared: true);

      final olderQuery = Database.chats
          .query(Chat_.zenModeIsShared
              .notNull()
              .and(Chat_.dbOnlyLatestMessageDate.lessThanDate(DateTime.now().subtract(const Duration(days: 7)))))
          .build();
      final older = olderQuery.find();
      olderQuery.close();
      for (final item in older) {
        item.zenModeIsShared = null;
      }
      Database.chats.putMany(older);
    }
  }

  void updateAttachmentGuid(String guid) {
    if (customAvatarPath == null) {
      if (photoAttachmentGuid != null) {
        unawaited(Attachment.deleteAsync(photoAttachmentGuid!));
      }
      photoAttachmentGuid = null;
    } else {
      if (photoAttachmentGuid != null) {
        unawaited(Attachment.deleteAsync(photoAttachmentGuid!));
      }
      photoAttachmentGuid = "${guid}_0";
      final data = Attachment(
        guid: photoAttachmentGuid,
        isOutgoing: true,
        transferName: "GroupPhotoImage",
        totalBytes: File(customAvatarPath!).lengthSync(),
        metadata: {},
      );
      final directory = Directory(data.directory);
      if (!directory.existsSync()) {
        directory.createSync(recursive: true);
      }
      File(customAvatarPath!).copySync(data.path);
      unawaited(data.saveAsync(null));
    }
  }

  static Future<Chat> findFromCloud(api.CloudChat c) async {
    var chat = Chat.findByRustGuid(c.groupId);
    if (chat != null) return chat;

    final byIdentifierQuery = Database.chats.query(Chat_.chatIdentifier.equals(c.chatIdentifier)).build();
    final byIdentifier = byIdentifierQuery.findFirst();
    byIdentifierQuery.close();
    if (byIdentifier != null) return byIdentifier;

    var cond = Chat_.isRoutingStub.equals(false);
    if (c.displayName != null) {
      cond = cond.and(Chat_.apnTitle.equals(c.displayName!));
    }
    final query = (Database.chats.query(cond)
          ..linkMany(Chat_.handles, Handle_.address.oneOf(c.participants.map((e) => e.uri).toList())))
        .build();
    final results = query.find();
    query.close();

    final result = results.firstWhereOrNull((element) {
      final participantsCopy = c.participants.map((e) => e.uri).toList();
      for (final handle in element.handles) {
        final included = participantsCopy.contains(handle.address);
        if (!included) return false;
        participantsCopy.remove(handle.address);
      }
      return participantsCopy.isEmpty;
    });
    if (result != null) return result;

    chat = await BackendSvc.createChat(c.participants.map((p) => p.uri).toList(), null, c.serviceName,
        existingGuid: c.groupId);
    chat.senderIsKnown = true;
    chat.save(updateSenderIsKnown: true);
    return chat;
  }

  void removeProfilePhoto() {
    try {
      final file = File(customAvatarPath!);
      file.deleteSync();
    } catch (_) {}
    customAvatarPath = null;
  }

  List<String> getRustHandlesExcludingMine() {
    final currentParticipants = handles.isNotEmpty ? handles.toList() : participants;
    return currentParticipants.map((handle) {
      return handle.address.isEmail ? "mailto:${handle.address}" : "tel:${handle.address}";
    }).toList();
  }

  Future<api.ConversationData> getConversationData() async {
    final currentHandles = getRustHandlesExcludingMine()..add(await ensureHandle());
    return api.ConversationData(
      participants: currentHandles,
      cvName: apnTitle,
      senderGuid: guid,
      afterGuid: sendLastMessage.stagingGuid ?? sendLastMessage.guid,
    );
  }

  Future<api.CloudChat> toCloud() async {
    api.CloudChat existing;
    if (cloudData != null) {
      existing = api.restoreCloudChat(data: cloudData!);
    } else {
      chatIdentifier = participants.length == 1
          ? participants[0].address
          : "chat${(Random().nextInt(pow(2, 32).toInt()) << 32) | Random().nextInt(pow(2, 32).toInt())}";
      cloudGuid ??= guid;
      existing = api.CloudChat(
        style: isGroup ? 43 : 45,
        isFiltered: 0,
        successfulQuery: 1,
        state: 3,
        chatIdentifier: chatIdentifier!,
        groupId: cloudGuid!,
        serviceName: "iMessage",
        originalGroupId: cloudGuid!,
        properties: api.CloudProp(
          numberOfTimesRespondedtoThread: 3,
          shouldForceToSms: false,
          legacyGroupIdentifiers: [],
          messageHandshakeState: 1,
        ),
        participants: participants.map((p) => api.CloudParticipant(uri: p.address)).toList(),
        prop001: const api.CloudProp001(syndicationType: 0),
        lastReadMessageTimestamp:
            dbOnlyLatestMessageDate == null ? 0 : RustPushBBUtils.nsSinceAppleEpoch(dbOnlyLatestMessageDate!),
        lastAddressedHandle: (await ensureHandle()).replaceFirst("mailto:", "").replaceFirst("tel:", ""),
        guid: "iMessage;${isGroup ? '+' : '-'};$chatIdentifier",
        displayName: displayName,
        proto001: api.encodeChatproto(chat: const api.ChatProto(unk1: 0)),
      );
    }
    existing.style = isGroup ? 43 : 45;
    existing.chatIdentifier = chatIdentifier!;
    existing.participants = participants.map((p) => api.CloudParticipant(uri: p.address)).toList();
    existing.lastReadMessageTimestamp =
        dbOnlyLatestMessageDate == null ? 0 : RustPushBBUtils.nsSinceAppleEpoch(dbOnlyLatestMessageDate!);
    existing.lastAddressedHandle = (await ensureHandle()).replaceFirst("mailto:", "").replaceFirst("tel:", "");
    existing.displayName = displayName;
    if (existing.properties != null) {
      existing.properties!.pv = groupVersion ?? 1;
      existing.properties!.lastSeenMessageGuid = lastReadMessageGuid;
      existing.properties!.lastModificationDate = api.dateNow();
      existing.properties!.groupPhotoGuid =
          photoAttachmentGuid != null ? unconvertAttachmentGuid(photoAttachmentGuid!) : null;
    }
    if (customAvatarPath == null) {
      existing.groupPhoto = null;
      existing.groupPhotoGuid = null;
    } else {
      existing.groupPhotoGuid = photoAttachmentGuid != null ? unconvertAttachmentGuid(photoAttachmentGuid!) : null;
    }
    return existing;
  }

  String unconvertAttachmentGuid(String guid) {
    final items = guid.split("_");
    if (items.length == 1) return guid;
    return "at_${items[1]}_${items[0]}";
  }

  bool applyFromCloud(api.CloudChat c, String record) {
    chatIdentifier = c.chatIdentifier;
    ckRecordId = record;
    cloudGuid = c.groupId;
    ckSyncState = c.properties?.pv == (groupVersion ?? 1);
    if (c.properties?.pv == null || c.properties!.pv! <= (groupVersion ?? 1)) {
      Database.chats.put(this);
      return false;
    }
    Logger.info("Syncing new chat");
    style = c.style;
    lastReadMessageGuid = c.properties?.lastSeenMessageGuid;
    groupVersion = c.properties?.pv;
    handles.clear();
    handles.addAll(c.participants.map((i) => RustPushBBUtils.rustHandleToBB(i.uri)));
    handles.applyToDb();

    usingHandle = c.lastAddressedHandle.isEmail ? "mailto:${c.lastAddressedHandle}" : "tel:${c.lastAddressedHandle}";
    displayName = c.displayName;
    dbOnlyLatestMessageDate = RustPushBBUtils.fromNsSinceAppleEpoch(c.lastReadMessageTimestamp);
    cloudData = api.saveCloudChat(value: c);
    ckSyncState = true;

    if (c.groupPhoto != null) {
      customAvatarPath = getIconPath(0);
    } else if (customAvatarPath != null) {
      File(customAvatarPath!).deleteSync();
      customAvatarPath = null;
    }

    Database.chats.put(this);
    return true;
  }

  static Future<Chat> getChatForTel(int tid, List<String> participants) async {
    final byTid = Database.chats
        .query(Chat_.telephonyId.equals(tid).and(Chat_.dateDeleted.isNull()).and(Chat_.isRoutingStub.equals(true)))
        .build();
    final existingByTid = byTid.findFirst();
    byTid.close();
    if (existingByTid != null) return existingByTid;

    final query = (Database.chats
            .query(Chat_.dateDeleted.isNull().and(Chat_.isRpSms.equals(true)).and(Chat_.isRoutingStub.equals(true)))
          ..linkMany(Chat_.handles, Handle_.address.oneOf(participants)))
        .build();
    final results = query.find();
    query.close();

    Chat? result = results.firstWhereOrNull((element) {
      final participantsCopy = [...participants];
      for (final handle in element.handles) {
        final included = participantsCopy.contains(handle.address);
        if (!included) return false;
        participantsCopy.remove(handle.address);
      }
      return participantsCopy.isEmpty;
    });
    if (result == null) {
      result = await BackendSvc.createChat(participants, null, "SMS");
      result.isRoutingStub = true;
      ChatsSvc.updateChat(result);
    }
    result.telephonyId = tid;
    result.save(updateTelephonyId: true);
    return result;
  }

  Future<void> deliverSMS(String sender, bool fromMe, List<Map<String, dynamic>> parts) async {
    if (!SettingsSvc.settings.isSmsRouter.value) return;
    if (sender.isEmail) return;

    if (fromMe && "tel:$sender" != usingHandle) {
      Logger.info("Chat delivering sms, handle $usingHandle not sms handle $sender");
      usingHandle = "tel:$sender";
      save(updateUsingHandle: true);
    }

    var handle = Handle.findOne(addressAndService: HandleLookupKey(sender, "iMessage"));
    if (handle == null) {
      handle = Handle(address: sender);
      handle.save();
    }
    if (handle.originalROWID == null) {
      handle.originalROWID = handle.id!;
      handle.save();
    }

    for (final part in parts) {
      final partContent = part["body"] is Uint8List
          ? part["body"] as Uint8List
          : Uint8List.fromList((part["body"] as List).cast<int>().toList());
      if (part["contentType"] == "application/smil") continue;
      if (part["contentType"] == "text/plain") {
        final bodyString = utf8.decode(partContent);
        if (bodyString.trim().isEmpty) continue;
        final message = Message(
          text: bodyString,
          threadOriginatorPart: "0:0:0",
          dateCreated: DateTime.now(),
          hasAttachments: false,
          isFromMe: fromMe,
          guid: part["id"] as String,
          handleId: 0,
          handle: handle,
          hasDdResults: true,
          hasBeenForwarded: true,
          attributedBody: [
            AttributedBody(
              string: bodyString,
              runs: [
                Run(range: [0, bodyString.length], attributes: Attributes(messagePart: 0))
              ],
            ),
          ],
        );
        await BackendSvc.sendMessage(this, message);
        if (fromMe) {
          await (BackendSvc as RustPushBackend).confirmSmsSent(message, this, true);
        }
      } else {
        final myUuid = "${part["id"]}_0";
        final data = await rootBundle.loadString("assets/rustpush/uti-map.json");
        final utiMap = jsonDecode(data);
        final message = Message(
          text: " ",
          threadOriginatorPart: "0:0:0",
          dateCreated: DateTime.now(),
          hasAttachments: true,
          isFromMe: fromMe,
          guid: part["id"] as String,
          handleId: 0,
          handle: handle,
          hasDdResults: true,
          hasBeenForwarded: true,
          attributedBody: [
            AttributedBody(
              string: " ",
              runs: [
                Run(range: [0, 1], attributes: Attributes(attachmentGuid: myUuid, messagePart: 0))
              ],
            ),
          ],
        )..attachments = [
            Attachment(
              guid: myUuid,
              uti: utiMap[part["contentType"] as String] ?? "public.data",
              mimeType: part["contentType"] as String,
              isOutgoing: false,
              bytes: partContent,
              totalBytes: partContent.length,
              transferName: "${part["id"]}.${extensionFromMime(part["contentType"] as String) ?? "bin"}",
            ),
          ];
        final attachment = message.attachments.first!;
        final directory = Directory(attachment.directory);
        if (!directory.existsSync()) {
          directory.createSync(recursive: true);
        }
        await File(attachment.path).writeAsBytes(partContent);
        await (BackendSvc as RustPushBackend).forwardMMSAttachment(this, message, message.attachments.first!);
        File(message.attachments.first!.path).deleteSync();
        if (fromMe) {
          await (BackendSvc as RustPushBackend).confirmSmsSent(message, this, true);
        }
      }
    }
  }

  /// Change a chat's display name
  Future<Chat> changeNameAsync(String? name) async {
    if (kIsWeb) {
      displayName = name;
      return this;
    }
    displayName = name;
    await saveAsync(updateDisplayName: true);
    return this;
  }

  Chat changeName(String? name) {
    if (kIsWeb) {
      displayName = name;
      return this;
    }
    displayName = name;
    save(updateDisplayName: true);
    return this;
  }

  /// Get a chat's title
  String getTitle() => isNullOrEmpty(displayName) ? getChatCreatorSubtitle() : displayName!;

  /// Get a chat's title
  String getChatCreatorSubtitle() {
    final count = handles.length;
    if (count == 0) {
      if (chatIdentifier != null) {
        if (chatIdentifier!.startsWith("urn:biz")) {
          return "Business Chat";
        }
        return chatIdentifier!;
      }
      return "Unnamed chat";
    } else if (count == 1) {
      return handles.first.displayName;
    }

    if (count <= 4) {
      final buffer = StringBuffer();
      for (int i = 0; i < count; i++) {
        if (i > 0) buffer.write(i == count - 1 ? ' & ' : ', ');
        buffer.write(handles[i].shortName);
      }
      return buffer.toString();
    } else {
      final buffer = StringBuffer();
      for (int i = 0; i < 3; i++) {
        if (i > 0) buffer.write(', ');
        buffer.write(handles[i].shortName);
      }
      buffer.write(' & ${count - 3} others');
      return buffer.toString();
    }
  }

  /// Return whether or not the notification should be muted
  bool shouldMuteNotification(Message? message) {
    /// Filter unknown senders & sender doesn't have a contact, then don't notify
    if (SettingsSvc.settings.filterUnknownSenders.value && handles.length == 1 && handles.first.contactsV2.isEmpty) {
      return true;

      /// Check if global text detection is on and notify accordingly
    } else if (SettingsSvc.settings.globalTextDetection.value.isNotEmpty) {
      List<String> text = SettingsSvc.settings.globalTextDetection.value.split(",");
      for (String s in text) {
        if (message?.text?.toLowerCase().contains(s.toLowerCase()) ?? false) {
          return false;
        }
      }
      return true;

      /// Check if muted
    } else if (muteType == "mute") {
      return true;

      /// Check if the sender is muted
    } else if (muteType == "mute_individuals") {
      List<String> individuals = muteArgs!.split(",");
      return individuals.contains(message?.handleRelation.target?.address ?? "");

      /// Check if the chat is temporarily muted
    } else if (muteType == "temporary_mute") {
      DateTime time = DateTime.parse(muteArgs!);
      bool shouldMute = DateTime.now().toLocal().difference(time).inSeconds.isNegative;
      if (!shouldMute) {
        toggleMuteAsync(false);
      }
      return shouldMute;

      /// Check if the chat has specific text detection and notify accordingly
    } else if (muteType == "text_detection") {
      List<String> text = muteArgs!.split(",");
      for (String s in text) {
        if (message?.text?.toLowerCase().contains(s.toLowerCase()) ?? false) {
          return false;
        }
      }
      return true;
    }

    /// If reaction and notify reactions off, then don't notify, otherwise notify
    return !SettingsSvc.settings.notifyReactions.value &&
        ReactionTypes.toList().contains(message?.associatedMessageType ?? "");
  }

  /// Toggle unread status - pure DB operation
  /// Note: For full unread toggle with active chat awareness, use ChatsSvc.toggleChatHasUnread
  Future<Chat> toggleHasUnreadAsync(bool hasUnread,
      {bool force = false, bool clearLocalNotifications = true, bool privateMark = true}) async {
    if (kIsDesktop && !hasUnread) {
      NotificationsSvc.clearDesktopNotificationsForChat(guid);
    }

    if (hasUnreadMessage == hasUnread && !force) return this;
    hasUnreadMessage = hasUnread;
    await saveAsync(updateHasUnreadMessage: true);

    try {
      if (clearLocalNotifications && !hasUnread) {
        ChatInterface.clearNotificationForChat(
          chatId: id!,
          chatGuid: guid,
        );
      }
      if (SettingsSvc.settings.enablePrivateAPI.value) {
        if (!hasUnread) {
          await BackendSvc.markRead(this, privateMark);
        } else {
          await BackendSvc.markUnread(this);
        }
      }
    } catch (_) {}

    return this;
  }

  Chat toggleHasUnread(bool hasUnread,
      {bool force = false, bool newOnMessage = false, bool clearLocalNotifications = true, bool privateMark = true}) {
    if (kIsDesktop && !hasUnread) {
      NotificationsSvc.clearDesktopNotificationsForChat(guid);
    }

    if (hasUnreadMessage == hasUnread && !force) return this;
    var changed = false;
    if (!ChatsSvc.isChatActive(guid) || !hasUnread || force) {
      changed = (Chat.findOne(guid: guid)?.hasUnreadMessage ?? hasUnreadMessage) != hasUnread || newOnMessage;
      hasUnreadMessage = hasUnread;
      save(updateHasUnreadMessage: true);
    }

    try {
      if (clearLocalNotifications && !hasUnread) {
        MethodChannelSvc.invokeMethod("delete-notification", {"notification_id": id, "tag": "new_message"});
      }
      if (privateMark && changed) {
        if (!hasUnread) {
          BackendSvc.markRead(
              this,
              SettingsSvc.settings.enablePrivateAPI.value &&
                  (autoSendReadReceipts ?? SettingsSvc.settings.privateMarkChatAsRead.value));
        } else {
          BackendSvc.markUnread(this);
        }
      }
    } catch (e, s) {
      Logger.warn("Failed to mark chat as read on message add", error: e, trace: s, tag: 'Chat');
    }

    return this;
  }

  /// Add message to chat - pure DB operation
  /// Note: For full message add with service updates, use ChatsSvc.addMessageToChat
  Future<MessageSaveResult> addMessage(Message message,
      {bool changeUnreadStatus = true,
      bool checkForMessageText = true,
      bool clearNotificationsIfFromMe = true,
      List<Attachment> attachments = const []}) async {
    // Save the message using the interface
    Message? latest = dbLatestMessage.target;
    Message? newMessage;
    bool isNewer = false;

    try {
      final result = await ChatInterface.addMessageToChat(
        messageData: message.toMap(),
        attachmentsData: attachments.map((e) => e.toMap()).toList(),
        chatData: toMap(),
        latestMessageData: (latest ?? Message(dateCreated: DateTime.fromMillisecondsSinceEpoch(0), guid: guid)).toMap(),
        checkForMessageText: checkForMessageText,
      );

      // Extract from MessageSaveResult
      newMessage = result.message;
      isNewer = result.isNewer;
    } catch (ex, stacktrace) {
      newMessage = Message.findOne(guid: message.guid);
      if (newMessage == null) {
        Logger.error("Failed to add message (GUID: ${message.guid}) to chat (GUID: $guid)",
            error: ex, trace: stacktrace);
      }
    }

    var effectiveMessage = newMessage ?? message;
    // Handle post-save operations on main thread
    if (isNewer) {
      setLatestMessage(effectiveMessage);
      if (dateDeleted != null) {
        dateDeleted = null;
        await saveAsync(updateDateDeleted: true);
      }
      if (isArchived! && !effectiveMessage.isFromMe! && SettingsSvc.settings.unarchiveOnNewMessage.value) {
        await toggleArchivedAsync(false);
      }
    }

    if (!(senderIsKnown ?? true) && effectiveMessage.isFromMe!) {
      senderIsKnown = true;
      cvc(this).reportJunkAvailable.value = !(senderIsKnown ?? true);
      await saveAsync(updateSenderIsKnown: true);
    }

    // Save the chat.
    // This will update the latestMessage info as well as update some
    // other fields that we want to "mimic" from the server
    await saveAsync();

    // If the incoming message was newer than the "last" one, set the unread status accordingly
    if (checkForMessageText && changeUnreadStatus && isNewer) {
      // Simple logic: mark read if from me, mark unread if not
      if (effectiveMessage.isFromMe!) {
        await toggleHasUnreadAsync(false, clearLocalNotifications: clearNotificationsIfFromMe, privateMark: false);
      } else {
        await toggleHasUnreadAsync(true, privateMark: false);
      }
    }

    // If the message is for adding or removing participants,
    // we need to ensure that all of the chat participants are correct by syncing with the server
    if (effectiveMessage.isParticipantEvent && checkForMessageText) {
      serverSyncParticipantsAsync();
    }

    // Return the saved message and isNewer flag
    return MessageSaveResult(effectiveMessage, isNewer);
  }

  Message addMessageLocal(Message message,
      {bool changeUnreadStatus = true, bool checkForMessageText = true, bool clearNotificationsIfFromMe = true}) {
    final latest = dbLatestMessage.target;
    final isNewer = latest?.dateCreated == null || (message.dateCreated?.isAfter(latest!.dateCreated!) ?? false);

    if (isNewer) {
      dbLatestMessage.target = message;
      dbOnlyLatestMessageDate = message.dateCreated;
      if (dateDeleted != null) {
        dateDeleted = null;
        save(updateDateDeleted: true);
        unawaited(ChatsSvc.addChat(this));
      }
      if (isArchived! &&
          !message.isFromMe! &&
          SettingsSvc.settings.unarchiveOnNewMessage.value &&
          !(participants.firstOrNull?.isBlocked() ?? false)) {
        toggleArchived(false);
      }
    }

    if (!(senderIsKnown ?? true) && message.isFromMe!) {
      senderIsKnown = true;
      cvc(this).reportJunkAvailable.value = !(senderIsKnown ?? true);
      save(updateSenderIsKnown: true);
    }

    save();

    if (checkForMessageText && changeUnreadStatus && isNewer) {
      if (message.isFromMe!) {
        toggleHasUnread(
          false,
          clearLocalNotifications: clearNotificationsIfFromMe,
          force: ChatsSvc.isChatActive(guid),
          privateMark: ChatsSvc.isChatActive(guid),
          newOnMessage: !message.isFromMe!,
        );
      } else if (!ChatsSvc.isChatActive(guid)) {
        toggleHasUnread(true, privateMark: false);
      }
    }

    if (message.isParticipantEvent && checkForMessageText) {
      unawaited(serverSyncParticipantsAsync());
    }

    return message;
  }

  Future<void> serverSyncParticipantsAsync() async {
    if (BackendSvc.getRemoteService() == null) return;
    // Sync participants from server - delegates to service layer
    // Note: For full sync with service updates, this is called by ChatsSvc.addMessageToChat
    try {
      final response = await HttpSvc.chat.fetchOne(guid, withQuery: "participants");
      if (response.statusCode == 200 && response.data["data"] != null) {
        final chatData = response.data["data"];
        final updatedChat = (await ChatInterface.bulkSyncChats(chatsData: [chatData])).chats;
        if (updatedChat.isNotEmpty) {
          await updatedChat.first.saveAsync();
        }
      }
    } catch (ex, stacktrace) {
      Logger.error("Failed to sync participants", error: ex, trace: stacktrace);
    }
  }

  // count() method moved to ChatsService

  Future<List<Attachment>> getAttachmentsAsync({bool fetchDeleted = false}) async {
    if (kIsWeb || id == null) return [];

    final stopwatch = Stopwatch()..start();

    /// Query the messages for this chat using ObjectBox's async API
    final messageQuery = (Database.messages.query(fetchDeleted
            ? Message_.dateCreated.notNull().and(Message_.dateDeleted.isNull().or(Message_.dateDeleted.notNull()))
            : Message_.dateDeleted.isNull().and(Message_.dateCreated.notNull()))
          ..link(Message_.chat, Chat_.id.equals(id!))
          ..order(Message_.dateCreated, flags: Order.descending))
        .build();

    // Execute query in worker isolate
    final messages = await messageQuery.findAsync();
    messageQuery.close();

    if (messages.isEmpty) {
      stopwatch.stop();
      Logger.debug("Fetched 0 messages for chat $guid in ${stopwatch.elapsedMilliseconds} ms");
      return [];
    }

    // Get all message IDs to query attachments
    final messageIds = messages.map((e) => e.id!).toList();

    // Query attachments linked to these messages asynchronously
    final attachmentQuery = (Database.attachments.query(Attachment_.mimeType.notNull())
          ..link(Attachment_.message, Message_.id.oneOf(messageIds)))
        .build();

    final attachments = await attachmentQuery.findAsync();
    attachmentQuery.close();

    // Remove duplicate attachments from the list, just in case
    if (attachments.isNotEmpty) {
      final guids = attachments.map((e) => e.guid).toSet();
      attachments.retainWhere((element) => guids.remove(element.guid));
    }

    stopwatch.stop();
    Logger.debug("Fetched ${attachments.length} attachments for chat $guid in ${stopwatch.elapsedMilliseconds} ms");
    return attachments;
  }

  /// Gets messages synchronously - DO NOT use in performance-sensitive areas,
  /// otherwise prefer [getMessagesAsync]
  static List<Message> getMessages(Chat chat,
      {int offset = 0, int limit = 25, bool includeDeleted = false, bool getDetails = false}) {
    if (kIsWeb || chat.id == null) return [];
    return Database.runInTransaction(TxMode.read, () {
      final query = (Database.messages.query(includeDeleted
              ? Message_.dateCreated.notNull().and(Message_.dateDeleted.isNull().or(Message_.dateDeleted.notNull()))
              : Message_.dateDeleted.isNull().and(Message_.dateCreated.notNull()))
            ..link(Message_.chat, Chat_.id.equals(chat.id!))
            ..order(Message_.dateCreated, flags: Order.descending))
          .build();
      query
        ..limit = limit
        ..offset = offset;
      final messages = query.find();
      query.close();
      for (int i = 0; i < messages.length; i++) {
        Message message = messages[i];
        if (chat.handles.isNotEmpty && !message.isFromMe! && message.handleId != null && message.handleId != 0) {
          Handle? handle = chat.handles.firstWhereOrNull((e) => e.originalROWID == message.handleId) ??
              message.handleRelation.target;
          if (handle == null) {
            messages.remove(message);
            i--;
          }
        }
      }
      // fetch attachments and reactions if requested
      if (getDetails) {
        final messageGuids = messages.map((e) => e.guid!).toList();
        final associatedMessagesQuery = (Database.messages.query(Message_.associatedMessageGuid.oneOf(messageGuids))
              ..order(Message_.originalROWID))
            .build();
        List<Message> associatedMessages = associatedMessagesQuery.find();
        associatedMessagesQuery.close();
        associatedMessages = MessageHelper.normalizedAssociatedMessages(associatedMessages);
        for (Message m in messages) {
          m.associatedMessages = associatedMessages.where((e) => e.associatedMessageGuid == m.guid).toList();
        }
      }
      return messages;
    });
  }

  /// Fetch messages asynchronously with progressive loading
  /// Returns messages with attachments, then loads reactions in background
  static Future<List<Message>> getMessagesAsync(Chat chat,
      {int offset = 0,
      int limit = 25,
      bool includeDeleted = false,
      int? searchAround,
      Function? onSupplementalDataLoaded}) async {
    if (kIsWeb || chat.id == null) return [];

    final totalStopwatch = Stopwatch()..start();

    // PHASE 1: Query messages with attachments using interface/actions pattern
    final messages = await ChatInterface.getMessagesAsync(
      chatId: chat.id!,
      chatGuid: chat.guid,
      participantsData: chat.handles.map((e) => e.toMap()).toList(),
      offset: offset,
      limit: limit,
      includeDeleted: includeDeleted,
      searchAround: searchAround,
    );

    if (messages.isEmpty) {
      return messages;
    }

    // PHASE 2: Load reactions in background (non-blocking)
    final messageGuids = messages.map((e) => e.guid!).toList();

    // Don't await - let this run in background and call callback when done
    _loadSupplementalDataAsync(messages, messageGuids, totalStopwatch, onSupplementalDataLoaded);

    totalStopwatch.stop();
    Logger.debug("[getMessagesAsync] RETURNED (Phase 1 complete): ${totalStopwatch.elapsedMilliseconds}ms");

    // Return messages immediately (reactions/attachments will be added later)
    return messages;
  }

  /// Load reactions in background and append to messages
  static Future<void> _loadSupplementalDataAsync(
    List<Message> messages,
    List<String> messageGuids,
    Stopwatch totalStopwatch,
    Function? onComplete,
  ) async {
    final supplementalStopwatch = Stopwatch()..start();

    try {
      var associatedMessages = await ChatInterface.loadSupplementalData(
        messageGuids: messageGuids,
      );

      Logger.debug("[getMessagesAsync] Phase 2 - Supplemental query: ${supplementalStopwatch.elapsedMilliseconds}ms");

      // Normalize reactions
      associatedMessages = MessageHelper.normalizedAssociatedMessages(associatedMessages);

      // Append reactions to original messages
      int messagesWithReactions = 0;
      for (Message m in messages) {
        final messageReactions = associatedMessages.where((e) => e.associatedMessageGuid == m.guid).toList();
        m.associatedMessages = messageReactions;
        if (messageReactions.isNotEmpty) {
          messagesWithReactions++;
          Logger.debug("[getMessagesAsync] Phase 2 - Added ${messageReactions.length} reactions to message ${m.guid}",
              tag: "MessageReactivity");
        }
      }

      supplementalStopwatch.stop();
      Logger.debug(
          "[getMessagesAsync] Phase 2 - COMPLETE: ${supplementalStopwatch.elapsedMilliseconds}ms (${associatedMessages.length} reactions on $messagesWithReactions messages)");

      // Notify caller that supplemental data has been loaded
      if (onComplete != null) {
        Logger.debug("[getMessagesAsync] Phase 2 - Calling onComplete callback", tag: "MessageReactivity");
        onComplete();
      } else {
        Logger.warn("[getMessagesAsync] Phase 2 - No onComplete callback provided!", tag: "MessageReactivity");
      }
    } catch (ex, stacktrace) {
      Logger.error("Failed to load supplemental data for messages", error: ex, trace: stacktrace);
    }
  }

  void webSyncParticipants() {}

  /// Toggle pin status - pure DB operation
  /// Note: For full pin toggle with service updates, use ChatsSvc.toggleChatPin
  Future<Chat> togglePinAsync(bool isPinned) async {
    if (id == null) return this;
    this.isPinned = isPinned;
    _pinIndex.value = null;
    await saveAsync(updateIsPinned: true, updatePinIndex: true);
    return this;
  }

  Chat togglePin(bool isPinned) {
    if (id == null) return this;
    this.isPinned = isPinned;
    _pinIndex.value = null;
    save(updateIsPinned: true, updatePinIndex: true);
    return this;
  }

  Future<Chat> toggleMuteAsync(bool isMuted) async {
    if (id == null) return this;
    muteType = isMuted ? "mute" : null;
    muteArgs = null;
    await saveAsync(updateMuteType: true, updateMuteArgs: true);
    return this;
  }

  Chat toggleMute(bool isMuted) {
    if (id == null) return this;
    muteType = isMuted ? "mute" : null;
    muteArgs = null;
    save(updateMuteType: true, updateMuteArgs: true);
    return this;
  }

  /// Toggle archive status - pure DB operation
  /// Note: For full archive toggle with service updates, use ChatsSvc.toggleChatArchive
  Future<Chat> toggleArchivedAsync(bool isArchived) async {
    if (id == null) return this;
    isPinned = false;
    this.isArchived = isArchived;
    await saveAsync(updateIsPinned: true, updateIsArchived: true);
    return this;
  }

  Chat toggleArchived(bool isArchived) {
    if (id == null) return this;
    isPinned = false;
    this.isArchived = isArchived;
    save(updateIsPinned: true, updateIsArchived: true);
    return this;
  }

  Future<Chat> toggleAutoReadAsync(bool? autoSendReadReceipts) async {
    if (id == null) return this;
    this.autoSendReadReceipts = autoSendReadReceipts;
    await saveAsync(updateAutoSendReadReceipts: true);
    BackendSvc.markRead(this, autoSendReadReceipts ?? SettingsSvc.settings.privateMarkChatAsRead.value);
    return this;
  }

  Chat toggleAutoRead(bool? autoSendReadReceipts) {
    if (id == null) return this;
    this.autoSendReadReceipts = autoSendReadReceipts;
    save(updateAutoSendReadReceipts: true);
    BackendSvc.markRead(this, autoSendReadReceipts ?? SettingsSvc.settings.privateMarkChatAsRead.value);
    return this;
  }

  Future<Chat> toggleAutoTypeAsync(bool? autoSendTypingIndicators) async {
    if (id == null) return this;
    this.autoSendTypingIndicators = autoSendTypingIndicators;
    await saveAsync(updateAutoSendTypingIndicators: true);
    if (!(autoSendTypingIndicators ?? SettingsSvc.settings.privateSendTypingIndicators.value)) {
      unawaited(ChatInterface.stopTyping(chatGuid: guid));
    }
    return this;
  }

  Chat toggleAutoType(bool? autoSendTypingIndicators) {
    if (id == null) return this;
    this.autoSendTypingIndicators = autoSendTypingIndicators;
    save(updateAutoSendTypingIndicators: true);
    if (!(autoSendTypingIndicators ?? SettingsSvc.settings.privateSendTypingIndicators.value)) {
      BackendSvc.stoppedTyping(this);
    }
    return this;
  }

  /// Finds a chat - only use this method on Flutter Web!!!
  static Future<Chat?> findOneWeb({String? guid, String? chatIdentifier}) async {
    return null;
  }

  static Chat? findByHandle(String handle) {
    final query = (Database.chats.query()..linkMany(Chat_.handles, Handle_.address.oneOf([handle]))).build();
    final results = query.find();
    query.close();

    return results.firstWhereOrNull((res) => res.handles.length == 1);
  }

  static Chat? findByRustGuid(String guid) {
    final direct = Chat.findOne(guid: guid);
    if (direct != null) return direct;

    // prioritize finding by related GUID
    final query = Database.chats.query(Chat_.guidRefs.containsElement(guid)).build();
    final results = query.find();
    query.close();
    if (results.isNotEmpty) {
      // we found one!
      return results[0];
    }
    return null;
  }

  // if soft is false, return is never null
  // only null if soft is true and no matching chat is found
  static Future<Chat?> findByRust(api.ConversationData data, String service,
      {bool soft = false, bool routingStub = false}) async {
    if (data.participants.isEmpty) {
      throw Exception("empty participants!??");
    }

    if (data.senderGuid != null) {
      // first find by direct GUID
      final direct = Chat.findOne(guid: data.senderGuid);
      if (direct != null) return direct;

      // prioritize finding by related GUID
      final query = Database.chats.query(Chat_.guidRefs.containsElement(data.senderGuid!)).build();
      final results = query.find();
      query.close();
      if (results.isNotEmpty) {
        // we found one!
        return results[0];
      }
    }

    var (mine, dartParticipants) = await RustPushBBUtils.rustParticipantsToBB(data.participants);

    final name = data.cvName;

    var cond = Chat_.isRoutingStub.equals(routingStub).and(Chat_.isRpSms.equals(service == "SMS"));
    if (name != null) {
      cond = cond.and(Chat_.apnTitle.equals(name));
    }
    final query = (Database.chats.query(cond)
          ..linkMany(Chat_.handles, Handle_.address.oneOf(dartParticipants.map((e) => e.address).toList())))
        .build();
    final results = query.find();
    query.close();

    Logger.warn("Found ${results.length} candidates");

    var result = results.firstWhereOrNull((element) {
      var participantsCopy = [...dartParticipants];
      for (var handle in element.handles) {
        var included = participantsCopy.contains(handle);
        if (!included) {
          Logger.warn(
              "Bailing on candidate because ${handle.address} ${handle.id} is not ${participantsCopy.map((i) => "${i.address} ${i.id}").join(", ")} .. ${element.handles.map((i) => "${i.address} ${i.id}").join(", ")}");
          return false;
        }
        participantsCopy.remove(handle);
      }
      Logger.warn(
          "Bailing on candidate left ${participantsCopy.map((i) => "${i.address} ${i.id}").join(", ")} .. ${element.handles.map((i) => "${i.address} ${i.id}").join(", ")}");
      return participantsCopy.isEmpty;
    });
    if (result == null && !soft) {
      result = await BackendSvc.createChat(dartParticipants.map((e) => e.address).toList(), null, service,
          existingGuid: data.senderGuid);
      result.displayName = data.cvName;
      result.isRoutingStub = routingStub;
      result.apnTitle = data.cvName;
      if (mine.isNotEmpty) result.usingHandle = mine[0];
      result = result.save();
      ChatsSvc.updateChat(result);
    }
    return result;
  }

  /// Finds a chat - DO NOT use this method on Flutter Web!! Prefer [findOneWeb]
  /// instead!!
  static Chat? findOne({String? guid, String? chatIdentifier}) {
    if (guid != null) {
      final query = Database.chats.query(Chat_.guid.equals(guid)).build();
      final result = query.findFirst();
      query.close();
      return result;
    } else if (chatIdentifier != null) {
      final query = Database.chats.query(Chat_.chatIdentifier.equals(chatIdentifier)).build();
      final result = query.findFirst();
      query.close();
      return result;
    }
    return null;
  }

  static Future<List<Chat>> getChatsAsync({int limit = 15, int offset = 0, List<int> ids = const []}) async {
    if (kIsWeb) throw Exception("Use socket to get chats on Web!");

    final chats = await ChatInterface.getChatsAsync(
      limit: limit,
      offset: offset,
      ids: ids,
    );

    // Populate contact name cache on main thread for ALL chats in one transaction
    // The cache populated in the isolate doesn't transfer through JSON serialization
    // Database.runInTransaction(TxMode.read, () {
    //   for (Chat c in chats) {
    //     // Re-fetch handles from ObjectBox to get proper instances with relationships
    //     if (c._participants.isNotEmpty) {
    //       final handleIds = c._participants.map((h) => h.id).whereType<int>().where((id) => id != 0).toList();
    //       if (handleIds.isNotEmpty) {
    //         final handlesBox = Database.handles;
    //         final fetchedHandles = handlesBox.getMany(handleIds).whereType<Handle>().toList();
    //         c._participants = fetchedHandles;

    //         // Cache contact names while in transaction
    //         for (final handle in c._participants) {
    //           Logger.debug('[TEST] Handle has formatted address: ${handle.formattedAddress}');
    //           final contactCount = handle.contactsV2.length;
    //           if (contactCount > 0) {
    //             handle.cachedContactName = handle.contactsV2.first.displayName;
    //           } else {
    //             handle.cachedContactName = null;
    //           }
    //         }
    //       }
    //     }
    //   }
    // });

    return chats;
  }

  static Future<List<Chat>> bulkSyncChats(List<Chat> chats) async {
    if (kIsWeb) throw Exception("Web does not support saving chats!");
    if (chats.isEmpty) return [];

    return (await ChatInterface.bulkSyncChats(
      chatsData: chats.map((e) => e.toMap()).toList(),
    ))
        .chats;
  }

  static void delete(Chat chat) async {
    if (kIsWeb) return;
    if (ChatsSvc.activeChat?.chat.guid == chat.guid) {
      ChatsSvc.activeChat = null;
      await Future.delayed(const Duration(milliseconds: 500));
    }
    final messages = Chat.getMessages(chat);
    final attachments = await chat.getAttachmentsAsync();
    for (final attachment in attachments) {
      try {
        File(attachment.path).deleteSync();
      } catch (e) {
        Logger.debug("Failed to rm attachment $e");
      }
    }
    Database.runInTransaction(TxMode.write, () {
      Database.chats.remove(chat.id!);
      Database.messages.removeMany(messages.map((e) => e.id!).toList());
      Database.attachments.removeMany(attachments.map((e) => e.id!).toList());
    });
    if (chat.ckRecordId != null && !PushSvc.syncStopDelete) {
      final list = PrefsSvc.i.getStringList("chatDeletionIds-1") ?? [];
      list.add(chat.ckRecordId!);
      PrefsSvc.i.setStringList("chatDeletionIds-1", list);
    }
  }

  static void softDelete(Chat chat, {bool markDeleted = true}) async {
    if (kIsWeb) return;
    if (ChatsSvc.activeChat?.chat.guid == chat.guid) {
      ChatsSvc.activeChat = null;
      await Future.delayed(const Duration(milliseconds: 500));
    }
    Database.runInTransaction(TxMode.write, () {
      chat.dateDeleted = DateTime.now();
      chat.hasUnreadMessage = false;
      chat.save(updateDateDeleted: true, updateHasUnreadMessage: true);
      chat.clearTranscript();
    });
    if (markDeleted) {
      await BackendSvc.moveToRecycleBin(chat, null);
    }
  }

  static void unDelete(Chat chat) async {
    if (kIsWeb) return;
    Database.runInTransaction(TxMode.write, () {
      chat.dateDeleted = null;
      chat.senderIsKnown = chat.handles.any((handle) => ContactV2.findOne(address: handle.address) != null);
      chat.save(updateDateDeleted: true, updateSenderIsKnown: true);
    });
  }

  void clearTranscript() {
    if (kIsWeb) return;
    Database.runInTransaction(TxMode.write, () {
      final toDelete = List<Message>.from(messages);
      for (Message element in toDelete) {
        element.dateDeleted = DateTime.now().toUtc();
      }
      Database.messages.putMany(toDelete);
    });
  }

  void restoreTranscript() {
    if (kIsWeb) return;
    Database.runInTransaction(TxMode.write, () {
      final toDelete = List<Message>.from(messages);
      for (final element in toDelete) {
        element.dateDeleted = null;
      }
      Database.messages.putMany(toDelete);
    });
  }

  Future<void> clearTranscriptAsync() async {
    if (kIsWeb || id == null) return;

    await ChatInterface.clearTranscriptAsync(
      chatId: id!,
      chatGuid: guid,
    );
  }

  /// The messaging service this chat belongs to, derived from the GUID prefix.
  ChatServiceType get service => isRpSms ? ChatServiceType.sms : ChatServiceType.iMessage;

  bool get isTextForwarding => service == ChatServiceType.sms;

  bool get isSMS => service == ChatServiceType.sms;

  bool get isIMessage => service == ChatServiceType.iMessage;

  // Check style first so handles isn't required to be evaluated, which will incur a DB lookup.
  bool get isGroup => style == 43 || handles.length > 1;

  Chat merge(Chat other) {
    id ??= other.id;
    _customAvatarPath.value ??= other._customAvatarPath.value;
    _customBackgroundPath.value ??= other._customBackgroundPath.value;
    _pinIndex.value ??= other._pinIndex.value;
    autoSendReadReceipts ??= other.autoSendReadReceipts;
    autoSendTypingIndicators ??= other.autoSendTypingIndicators;
    textFieldText ??= other.textFieldText;
    textFieldAnnotations ??= other.textFieldAnnotations;
    if (textFieldAttachments.isEmpty) {
      textFieldAttachments.addAll(other.textFieldAttachments);
    }
    chatIdentifier ??= other.chatIdentifier;
    displayName ??= other.displayName;
    if (handles.isEmpty) {
      handles.addAll(other.handles);
    }
    hasUnreadMessage ??= other.hasUnreadMessage;
    isArchived ??= other.isArchived;
    isPinned ??= other.isPinned;
    if (dbLatestMessage.target == null && other.dbLatestMessage.target != null) {
      setLatestMessage(other.dbLatestMessage.target!);
    }
    muteArgs ??= other.muteArgs;
    dateDeleted ??= other.dateDeleted;
    style ??= other.style;
    apnTitle ??= other.apnTitle;
    usingHandle ??= other.usingHandle;
    shareZenMode ??= other.shareZenMode;
    dateNotifiedAnyways ??= other.dateNotifiedAnyways;
    zenModeIsShared ??= other.zenModeIsShared;
    senderIsKnown ??= other.senderIsKnown;
    transcriptPosterPath ??= other.transcriptPosterPath;
    groupVersion ??= other.groupVersion;
    telephonyId ??= other.telephonyId;
    isRpSms = isRpSms || other.isRpSms;
    notifsSilenced = notifsSilenced || other.notifsSilenced;
    isRoutingStub = isRoutingStub || other.isRoutingStub;
    if (guidRefs.isEmpty) {
      guidRefs.addAll(other.guidRefs);
    }
    return this;
  }

  static int sort(Chat? a, Chat? b) {
    // If they both are pinned & ordered, reflect the order
    if (a!.isPinned! && b!.isPinned! && a.pinIndex != null && b.pinIndex != null) {
      return a.pinIndex!.compareTo(b.pinIndex!);
    }

    // If b is pinned & ordered, but a isn't either pinned or ordered, return accordingly
    if (b!.isPinned! && b.pinIndex != null && (!a.isPinned! || a.pinIndex == null)) {
      return 1;
    }
    // If a is pinned & ordered, but b isn't either pinned or ordered, return accordingly
    if (a.isPinned! && a.pinIndex != null && (!b.isPinned! || b.pinIndex == null)) {
      return -1;
    }

    // Compare when one is pinned and the other isn't
    if (!a.isPinned! && b.isPinned!) {
      return 1;
    }
    if (a.isPinned! && !b.isPinned!) {
      return -1;
    }

    // Compare the last message dates (negate to sort newest first)
    final aDate = a.dbOnlyLatestMessageDate ?? DateTime.fromMillisecondsSinceEpoch(0);
    final bDate = b.dbOnlyLatestMessageDate ?? DateTime.fromMillisecondsSinceEpoch(0);
    return -aDate.compareTo(bDate);
  }

  String getIconPath(int responseLength) {
    return "${FilesystemSvc.appDocDir.path}/avatars/${FilesystemService.sanitizeGuid(guid)}/avatar-$responseLength.jpg";
  }

  static Future<void> getIcon(Chat c, {bool force = false}) async {
    if ((!force && c.lockChatIcon) || BackendSvc.getRemoteService() == null) return;
    final response = await BackendSvc.getRemoteService()!.chat.getIcon(c.guid).catchError((err, stack) async {
      Logger.error("Failed to get chat icon for chat ${c.getTitle()}", error: err, trace: stack);
      return Response(statusCode: 500, requestOptions: RequestOptions(path: ""));
    });
    if (response.statusCode != 200 || isNullOrEmpty(response.data)) {
      if (c.customAvatarPath != null) {
        await File(c.customAvatarPath!).delete(recursive: true);
        c.customAvatarPath = null;
        await c.saveAsync(updateCustomAvatarPath: true);
      }
    } else {
      Logger.debug("Got chat icon for chat ${c.getTitle()}");
      final file = File(c.getIconPath(response.data.length));
      if (!(await file.exists())) {
        await file.create(recursive: true);
      }
      await file.writeAsBytes(response.data);
      c.customAvatarPath = file.path;
      await c.saveAsync(updateCustomAvatarPath: true);
    }
  }

  Map<String, dynamic> toMap() {
    final participants = handles.isEmpty ? this.participants : handles.toList();
    return {
      "ROWID": id,
      "guid": guid,
      "chatIdentifier": chatIdentifier,
      "isArchived": isArchived!,
      "muteType": muteType,
      "muteArgs": muteArgs,
      "isPinned": isPinned!,
      "displayName": displayName,
      "participants": participants.map((item) => item.toMap()).toList(),
      "hasUnreadMessage": hasUnreadMessage!,
      "_customAvatarPath": _customAvatarPath.value,
      "_customBackgroundPath": _customBackgroundPath.value,
      "_pinIndex": _pinIndex.value,
      "autoSendReadReceipts": autoSendReadReceipts,
      "autoSendTypingIndicators": autoSendTypingIndicators,
      "dateDeleted": dateDeleted?.millisecondsSinceEpoch,
      "style": style,
      "lockChatName": lockChatName,
      "lockChatIcon": lockChatIcon,
      "lastReadMessageGuid": lastReadMessageGuid,
      "apnTitle": apnTitle,
      "usingHandle": usingHandle,
      "isRpSms": isRpSms,
      "guidRefs": guidRefs,
      "telephonyId": telephonyId,
      "shareZenMode": shareZenMode,
      "notifsSilenced": notifsSilenced,
      "dateNotifiedAnyways": dateNotifiedAnyways?.millisecondsSinceEpoch,
      "zenModeIsShared": zenModeIsShared,
      "senderIsKnown": senderIsKnown,
      "isRoutingStub": isRoutingStub,
      "transcriptPosterPath": transcriptPosterPath,
      "transcriptBackgroundVersion": transcriptBackgroundVersion,
      "ckRecordId": ckRecordId,
      "ckSyncState": ckSyncState,
      "photoAttachmentGuid": photoAttachmentGuid,
      "cloudGuid": cloudGuid,
      "customThemeLight": customThemeLight,
      "customThemeDark": customThemeDark,
      "textFieldText": textFieldText,
      "textFieldAnnotations": textFieldAnnotations,
      "textFieldAttachments": textFieldAttachments,
      "dbOnlyLatestMessageDate": dbOnlyLatestMessageDate?.millisecondsSinceEpoch,
      "dbLatestMessageId": dbLatestMessage.targetId,
    };
  }
}
