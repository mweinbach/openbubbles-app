import 'dart:async';
import 'dart:math';

import 'package:bluebubbles/app/components/custom_text_editing_controllers.dart';
import 'package:bluebubbles/app/layouts/chat_creator/chat_creator_dialogs.dart';
import 'package:bluebubbles/app/layouts/chat_creator/chat_creator_utils.dart';
import 'package:bluebubbles/app/layouts/chat_creator/widgets/chat_list_section.dart';
import 'package:bluebubbles/app/layouts/chat_creator/widgets/message_type_toggle.dart';
import 'package:bluebubbles/app/layouts/chat_creator/widgets/selected_contact_chip.dart';
import 'package:bluebubbles/app/layouts/conversation_view/pages/conversation_view.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/text_field_component.dart';
import 'package:bluebubbles/app/state/chat_state_scope.dart';
import 'package:bluebubbles/app/wrappers/bb_app_bar.dart';
import 'package:bluebubbles/app/wrappers/bb_scaffold.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/app/wrappers/titlebar_wrapper.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/conversation_view/pages/messages_view.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/services/backend/interfaces/sync_interface.dart';
import 'package:bluebubbles/services/ui/chat/send_data.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:bluebubbles/utils/string_utils.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:dio/dio.dart' show Response;
import 'package:get/get.dart' hide Response;
import 'package:slugify/slugify.dart';
import 'package:bluebubbles/models/models.dart' show ContactSearchResult;

class SelectedContact {
  final String displayName;
  final String address;
  late final Rxn<ChatServiceType> serviceType;

  SelectedContact({required this.displayName, required this.address, ChatServiceType? serviceType}) {
    this.serviceType = Rxn(serviceType);
  }
}

@Deprecated("Use NewChatCreator instead")
class ChatCreator extends StatefulWidget {
  const ChatCreator({
    super.key,
    this.initialText = "",
    this.initialAttachments = const [],
    this.initialSelected = const [],
  });

  final String? initialText;
  final List<PlatformFile> initialAttachments;
  final List<SelectedContact> initialSelected;

  @override
  ChatCreatorState createState() => ChatCreatorState();
}

class ChatCreatorState extends State<ChatCreator> with ThemeHelpers {
  final TextEditingController addressController = TextEditingController();
  final messageNode = FocusNode();
  late final MentionTextEditingController textController =
      MentionTextEditingController(text: widget.initialText, focusNode: messageNode);
  final FocusNode addressNode = FocusNode();
  final ScrollController addressScrollController = ScrollController();

  List<ContactV2> contacts = [];
  final filteredContacts = <ContactV2>[].obs;
  List<Chat> existingChats = [];
  final filteredChats = <Chat>[].obs;
  late final RxList<SelectedContact> selectedContacts = List<SelectedContact>.from(widget.initialSelected).obs;
  final Rxn<ConversationViewController> fakeController = Rxn(null);
  final Rx<ChatServiceType> selectedService = ChatServiceType.iMessage.obs;
  String? oldText;
  ConversationViewController? oldController;
  Timer? _debounce;
  Completer<void>? createCompleter;
  MessagesService? messagesService;
  int selectedSuggestionIndex = -1;
  int previousSuggestionIndex = -1;
  final Map<int, GlobalKey> suggestionKeys = {};

  bool canCreateGroupChats = BackendSvc.canCreateGroupChats();

  @override
  void initState() {
    super.initState();

    messageNode.addListener(() {
      if (messageNode.hasFocus && addressController.text.trim().isNotEmpty) {
        unawaited(addressOnSubmitted());
      }
    });

    addressController.addListener(() {
      _debounce?.cancel();
      _debounce = Timer(const Duration(milliseconds: 250), () async {
        final tuple = await SchedulerBinding.instance.scheduleTask(() async {
          // If you type and then delete everything, show selected chat view
          if (addressController.text.isEmpty && selectedContacts.isNotEmpty) {
            await findExistingChat();
            return ContactSearchResult(contacts, existingChats);
          }

          if (addressController.text != oldText) {
            oldText = addressController.text;
            // if user has typed stuff, remove the message view and show filtered results
            if (addressController.text.isNotEmpty && fakeController.value != null) {
              ChatsSvc.setAllInactive();
              oldController = fakeController.value;
              fakeController.value = null;
            }
          }
          final query = addressController.text.toLowerCase();
          final _contacts = ChatCreatorUtils.filterContacts(contacts, query);
          final _chats = ChatCreatorUtils.filterChats(existingChats, query, selectedService.value);
          return ContactSearchResult(_contacts, _chats);
        }, Priority.animation);
        _debounce = null;
        filteredContacts.value = tuple.contacts;
        filteredChats.value = List<Chat>.from(tuple.chats);
        if (addressController.text.isNotEmpty) {
          filteredChats.sort((a, b) => a.handles.length.compareTo(b.handles.length));
        }
        final totalSuggestions = _totalSuggestions;
        if (addressController.text.isEmpty || totalSuggestions == 0) {
          selectedSuggestionIndex = -1;
        } else if (selectedSuggestionIndex < 0 || selectedSuggestionIndex >= totalSuggestions) {
          selectedSuggestionIndex = 0;
        }
      });
    });

    // Load contacts and chats asynchronously
    () async {
      if (widget.initialAttachments.isEmpty) {
        contacts = await ContactsSvcV2.getAllContacts();
        if (mounted) {
          filteredContacts.value = contacts;
        }
      }
      if (ChatsSvc.loadedAllChats.isCompleted) {
        existingChats = ChatsSvc.allChats;
        filteredChats.value = existingChats.where((e) => e.isIMessage).toList();
      } else {
        ChatsSvc.loadedAllChats.future.then((_) {
          existingChats = ChatsSvc.allChats;
          filteredChats.value = existingChats.where((e) => e.isIMessage).toList();
        });
      }
      if (widget.initialSelected.isNotEmpty) {
        findExistingChat();
      }
    }();

    if (widget.initialSelected.isNotEmpty) messageNode.requestFocus();
  }

  Future<void> addSelected(SelectedContact c) async {
    selectedContacts.add(c);
    try {
      final available = await BackendSvc.handleiMessageState(c.address);
      c.serviceType.value = available ? ChatServiceType.iMessage : ChatServiceType.sms;
      if (!available && !SettingsSvc.settings.nonIMessageWarning.value && mounted) {
        await showDialog(
          context: context,
          barrierDismissible: false,
          builder: (BuildContext context) {
            return AlertDialog(
              title: Text(
                "This recipient is GREEN because they do not have iMessage or you are being rate-limited.",
                style: context.theme.textTheme.titleLarge,
              ),
              content: Text(
                "${SettingsSvc.settings.smsForwardingTargets.isEmpty ? 'You can start a text here, and sending it will open your default messaging app. ' : ''}Rate limits can start at 0 users for brand new accounts. The only way to resolve a rate limit is patience, trying to reconfigure or re-install to 'fix' the rate limit will result in being temporarily blocked from iMessage.",
                style: context.theme.textTheme.bodyLarge,
              ),
              backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
              actions: <Widget>[
                TextButton(
                  child: Text(
                    "Ok",
                    style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary),
                  ),
                  onPressed: () async {
                    Navigator.of(context).pop();
                    SettingsSvc.settings.nonIMessageWarning.value = true;
                    await SettingsSvc.settings.saveOneAsync('nonIMessageWarning');
                  },
                ),
              ],
            );
          },
        );
      }
    } catch (e, s) {
      Logger.warn("Failed to check iMessage availability for contact", error: e, trace: s, tag: 'ChatCreator');
    }
    addressController.text = "";
    await findExistingChat();
  }

  void addSelectedList(Iterable<SelectedContact> c) {
    selectedContacts.addAll(c);
    addressController.text = "";
    unawaited(findExistingChat());
  }

  void removeSelected(SelectedContact c) {
    selectedContacts.remove(c);
    unawaited(findExistingChat());
  }

  GlobalKey _suggestionKey(int index) => suggestionKeys.putIfAbsent(index, () => GlobalKey());

  void _scrollSelectedSuggestionIntoView() {
    if (selectedSuggestionIndex < 0) return;
    final movingUp = previousSuggestionIndex >= 0 && selectedSuggestionIndex < previousSuggestionIndex;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final context = _suggestionKey(selectedSuggestionIndex).currentContext;
      if (context == null) return;
      Scrollable.ensureVisible(
        context,
        duration: const Duration(milliseconds: 150),
        alignment: movingUp ? 0 : 1,
        alignmentPolicy: ScrollPositionAlignmentPolicy.explicit,
      );
    });
  }

  int get _totalSuggestions {
    int contactSuggestions = 0;
    for (final contact in filteredContacts) {
      final seenNumbers = <String>{};
      final uniquePhones = contact.phoneNumbers.where((p) => seenNumbers.add(p.number.numericOnly())).toList();
      final seenAddresses = <String>{};
      final uniqueEmails = contact.emailAddresses.where((e) => seenAddresses.add(e.address.trim())).toList();
      contactSuggestions += uniquePhones.length + uniqueEmails.length;
    }
    return filteredChats.length + contactSuggestions;
  }

  Future<bool> _selectSuggestionAt(int index) async {
    if (index < 0) return false;
    if (index < filteredChats.length) {
      addSelectedList(filteredChats[index]
          .handles
          .where((e) => selectedContacts.firstWhereOrNull((c) => c.address == e.address) == null)
          .map((e) => SelectedContact(
                displayName: e.displayName,
                address: e.address,
                serviceType: filteredChats[index].service,
              )));
      return true;
    }

    int contactIndex = index - filteredChats.length;
    for (final contact in filteredContacts) {
      final seenNumbers = <String>{};
      final uniquePhones = contact.phoneNumbers.where((p) => seenNumbers.add(p.number.numericOnly())).toList();
      if (contactIndex < uniquePhones.length) {
        final address = uniquePhones[contactIndex].number;
        if (selectedContacts.firstWhereOrNull((c) => c.address == address) != null) return true;
        await addSelected(SelectedContact(displayName: contact.computedDisplayName, address: address));
        return true;
      }
      contactIndex -= uniquePhones.length;

      final seenAddresses = <String>{};
      final uniqueEmails = contact.emailAddresses.where((e) => seenAddresses.add(e.address.trim())).toList();
      if (contactIndex < uniqueEmails.length) {
        final address = uniqueEmails[contactIndex].address;
        if (selectedContacts.firstWhereOrNull((c) => c.address == address) != null) return true;
        await addSelected(SelectedContact(displayName: contact.computedDisplayName, address: address));
        return true;
      }
      contactIndex -= uniqueEmails.length;
    }

    return false;
  }

  Future<Chat?> findExistingChat({bool checkDeleted = false, bool update = true}) async {
    // no selected items, remove message view
    if (selectedContacts.isEmpty) {
      ChatsSvc.setAllInactive();
      fakeController.value = null;
      return null;
    }
    if (selectedContacts.firstWhereOrNull((element) => element.serviceType.value == ChatServiceType.sms) != null) {
      selectedService.value = ChatServiceType.sms;
      textController.supportsFormatting = false;
      filteredChats.value = List<Chat>.from(existingChats.where((e) => !e.isIMessage));
    } else {
      selectedService.value = ChatServiceType.iMessage;
      textController.supportsFormatting = true;
      filteredChats.value = List<Chat>.from(existingChats.where((e) => e.isIMessage));
    }
    Chat? existingChat;
    // try and find the chat simply by identifier
    if (selectedContacts.length == 1) {
      final address = selectedContacts.first.address;
      try {
        if (kIsWeb) {
          existingChat = await Chat.findOneWeb(chatIdentifier: slugify(address, delimiter: ''));
        } else {
          final query = Database.chats.query(Chat_.chatIdentifier.equals(slugify(address, delimiter: ''))).build();
          final result = query.find();
          existingChat = result.firstWhereOrNull(
            (element) => !(element.isRoutingStub) && element.isIMessage == selectedService.value.isIMessageService,
          );
          query.close();
        }
      } catch (e, s) {
        Logger.warn("Failed to find existing chat by identifier", error: e, trace: s, tag: 'ChatCreator');
      }
    }
    // match each selected contact to a participant in a chat
    if (existingChat == null) {
      for (Chat c in (checkDeleted ? Database.chats.getAll() : filteredChats)) {
        if (ChatCreatorUtils.chatMatchesSelectedContacts(c, selectedContacts.map((e) => e.address).toList())) {
          existingChat = c;
          break;
        }
      }
    }
    // if match, show message view, otherwise hide it
    if (update) {
      if (existingChat != null) {
        ChatsSvc.setActiveChat(existingChat, clearNotifications: false);
        ChatsSvc.activeChat!.controller = cvc(existingChat);

        // Get or create the MessagesService for this chat
        // Only create a new one if we don't already have one for this chat
        // DON'T initialize it here - let MessagesView initialize it with proper handlers
        if (messagesService == null || messagesService!.tag != existingChat.guid) {
          messagesService = maybeFindMessagesSvc(existingChat.guid) ?? MessagesService(existingChat.guid);
        }

        if (widget.initialAttachments.isNotEmpty) {
          ChatsSvc.activeChat!.controller!.pickedAttachments.value = widget.initialAttachments;
        } else if (fakeController.value != null && fakeController.value!.pickedAttachments.isNotEmpty) {
          ChatsSvc.activeChat!.controller!.pickedAttachments.value = fakeController.value!.pickedAttachments;
        }

        if (widget.initialText != null && widget.initialText!.isNotEmpty) {
          ChatsSvc.activeChat!.controller!.textController.text = widget.initialText!;
        } else if (fakeController.value?.textController.text != null &&
            fakeController.value!.textController.text.isNotEmpty) {
          ChatsSvc.activeChat!.controller!.textController.text = fakeController.value!.textController.text;
        } else if (textController.text.isNotEmpty) {
          ChatsSvc.activeChat!.controller!.textController.text = textController.text;
        }

        fakeController.value = ChatsSvc.activeChat!.controller;
      } else {
        ChatsSvc.setAllInactive();
        fakeController.value = null;
        messagesService = null;
      }
    }
    if (checkDeleted && existingChat?.dateDeleted != null) {
      ChatsSvc.unDeleteChat(existingChat!);
      // ignore: argument_type_not_assignable, return_of_invalid_type, invalid_assignment, for_in_of_invalid_element_type
      await ChatsSvc.addChat(existingChat);
    }
    return existingChat;
  }

  Future<void> addressOnSubmitted() async {
    if (selectedSuggestionIndex >= 0 && await _selectSuggestionAt(selectedSuggestionIndex)) {
      return;
    }
    final text = addressController.text;
    if (text.isEmail || text.isPhoneNumber) {
      await addSelected(SelectedContact(
        displayName: text,
        address: text,
      ));
    } else if (filteredContacts.length == 1) {
      final possibleAddresses = [
        ...filteredContacts.first.phoneNumbers.map((p) => p.number),
        ...filteredContacts.first.emailAddresses.map((e) => e.address),
      ];
      if (possibleAddresses.length == 1) {
        await addSelected(SelectedContact(
          displayName: filteredContacts.first.computedDisplayName,
          address: possibleAddresses.first,
        ));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return BBScaffold(
      appBar: BBAppBar(
        titleText: "New Conversation",
        leading: buildBackButton(context),
        backgroundColor: Colors.transparent,
        toolbarHeight: kIsDesktop ? 90 : 50,
        actions: [
          if (!canCreateGroupChats)
            IconButton(
              icon: Icon(iOS ? CupertinoIcons.exclamationmark_circle : Icons.error_outline,
                  color: context.theme.colorScheme.error),
              onPressed: () {
                ChatCreatorDialogs.showGroupChatCreationDialog(Get.context!);
              },
            ),
        ],
      ),
      body: FocusScope(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 15.0, vertical: 5.0),
              child: Row(
                children: [
                  Text(
                    "To: ",
                    style: context.theme.textTheme.bodyMedium!.copyWith(color: context.theme.colorScheme.outline),
                  ),
                  Expanded(
                    child: SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      physics: ThemeSwitcher.getScrollPhysics(),
                      controller: addressScrollController,
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          AnimatedSize(
                            duration: const Duration(milliseconds: 250),
                            curve: Curves.easeIn,
                            alignment: Alignment.centerLeft,
                            child: ConstrainedBox(
                              constraints:
                                  BoxConstraints(maxHeight: context.theme.textTheme.bodyMedium!.fontSize! + 20),
                              child: Obx(() => ListView.builder(
                                    itemCount: selectedContacts.length,
                                    shrinkWrap: true,
                                    scrollDirection: Axis.horizontal,
                                    physics: const NeverScrollableScrollPhysics(),
                                    findChildIndexCallback: (key) =>
                                        findChildIndexByKey(selectedContacts, key, (item) => item.address),
                                    itemBuilder: (context, index) {
                                      final e = selectedContacts[index];
                                      return SelectedContactChip(
                                        key: ValueKey(e.address),
                                        contact: e,
                                        onRemove: () => removeSelected(e),
                                      );
                                    },
                                  )),
                            ),
                          ),
                          ConstrainedBox(
                            constraints: BoxConstraints(maxWidth: NavigationSvc.width(context) - 50),
                            child: Focus(
                              onKeyEvent: (node, event) {
                                if (event is KeyDownEvent) {
                                  if (event.logicalKey == LogicalKeyboardKey.backspace &&
                                      (addressController.selection.start == 0 || addressController.text.isEmpty)) {
                                    if (selectedContacts.isNotEmpty) {
                                      removeSelected(selectedContacts.last);
                                    }
                                    return KeyEventResult.handled;
                                  } else if (!HardwareKeyboard.instance.isShiftPressed &&
                                      event.logicalKey == LogicalKeyboardKey.tab) {
                                    messageNode.requestFocus();
                                    return KeyEventResult.handled;
                                  } else if (event.logicalKey == LogicalKeyboardKey.arrowDown &&
                                      _totalSuggestions > 0) {
                                    if (selectedSuggestionIndex >= _totalSuggestions - 1) {
                                      messageNode.requestFocus();
                                      return KeyEventResult.handled;
                                    }
                                    setState(() {
                                      previousSuggestionIndex = selectedSuggestionIndex;
                                      selectedSuggestionIndex = min(
                                        selectedSuggestionIndex < 0 ? 0 : selectedSuggestionIndex + 1,
                                        _totalSuggestions - 1,
                                      );
                                    });
                                    _scrollSelectedSuggestionIntoView();
                                    return KeyEventResult.handled;
                                  } else if (event.logicalKey == LogicalKeyboardKey.arrowUp && _totalSuggestions > 0) {
                                    setState(() {
                                      previousSuggestionIndex = selectedSuggestionIndex;
                                      selectedSuggestionIndex = max(selectedSuggestionIndex - 1, 0);
                                    });
                                    _scrollSelectedSuggestionIntoView();
                                    return KeyEventResult.handled;
                                  } else if ((event.logicalKey == LogicalKeyboardKey.enter ||
                                          event.logicalKey == LogicalKeyboardKey.select) &&
                                      !HardwareKeyboard.instance.isShiftPressed) {
                                    unawaited(addressOnSubmitted());
                                    return KeyEventResult.handled;
                                  }
                                }
                                return KeyEventResult.ignored;
                              },
                              child: TextField(
                                textCapitalization: TextCapitalization.sentences,
                                focusNode: addressNode,
                                autocorrect: false,
                                controller: addressController,
                                style: context.theme.textTheme.bodyMedium,
                                maxLines: 1,
                                selectionControls: iOS ? cupertinoTextSelectionControls : materialTextSelectionControls,
                                autofocus: widget.initialAttachments.isEmpty &&
                                    (widget.initialText?.isEmpty ?? true) &&
                                    widget.initialSelected.isEmpty,
                                enableIMEPersonalizedLearning: !SettingsSvc.settings.incognitoKeyboard.value,
                                textInputAction: TextInputAction.done,
                                cursorColor: context.theme.colorScheme.primary,
                                cursorHeight: context.theme.textTheme.bodyMedium!.fontSize! * 1.25,
                                decoration: InputDecoration(
                                  border: InputBorder.none,
                                  fillColor: Colors.transparent,
                                  hintText: "Enter a name, number, or email...",
                                  hintStyle: context.theme.textTheme.bodyMedium!
                                      .copyWith(color: context.theme.colorScheme.outline),
                                ),
                                onSubmitted: (String value) => unawaited(addressOnSubmitted()),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            if (BackendSvc.supportsSmsForwarding())
              Obx(() => MessageTypeToggle(
                    selectedService: selectedService.value,
                    onToggle: (index) async {
                      selectedContacts.clear();
                      addressController.text = "";
                      if (index == 0) {
                        selectedService.value = ChatServiceType.iMessage;
                        textController.supportsFormatting = true;
                        filteredChats.value = List<Chat>.from(existingChats.where((e) => e.isIMessage));
                      } else {
                        selectedService.value = ChatServiceType.sms;
                        textController.supportsFormatting = false;
                        filteredChats.value = List<Chat>.from(existingChats.where((e) => !e.isIMessage));
                      }
                      ChatsSvc.setAllInactive();
                      fakeController.value = null;
                    },
                  )),
            Expanded(
              child: Obx(() => Theme(
                    data: context.theme.copyWith(
                      // in case some components still use legacy theming
                      primaryColor: context.theme.colorScheme.bubble(context, selectedService.value.isIMessageService),
                      colorScheme: context.theme.colorScheme.copyWith(
                        primary: context.theme.colorScheme.bubble(context, selectedService.value.isIMessageService),
                        onPrimary: context.theme.colorScheme.onBubble(context, selectedService.value.isIMessageService),
                        surface: ThemeSvc.isMaterialYouActive(context)
                            ? null
                            : (context.theme.extensions[BubbleColors] as BubbleColors?)?.receivedBubbleColor,
                        onSurface: ThemeSvc.isMaterialYouActive(context)
                            ? null
                            : (context.theme.extensions[BubbleColors] as BubbleColors?)?.onReceivedBubbleColor,
                      ),
                    ),
                    child: Obx(() {
                      // Access the lists to ensure Obx tracks changes
                      final chats = filteredChats.toList();
                      final contacts = filteredContacts.toList();
                      return AnimatedSwitcher(
                        duration: const Duration(milliseconds: 150),
                        child: fakeController.value == null
                            ? ChatListSection(
                                filteredChats: chats,
                                filteredContacts: contacts,
                                selectedContacts: selectedContacts,
                                onChatTap: addSelectedList,
                                onContactTap: addSelected,
                                selectedSuggestionIndex: selectedSuggestionIndex,
                                suggestionKeyBuilder: _suggestionKey,
                              )
                            : ChatStateScope(
                                chatState: ChatsSvc.getOrCreateChatState(fakeController.value!.chat),
                                child: Container(
                                  color: Colors.transparent,
                                  child: MessagesView(
                                    customService: messagesService,
                                    controller: fakeController.value!,
                                  ),
                                ),
                              ),
                      );
                    }),
                  )),
            ),
            Padding(
              padding: EdgeInsets.only(
                left: 5.0,
                top: 10.0,
                bottom: 5.0 + MediaQuery.of(context).viewPadding.bottom,
              ),
              child: Obx(
                () => Theme(
                    data: context.theme.copyWith(
                      // in case some components still use legacy theming
                      primaryColor: context.theme.colorScheme.bubble(context, selectedService.value.isIMessageService),
                      colorScheme: context.theme.colorScheme.copyWith(
                        primary: context.theme.colorScheme.bubble(context, selectedService.value.isIMessageService),
                        onPrimary: context.theme.colorScheme.onBubble(context, selectedService.value.isIMessageService),
                        surface: ThemeSvc.isMaterialYouActive(context)
                            ? null
                            : (context.theme.extensions[BubbleColors] as BubbleColors?)?.receivedBubbleColor,
                        onSurface: ThemeSvc.isMaterialYouActive(context)
                            ? null
                            : (context.theme.extensions[BubbleColors] as BubbleColors?)?.onReceivedBubbleColor,
                      ),
                    ),
                    child: Focus(
                      onKeyEvent: (node, event) {
                        if (event is KeyDownEvent &&
                            HardwareKeyboard.instance.isShiftPressed &&
                            event.logicalKey == LogicalKeyboardKey.tab) {
                          addressNode.requestFocus();
                          return KeyEventResult.handled;
                        }
                        return KeyEventResult.ignored;
                      },
                      child: Obx(() => TextFieldComponent(
                          focusNode: messageNode,
                          textController: textController,
                          controller: fakeController.value,
                          recorderController: null,
                          initialAttachments: widget.initialAttachments,
                          hideMediaPicker: fakeController.value == null,
                          sendMessage: ({String? effect}) async {
                            if (selectedContacts.isEmpty) {
                              showSnackbar("Error!", "Choose a contact before sending this message!");
                              return;
                            }
                            if (addressController.text.trim().isNotEmpty) {
                              showDialog(
                                context: context,
                                barrierDismissible: false,
                                builder: (BuildContext context) {
                                  return AlertDialog(
                                    backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                    title: Text(
                                      "Querying available services...",
                                      style: context.theme.textTheme.titleLarge,
                                    ),
                                    content: SizedBox(
                                      height: 70,
                                      child: Center(
                                        child: CircularProgressIndicator(
                                          backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                          valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                                        ),
                                      ),
                                    ),
                                  );
                                },
                              );
                              await addressOnSubmitted();
                              Get.back(closeOverlays: true);
                            }
                            Chat? chat =
                                fakeController.value?.chat ?? await findExistingChat(checkDeleted: true, update: false);

                            // If no local chat and we have a single contact, try fetching from the
                            // server using the guessed GUID pattern before falling back to creation.
                            if (chat == null && selectedContacts.length == 1) {
                              final address = selectedContacts.first.address;
                              final service = selectedService.value.method;
                              chat = await ChatsSvc.fetchChat('$service;-;$address');
                            }

                            bool existsOnServer = true;
                            if (chat != null && BackendSvc.getRemoteService() != null) {
                              try {
                                await BackendSvc.getRemoteService()!.chat.fetchOne(chat.guid);
                              } catch (_) {
                                existsOnServer = false;
                              }
                            }

                            if (selectedService.value == ChatServiceType.sms &&
                                SettingsSvc.settings.smsForwardingTargets.keys.isEmpty) {
                              if (kIsDesktop) {
                                showSnackbar("Someone's not on iMessage!", "Enable SMS forwarding on another device!");
                                return;
                              }

                              if (!SettingsSvc.settings.warnedTextChats.value) {
                                await showDialog(
                                  context: context,
                                  barrierDismissible: false,
                                  builder: (BuildContext context) {
                                    return AlertDialog(
                                      title: Text(
                                        "Text chats open in Messages",
                                        style: context.theme.textTheme.titleLarge,
                                      ),
                                      content: Text(
                                        "You will be sent to your default messaging app if iMessage isn't supported by all participants.",
                                        style: context.theme.textTheme.bodyLarge,
                                      ),
                                      backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                      actions: <Widget>[
                                        TextButton(
                                          child: Text(
                                            "Ok",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary),
                                          ),
                                          onPressed: () async {
                                            Navigator.of(context).pop();
                                            SettingsSvc.settings.warnedTextChats.value = true;
                                            await SettingsSvc.settings.saveOneAsync('warnedTextChats');
                                          },
                                        ),
                                      ],
                                    );
                                  },
                                );
                              }

                              final participants = selectedContacts
                                  .map((e) => e.address.isEmail ? e.address : cleansePhoneNumber(e.address))
                                  .toList();
                              await MethodChannelSvc.invokeMethod("open-sms-app", {
                                "targets": participants.join(";"),
                                "body": textController.text,
                              });
                              if (mounted) Navigator.of(context).pop();
                              return;
                            }

                            if (chat != null && existsOnServer) {
                              final existingChat = chat;
                              // Ensure fakeController is set up for this chat
                              if (fakeController.value == null) {
                                ChatsSvc.setActiveChat(existingChat, clearNotifications: false);
                                ChatsSvc.activeChat!.controller = cvc(existingChat);
                                fakeController.value = ChatsSvc.activeChat!.controller;
                              }
                              if (messagesService == null || messagesService!.tag != existingChat.guid) {
                                messagesService =
                                    maybeFindMessagesSvc(existingChat.guid) ?? MessagesService(existingChat.guid);
                              }

                              final ctrl = fakeController.value!;
                              ctrl.textController.text = textController.text;
                              ctrl.pickedAttachments.value = List<PlatformFile>.from(widget.initialAttachments);
                              ctrl.replyToMessage = null;

                              // Pre-queue the send so _SendAnimationState fires it as soon as
                              // it wires up sendFunc — after the ConversationView frame builds.
                              ctrl.pendingSend = SendData(
                                attachments: widget.initialAttachments,
                                text: ctrl.textController.text,
                                annotations: ctrl.buildAttributedBody(),
                                subject: "",
                                replyGuid: ctrl.replyToMessage?.message.threadOriginatorGuid ??
                                    ctrl.replyToMessage?.message.guid,
                                replyPart: ctrl.replyToMessage?.partIndex,
                                effectId: effect,
                              );

                              if (BackendSvc is RustPushBackend &&
                                  widget.initialAttachments.isEmpty &&
                                  (widget.initialText?.isEmpty ?? true)) {
                                final pushBackend = BackendSvc as RustPushBackend;
                                final handle = selectedService.value == ChatServiceType.iMessage
                                    ? await pushBackend.getDefaultHandle()
                                    : await pushBackend.getDefaultSMSHandle();
                                existingChat.usingHandle = handle;
                                await existingChat.saveAsync(updateUsingHandle: true);
                              }

                              NavigationSvc.pushAndRemoveUntil(
                                Get.context!,
                                ConversationView(
                                  chat: existingChat,
                                  customService: messagesService,
                                  fromChatCreator: true,
                                ),
                                (route) => route.isFirst,
                                // don't force close the active chat in tablet mode
                                closeActiveChat: false,
                                // only used in non-tablet mode context
                                customRoute: PageRouteBuilder(
                                  pageBuilder: (_, __, ___) => TitleBarWrapper(
                                      child: ConversationView(
                                    chat: existingChat,
                                    customService: messagesService,
                                    fromChatCreator: true,
                                  )),
                                  transitionDuration: Duration.zero,
                                ),
                              );
                            } else {
                              if (!(createCompleter?.isCompleted ?? true)) return;

                              // Attachments cannot be sent when creating a brand-new chat because
                              // the server's createChat API only accepts a text body. Show an error
                              // and let the user pick an existing contact instead.
                              if (widget.initialAttachments.isNotEmpty) {
                                ChatCreatorDialogs.showCannotForwardAttachmentDialog(context);
                                return;
                              }

                              // hard delete a chat that exists on BB but not on the server to make way for the proper server data
                              if (chat != null) {
                                ChatsSvc.removeChat(chat);
                                ChatsSvc.deleteChat(chat);
                              }
                              createCompleter = Completer();
                              final participants = selectedContacts
                                  .map((e) => e.address.isEmail ? e.address : cleansePhoneNumber(e.address))
                                  .toList();
                              final method = selectedService.value.method;
                              BuildContext? createDialogCtx;
                              showDialog(
                                  context: context,
                                  barrierDismissible: false,
                                  builder: (BuildContext dialogContext) {
                                    createDialogCtx = dialogContext;
                                    return ChatCreatorDialogs.buildCreatingChatDialog(dialogContext, method);
                                  });
                              BackendSvc.createChat(
                                participants.map((i) => RustPushBBUtils.formatAddress(i)).toList(),
                                textController.getFinalAnnotations(),
                                method,
                              ).then((newChat) async {
                                newChat.senderIsKnown = true;
                                newChat = await newChat.saveAsync(updateSenderIsKnown: true);

                                // Fetch the newly saved chat data from the DB
                                // Throw an error if it wasn't saved correctly.
                                final saved = await ChatsSvc.fetchChat(newChat.guid);
                                if (saved == null) {
                                  return showSnackbar("Error", "Failed to save chat!");
                                }

                                // Update the chat in the chat list.
                                // If it wasn't existing, add it.
                                newChat = saved;
                                bool updated = ChatsSvc.updateChat(newChat);
                                if (!updated) {
                                  await ChatsSvc.addChat(newChat);
                                }

                                // Fetch the last message for the chat and save it.
                                final messageRes =
                                    await BackendSvc.getRemoteService()?.chat.getMessages(newChat.guid, limit: 1);
                                if (messageRes != null && messageRes.data["data"].length > 0) {
                                  final rawMessages =
                                      (messageRes.data["data"] as List<dynamic>).cast<Map<String, dynamic>>();
                                  await SyncInterface.bulkSyncData(
                                    chatData: newChat.toMap(),
                                    messagesData: rawMessages,
                                  );
                                }

                                // Force close the message service for the chat so it can be reloaded.
                                // If this isn't done, new messages will not show.
                                maybeFindMessagesSvc(newChat.guid)?.close(force: true);
                                cvc(newChat).close();

                                // Let awaiters know we completed
                                createCompleter?.complete();

                                if (createDialogCtx != null) Navigator.of(createDialogCtx!).pop();
                                if (!mounted) return;
                                NavigationSvc.pushAndRemoveUntil(
                                  Get.context!,
                                  ConversationView(chat: newChat),
                                  (route) => route.isFirst,
                                  customRoute: PageRouteBuilder(
                                    pageBuilder: (_, __, ___) => TitleBarWrapper(
                                      child: ConversationView(
                                        chat: newChat,
                                        fromChatCreator: true,
                                      ),
                                    ),
                                    transitionDuration: Duration.zero,
                                  ),
                                );
                              }).catchError((error) {
                                if (createDialogCtx != null) Navigator.of(createDialogCtx!).pop();
                                if (!mounted) {
                                  if (!createCompleter!.isCompleted) createCompleter?.completeError(error);
                                  return;
                                }
                                showBBDialog(
                                  barrierDismissible: false,
                                  context: context,
                                  title: 'Failed to create chat!',
                                  body: error is Response
                                      ? 'Reason: (${error.data["error"]["type"]}) -> ${error.data["error"]["message"]}'
                                      : error.toString(),
                                  actions: [
                                    BBDialogAction(
                                      text: 'OK',
                                      isDefault: true,
                                      onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
                                    ),
                                  ],
                                );
                                if (!createCompleter!.isCompleted) {
                                  createCompleter?.completeError(error);
                                }
                              });
                            }
                          })),
                    )),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
