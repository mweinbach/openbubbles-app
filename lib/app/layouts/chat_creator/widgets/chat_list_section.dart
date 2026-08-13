import 'package:bluebubbles/app/layouts/chat_creator/chat_creator.dart';
import 'package:bluebubbles/app/layouts/chat_creator/widgets/chat_creator_tile.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// Extracted widget for the chat/contact list to isolate rebuilds
/// Only rebuilds when filteredChats or filteredContacts changes
class ChatListSection extends StatelessWidget {
  const ChatListSection({
    super.key,
    required this.filteredChats,
    required this.filteredContacts,
    required this.onChatTap,
    required this.onContactTap,
    required this.selectedContacts,
    required this.selectedSuggestionIndex,
    required this.suggestionKeyBuilder,
  });

  final List<Chat> filteredChats;
  final List<ContactV2> filteredContacts;
  final Function(List<SelectedContact>) onChatTap;
  final Function(SelectedContact) onContactTap;
  final List<SelectedContact> selectedContacts;
  final int selectedSuggestionIndex;
  final GlobalKey Function(int index) suggestionKeyBuilder;

  @override
  Widget build(BuildContext context) {
    return CustomScrollView(
      physics: ThemeSwitcher.getScrollPhysics(),
      slivers: <Widget>[
        SliverList(
          delegate: SliverChildBuilderDelegate((context, index) {
            if (filteredChats.isEmpty) {
              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: Text(
                      "Loading existing chats...",
                      style: context.theme.textTheme.labelLarge,
                    ),
                  ),
                  buildProgressIndicator(context, size: 15),
                ],
              );
            }
            final chat = filteredChats[index];

            return Obx(() {
              final chatState = ChatsSvc.getChatState(chat.guid);
              final title = chatState?.title.value ?? chat.getTitle();
              final isSelectedSuggestion = selectedSuggestionIndex == index;
              return Material(
                key: suggestionKeyBuilder(index),
                color: isSelectedSuggestion
                    ? context.theme.colorScheme.outline.withValues(alpha: 0.2)
                    : Colors.transparent,
                child: InkWell(
                  onTap: () {
                    onChatTap(chat.handles
                        .where((e) => selectedContacts.firstWhereOrNull((c) => c.address == e.address) == null)
                        .map((e) => SelectedContact(
                              displayName: e.displayName,
                              address: e.address,
                              serviceType: chat.service,
                            ))
                        .toList());
                  },
                  child: ChatCreatorTile(
                    key: ValueKey(chat.guid),
                    title: title,
                    subtitle: chatState?.chatCreatorSubtitle.value ?? chat.getChatCreatorSubtitle(),
                    chat: chat,
                  ),
                ),
              );
            });
          },
              childCount:
                  filteredChats.length.clamp(ChatsSvc.loadedAllChats.isCompleted ? 0 : 1, double.infinity).toInt()),
        ),
        SliverList(
          delegate: SliverChildBuilderDelegate(
            (context, index) {
              final contact = filteredContacts[index];
              // Dedup by phone number / email address, preserving labels
              final seenNumbers = <String>{};
              final uniquePhones = contact.phoneNumbers.where((p) => seenNumbers.add(p.number.numericOnly())).toList();
              final seenAddresses = <String>{};
              final uniqueEmails = contact.emailAddresses.where((e) => seenAddresses.add(e.address.trim())).toList();

              return Obx(() {
                final hideInfo = SettingsSvc.settings.redactedMode.value && SettingsSvc.settings.hideContactInfo.value;
                int suggestionOffset = filteredChats.length;
                for (int i = 0; i < index; i++) {
                  final prevSeenNumbers = <String>{};
                  final prevUniquePhones = filteredContacts[i]
                      .phoneNumbers
                      .where((p) => prevSeenNumbers.add(p.number.numericOnly()))
                      .toList();
                  final prevSeenAddresses = <String>{};
                  final prevUniqueEmails =
                      filteredContacts[i].emailAddresses.where((e) => prevSeenAddresses.add(e.address.trim())).toList();
                  suggestionOffset += prevUniquePhones.length + prevUniqueEmails.length;
                }
                return Column(
                  key: ValueKey(contact.nativeContactId),
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ...uniquePhones.asMap().entries.map((entry) => Material(
                          key: suggestionKeyBuilder(suggestionOffset + entry.key),
                          color: selectedSuggestionIndex == suggestionOffset + entry.key
                              ? context.theme.colorScheme.outline.withValues(alpha: 0.2)
                              : Colors.transparent,
                          child: InkWell(
                            onTap: () {
                              if (selectedContacts.firstWhereOrNull((c) => c.address == entry.value.number) != null)
                                return;
                              onContactTap(SelectedContact(
                                  displayName: contact.computedDisplayName, address: entry.value.number));
                            },
                            child: ChatCreatorTile(
                              title: hideInfo ? "Contact" : contact.computedDisplayName,
                              subtitle: hideInfo ? "" : entry.value.number,
                              label: hideInfo ? null : entry.value.label,
                              contact: contact,
                              format: true,
                            ),
                          ),
                        )),
                    ...uniqueEmails.asMap().entries.map((entry) => Material(
                          key: suggestionKeyBuilder(suggestionOffset + uniquePhones.length + entry.key),
                          color: selectedSuggestionIndex == suggestionOffset + uniquePhones.length + entry.key
                              ? context.theme.colorScheme.outline.withValues(alpha: 0.2)
                              : Colors.transparent,
                          child: InkWell(
                            onTap: () {
                              if (selectedContacts.firstWhereOrNull((c) => c.address == entry.value.address) != null)
                                return;
                              onContactTap(SelectedContact(
                                  displayName: contact.computedDisplayName, address: entry.value.address));
                            },
                            child: ChatCreatorTile(
                              title: hideInfo ? "Contact" : contact.computedDisplayName,
                              subtitle: hideInfo ? "" : entry.value.address,
                              label: hideInfo ? null : entry.value.label,
                              contact: contact,
                            ),
                          ),
                        )),
                  ],
                );
              });
            },
            childCount: filteredContacts.length,
          ),
        ),
      ],
    );
  }
}
