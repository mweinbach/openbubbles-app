import 'dart:async';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:audio_waveforms/audio_waveforms.dart';
import 'package:bluebubbles/app/components/custom_text_editing_controllers.dart';
import 'package:bluebubbles/app/layouts/settings/pages/profile/posterkit.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/backend/interfaces/prefs_interface.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter_keyboard_visibility/flutter_keyboard_visibility.dart';
import 'package:get/get.dart';
import 'package:google_ml_kit/google_ml_kit.dart' hide Message;
import 'package:metadata_fetch/metadata_fetch.dart';
import 'package:scroll_to_index/scroll_to_index.dart';
import 'package:bluebubbles/services/ui/chat/send_data.dart';
import 'package:bluebubbles/models/models.dart' show MessageReplyContext;
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:universal_io/io.dart';
import 'package:unicode_emojis/unicode_emojis.dart';

class MessageEditEntry {
  final Message message;
  final MessagePart part;
  final MentionTextEditingController controller;
  const MessageEditEntry({required this.message, required this.part, required this.controller});
}

ConversationViewController cvc(Chat chat, {String? tag}) =>
    Get.isRegistered<ConversationViewController>(tag: tag ?? chat.guid)
        ? Get.find<ConversationViewController>(tag: tag ?? chat.guid)
        : Get.put(ConversationViewController(chat, tag_: tag), tag: tag ?? chat.guid);

class ConversationViewController extends StatefulController with GetSingleTickerProviderStateMixin {
  final Chat chat;
  late final String tag;
  bool fromChatCreator = false;
  bool fromSearchResult = false;
  bool addedRecentPhotoReply = false;
  final AutoScrollController scrollController = AutoScrollController();

  ConversationViewController(this.chat, {String? tag_}) {
    tag = tag_ ?? chat.guid;
    recipientNotifsSilenced.value = chat.notifsSilenced;
    reportJunkAvailable.value = !(chat.senderIsKnown ?? true);
  }

  // caching items
  final Map<String, Map<String, (Uint8List, StickerData?)>> stickerData = {};
  final Map<String, Metadata> legacyUrlPreviews = {};
  final Map<String, VideoController> videoPlayers = {};
  final Map<String, PlayerController> audioPlayers = {};
  final Map<String, Player> audioPlayersDesktop = {};
  final Map<String, List<EntityAnnotation>> mlKitParsedText = {};

  // message view items
  final RxBool showTypingIndicator = false.obs;
  final RxList<Handle> showTypingIndicatorFor = <Handle>[].obs;
  final RxBool showScrollDown = false.obs;
  final RxDouble timestampOffset = 0.0.obs;
  final RxBool inSelectMode = false.obs;
  final RxList<Message> selected = <Message>[].obs;
  final RxList<MessageEditEntry> editing = <MessageEditEntry>[].obs;
  final GlobalKey focusInfoKey = GlobalKey();
  final GlobalKey typingInfoKey = GlobalKey();
  final RxBool recipientNotifsSilenced = false.obs;
  final RxBool reportJunkAvailable = false.obs;
  final RxBool showSmartReplyRow = false.obs;
  final RxDouble smartReplyRowHeight = 0.0.obs;
  bool showingOverlays = false;
  final Map<String, (StreamSubscription<dynamic>, Uint8List?)> typingIndicatorData = {};

  /// True while any route is pushed on top of the conversation view route (e.g.
  /// ConversationDetails). Used by onAppResume to skip keyboard auto-focus on mobile.
  bool showingSubRoute = false;
  bool _subjectWasLastFocused = false; // If this is false, then message field was last focused (default)

  FocusNode get lastFocusedNode => _subjectWasLastFocused ? subjectFocusNode : focusNode;
  SpellCheckTextEditingController get lastFocusedTextController =>
      _subjectWasLastFocused ? subjectTextController : textController;

  // text field items
  final RxBool showAttachmentPicker = false.obs;
  RxBool showEmojiPicker = false.obs;
  final GlobalKey textFieldKey = GlobalKey();
  final RxList<PlatformFile> pickedAttachments = <PlatformFile>[].obs;
  final focusNode = FocusNode();
  final subjectFocusNode = FocusNode();
  final headerBackFocusNode = FocusNode();
  final attachmentPickerFocusNode = FocusNode();
  final recordButtonFocusNode = FocusNode();

  /// When set, the next app-resume will NOT pull focus back to the text field. Used by the
  /// voice-memo flow so focus can return to the record button after the mic permission prompt.
  /// Consumed (reset) on the next resume by [AppLifecycleService.open].
  bool suppressResumeRefocus = false;
  FocusNode? bottomMessageFocusNode;
  // Per-message callbacks (keyed by message GUID) that open the long-press options modal,
  // registered by each MessagePopupHolder. Lets a focused message open its modal via a
  // held D-pad/Enter key.
  final Map<String, VoidCallback> messagePopupOpeners = {};
  // Per-message (keyed by GUID) "do what a tap does" action for attachments: download the
  // photo if not downloaded, or open it if downloaded. Lets a focused message trigger it
  // with a D-pad/Enter press. Registered by the attachment widgets based on current state.
  final Map<String, VoidCallback> attachmentTapActions = {};
  // Opens the error dialog for a failed message; keyed by message GUID. Registered by
  // ErrorIndicatorObserver so a focused errored message can open its error via D-pad/Enter.
  final Map<String, void Function(BuildContext)> errorOpeners = {};
  // Parent focus node of the staged-attachment delete buttons; lets the text field jump up
  // to the first delete button via traversalDescendants.
  final FocusNode pickedAttachmentsListNode = FocusNode(canRequestFocus: false, skipTraversal: true);
  late final textController = MentionTextEditingController(focusNode: focusNode, supportsFormatting: chat.isIMessage);
  late final subjectTextController = SpellCheckTextEditingController(focusNode: subjectFocusNode);
  final Rx<(PlatformFile?, PayloadData)?> pickedApp = Rx<(PlatformFile?, PayloadData)?>(null);
  final RxBool showRecording = false.obs;
  final RxList<Emoji> emojiMatches = <Emoji>[].obs;
  final RxInt emojiSelectedIndex = 0.obs;
  final RxList<Mentionable> mentionMatches = <Mentionable>[].obs;
  final RxInt mentionSelectedIndex = 0.obs;
  final ScrollController emojiScrollController = ScrollController();
  final Rxn<DateTime> scheduledDate = Rxn<DateTime>(null);
  final Rxn<MessageReplyContext> _replyToMessage = Rxn<MessageReplyContext>(null);
  MessageReplyContext? get replyToMessage => _replyToMessage.value;
  set replyToMessage(MessageReplyContext? m) {
    _replyToMessage.value = m;
    if (m != null) {
      lastFocusedNode.requestFocus();
    }
  }

  late final mentionables = chat.handles
      .map((e) => Mentionable(
            handle: e,
          ))
      .toList();

  final Rxn<ContactV2> suggestedContact = Rxn<ContactV2>(null);
  final RxBool suggestShare = false.obs;
  bool keyboardOpen = false;
  double _keyboardOffset = 0;
  Timer? _scrollDownDebounce;
  Timer? _debounceTyping;
  Future<void> Function(SendData)? sendFunc;
  final Rxn<api.SimplifiedTranscriptPoster> backgroundPoster = Rxn<api.SimplifiedTranscriptPoster>(null);
  Map<String, ui.Image> images = {};
  StreamSubscription<int>? shareSubscription;

  /// When set, [_SendAnimationState] will auto-fire this send as soon as it
  /// registers [sendFunc] (i.e. immediately after the widget is built).
  /// Used by ChatCreator to pre-queue a send before navigating to ConversationView.
  SendData? pendingSend;

  /// Completer that resolves once [MessagesView] has finished setting up its
  /// handlers AND its list key (both sync and async loadChunk paths).
  ///
  /// [SendAnimation] waits on this before firing a [pendingSend] so that
  /// [handleNewMessage] → [_listKey.currentState?.insertItem] is guaranteed
  /// to find a mounted [SliverAnimatedList], preventing the silent no-op race.
  Completer<void> _messagesViewReady = Completer<void>();

  /// Called by [MessagesView] once its handlers and list key are fully set up.
  void markMessagesViewReady() {
    if (!_messagesViewReady.isCompleted) {
      _messagesViewReady.complete();
    }
  }

  /// Called by [MessagesView.dispose] so that the next visit starts fresh.
  void resetMessagesViewReady() {
    if (_messagesViewReady.isCompleted) {
      _messagesViewReady = Completer<void>();
    }
  }

  /// Future that resolves once [MessagesView] has fully initialized.
  Future<void> get messagesViewReady => _messagesViewReady.future;

  @override
  void onInit() {
    super.onInit();

    shareSubscription = SettingsSvc.settings.shareVersion.listen((_) => updateContactInfo());
    updateContactInfo();

    textController.mentionables = mentionables;
    KeyboardVisibilityController().onChange.listen((bool visible) async {
      keyboardOpen = visible;
      if (scrollController.hasClients && scrollController.positions.length == 1) {
        _keyboardOffset = scrollController.offset;
      }
    });

    scrollController.addListener(() {
      if (!scrollController.hasClients || scrollController.positions.length != 1) return;
      if (keyboardOpen &&
          SettingsSvc.settings.hideKeyboardOnScroll.value &&
          scrollController.offset > _keyboardOffset + 100) {
        focusNode.unfocus();
        subjectFocusNode.unfocus();
      }

      if (showScrollDown.value && scrollController.offset >= 500) return;
      if (!showScrollDown.value && scrollController.offset < 500) return;

      if (scrollController.offset >= 500 && !showScrollDown.value) {
        showScrollDown.value = true;
        if (_scrollDownDebounce?.isActive ?? false) _scrollDownDebounce?.cancel();
        _scrollDownDebounce = Timer(const Duration(seconds: 3), () {
          showScrollDown.value = false;
        });
      } else if (showScrollDown.value) {
        showScrollDown.value = false;
      }
    });

    focusNode.addListener(() {
      if (focusNode.hasFocus) {
        _subjectWasLastFocused = false;
      }
    });

    subjectFocusNode.addListener(() {
      if (subjectFocusNode.hasFocus) {
        _subjectWasLastFocused = true;
      }
    });
    updatePoster();
  }

  void clearTypingState() {
    _debounceTyping = null;
  }

  void triggerTypingIndicator() {
    if (!SettingsSvc.settings.enablePrivateAPI.value ||
        !(chat.autoSendTypingIndicators ?? SettingsSvc.settings.privateSendTypingIndicators.value)) {
      return;
    }

    _debounceTyping?.cancel();
    if (_debounceTyping == null) {
      final app = pickedApp.value?.$2.appData?.firstOrNull;
      BackendSvc.startedTyping(chat, app?.appId != null ? app : null);
    }
    _debounceTyping = Timer(const Duration(seconds: 5), () {
      BackendSvc.stoppedTyping(chat);
      _debounceTyping = null;
    });
  }

  void updateContactInfo() {
    if (chat.handles.length != 1) return;

    var handle = Handle.findOne(id: chat.handles.first.id!)!;

    final sharedContact = handle.contactsV2.firstWhereOrNull((h) => h.isSharedSuggestion);

    if (sharedContact != null && !SettingsSvc.settings.isDumb.value) {
      suggestedContact.value = sharedContact;
    }

    suggestShare.value = (sharedContact != null || !SettingsSvc.settings.shareContactAutomatically.value) &&
        !SettingsSvc.settings.sharedContacts.contains(chat.handles.first.address) &&
        !SettingsSvc.settings.dismissedContacts.contains(chat.handles.first.address) &&
        SettingsSvc.settings.nameAndPhotoSharing.value &&
        chat.isIMessage && !SettingsSvc.settings.isDumb.value;
  }

  void updatePoster() async {
    if (chat.transcriptPosterPath == null) {
      backgroundPoster.value = null;
      return;
    }
    var data = await File("${chat.transcriptPosterPath}.jpg").readAsBytes();
    var poster = await api.fromTranscriptPosterSave(poster: data);
    images = await loadPosterImages(chat.transcriptPosterPath!, poster.poster);
    backgroundPoster.value = poster;
  }

  @override
  void onClose() {
    updateSmartReplyLayout(visible: false, height: 0);
    for (PlayerController a in audioPlayers.values) {
      a.pausePlayer();
      a.dispose();
    }
    for (Player a in audioPlayersDesktop.values) {
      a.dispose();
    }
    for (VideoController a in videoPlayers.values) {
      a.player.pause();
      a.player.dispose();
    }
    scrollController.dispose();
    headerBackFocusNode.dispose();
    attachmentPickerFocusNode.dispose();
    recordButtonFocusNode.dispose();
    pickedAttachmentsListNode.dispose();
    shareSubscription?.cancel();
    _debounceTyping?.cancel();
    super.onClose();
  }

  Future<void> scrollToBottom() async {
    if (scrollController.positions.isNotEmpty && scrollController.positions.first.extentBefore > 0) {
      await scrollController.animateTo(
        0.0,
        curve: Curves.easeOut,
        duration: const Duration(milliseconds: 300),
      );
    }

    if (SettingsSvc.settings.openKeyboardOnSTB.value) {
      focusNode.requestFocus();
    }
  }

  Future<void> scrollToTime(DateTime time) async {
    final messages = MessagesSvc(chat.guid).struct.messages;
    messages.sort(Message.sort);
    if (scrollController.positions.isNotEmpty) {
      final index = messages.indexWhere((element) {
        final comparableDate = element.dateScheduled ??
            (element.dateDelivered == null
                ? element.dateCreated
                : (element.dateCreated!.isBefore(element.dateDelivered!)
                    ? element.dateCreated
                    : element.dateDelivered));
        return comparableDate?.isBefore(time) ?? false;
      });
      await scrollController.scrollToIndex(index, preferPosition: AutoScrollPosition.begin);
    }

    if (SettingsSvc.settings.openKeyboardOnSTB.value) {
      focusNode.requestFocus();
    }
  }

  Future<void> send(SendData data) async {
    await sendFunc?.call(data);
  }

  AttributedBody buildAttributedBody() {
    return textController.getFinalAnnotations();
  }

  bool isSelected(String guid) {
    return selected.firstWhereOrNull((e) => e.guid == guid) != null;
  }

  bool isEditing(String guid, int part) {
    return editing.firstWhereOrNull((e) => e.message.guid == guid && e.part.part == part) != null;
  }

  void updateSmartReplyLayout({required bool visible, required double height}) {
    if (showSmartReplyRow.value != visible) {
      showSmartReplyRow.value = visible;
    }

    final nextHeight = visible ? height : 0.0;
    if (smartReplyRowHeight.value != nextHeight) {
      smartReplyRowHeight.value = nextHeight;
    }
  }

  void close() {
    updateSmartReplyLayout(visible: false, height: 0);
    EventDispatcherSvc.emit("update-highlight", null);
    ChatsSvc.setAllInactive();
    Get.delete<ConversationViewController>(tag: tag);
  }

  Future<void> saveReplyToMessageState() async {
    await PrefsInterface.saveReplyToMessageState(
      chat.guid,
      replyToMessage?.message.guid,
      replyToMessage?.partIndex,
    );
  }

  Future<void> loadReplyToMessageState() async {
    final data = await PrefsInterface.loadReplyToMessageState(chat.guid);
    if (data != null) {
      final messageGuid = data['messageGuid'] as String;
      final messagePart = data['messagePart'] as int;
      final message = Message.findOne(guid: messageGuid);
      if (message != null) {
        replyToMessage = MessageReplyContext(message, messagePart);
      }
    }
  }
}
