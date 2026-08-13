import 'package:bluebubbles/app/layouts/chat_creator/chat_creator.dart' show SelectedContact;
import 'package:bluebubbles/app/layouts/chat_creator/chat_creator_controller.dart';
import 'package:bluebubbles/app/layouts/chat_creator/widgets/chat_creator_tile.dart';
import 'package:bluebubbles/app/layouts/chat_creator/widgets/search_contact_tile.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

/// The scrollable list of search results shown when no chat is resolved.
///
/// Section 0 — "Send to [address]": shown when the typed query is a valid
/// phone number or email that does not match any known contact. Lets the user
/// send to arbitrary addresses.
///
/// Section 1 — "Conversations": existing chats matching the current search/
/// service filter. Tapping a row selects all its participants.
///
/// Section 2 — "Contacts": contacts from the address book. Tapping a row
/// selects that specific phone/email address.
///
/// When chats and contacts are both empty (and data is loaded) an in-line
/// empty state message is shown.
///
/// Wrapped in [Obx] so it rebuilds only when [filteredChats],
/// [filteredContacts], or [currentQuery] changes.
class SearchResultsList extends StatelessWidget {
  const SearchResultsList({super.key, required this.controller});

  final ChatCreatorController controller;

  /// True when [query] is a standalone valid address not covered by any
  /// existing contact result or already-selected chip.
  bool _shouldShowFallback(
    String query,
    List contacts,
    List selected,
  ) {
    if (query.isEmpty) return false;
    if (!query.isEmail && !query.isPhoneNumber) return false;
    // Already selected
    if (selected.any((c) => (c as SelectedContact).address == query)) {
      return false;
    }
    // Already surfaced in a contact row — don't duplicate
    return true;
  }

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      var chats = controller.filteredChats.toList();
      var contacts = controller.filteredContacts.toList();
      // On dumb (D-pad) devices keep the suggestion list short (5 total) so it's quick to
      // navigate; chats are shown first, then contacts fill any remaining slots.
      if (SettingsSvc.settings.isDumb.value) {
        chats = chats.take(5).toList();
        contacts = contacts.take(5 - chats.length).toList();
      }
      final chatsLoaded = ChatsSvc.loadedAllChats.isCompleted;
      final query = controller.currentQuery.value.trim();
      final selected = controller.selectedContacts.toList();
      final showFallback = _shouldShowFallback(query, contacts, selected);
      final isEmpty = chatsLoaded && chats.isEmpty && contacts.isEmpty && !showFallback;

      return Focus(
        focusNode: controller.suggestionsListNode,
        onKeyEvent: (node, event) {
          if (event is! KeyDownEvent) return KeyEventResult.ignored;
          final isUp = event.logicalKey == LogicalKeyboardKey.arrowUp;
          final isDown = event.logicalKey == LogicalKeyboardKey.arrowDown;
          if (!isUp && !isDown) return KeyEventResult.ignored;

          // Each suggestion row contributes more than one focus node, so we can't rely on
          // index/equality. Instead use geometry: only hop out of the list when there is no
          // focusable row above (for up) / below (for down) the currently focused row. When
          // there IS another row in that direction, return ignored and let directional
          // navigation move between rows. The 1px tolerance ignores sibling nodes on the same
          // row (identical vertical center). At the top, up *always* goes to the To: field —
          // it can never fall through to the service toggle.
          final rows = node.traversalDescendants.toList();
          final focusedDy = FocusManager.instance.primaryFocus?.rect.center.dy;

          if (isUp) {
            final hasRowAbove =
                focusedDy != null && rows.any((r) => r.rect.center.dy < focusedDy - 1);
            if (!hasRowAbove) {
              controller.addressNode.requestFocus();
              return KeyEventResult.handled;
            }
            return KeyEventResult.ignored; // let directional nav move to the row above
          }
          // isDown
          final hasRowBelow =
              focusedDy != null && rows.any((r) => r.rect.center.dy > focusedDy + 1);
          if (!hasRowBelow) {
            controller.messageNode.requestFocus();
            return KeyEventResult.handled;
          }
          return KeyEventResult.ignored; // let directional nav move to the row below
        },
        child: CustomScrollView(
        physics: ThemeSwitcher.getScrollPhysics(),
        slivers: [
          // ----------------------------------------------------------------
          // Fallback "Send to [address]" row
          // ----------------------------------------------------------------
          if (showFallback) ...[
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                child: Text(
                  'New Message',
                  style: context.theme.textTheme.labelLarge?.copyWith(
                    color: context.theme.colorScheme.outline,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: () {
                    final address = query.isEmail ? query : controller.normalizeToE164(query);
                    controller.addSelected(
                      SelectedContact(displayName: address, address: address),
                    );
                  },
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: context.theme.colorScheme.primaryContainer,
                      child: Icon(
                        Icons.send,
                        size: 20,
                        color: context.theme.colorScheme.primary,
                      ),
                    ),
                    title: Text(
                      'Send to "${query.isEmail ? query : controller.normalizeToE164(query)}"',
                      style: context.theme.textTheme.bodyMedium,
                    ),
                    subtitle: Text(
                      query.isEmail ? 'Email address' : 'Phone number',
                      style: context.theme.textTheme.bodySmall?.copyWith(
                        color: context.theme.colorScheme.outline,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],

          // ----------------------------------------------------------------
          // Conversations section
          // ----------------------------------------------------------------
          if (chats.isNotEmpty || !chatsLoaded)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                child: Text(
                  'Conversations',
                  style: context.theme.textTheme.labelLarge?.copyWith(
                    color: context.theme.colorScheme.outline,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),

          SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, index) {
                if (!chatsLoaded && chats.isEmpty) {
                  return Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Text(
                          'Loading existing chats...',
                          style: context.theme.textTheme.labelLarge,
                        ),
                      ),
                      buildProgressIndicator(context, size: 15),
                    ],
                  );
                }
                final chat = chats[index];
                return Obx(() {
                  final chatState = ChatsSvc.getChatState(chat.guid);
                  final title = chatState?.title.value ?? chat.getTitle();
                  final subtitle = chatState?.chatCreatorSubtitle.value ?? chat.getChatCreatorSubtitle();
                  return Material(
                    color: Colors.transparent,
                    child: InkWell(
                      onTap: () {
                        final participants = chat.handles
                            .where((h) =>
                                controller.selectedContacts.firstWhereOrNull((c) => c.address == h.address) == null)
                            .map(
                              (h) => SelectedContact(
                                displayName: h.displayName,
                                address: h.address,
                                serviceType: chat.service,
                              ),
                            )
                            .toList();
                        controller.addSelectedFromChat(participants);
                      },
                      child: ChatCreatorTile(
                        key: ValueKey(chat.guid),
                        title: title,
                        subtitle: subtitle,
                        chat: chat,
                      ),
                    ),
                  );
                });
              },
              childCount: (!chatsLoaded && chats.isEmpty ? 1 : chats.length),
            ),
          ),

          // ----------------------------------------------------------------
          // Contacts section
          // ----------------------------------------------------------------
          if (contacts.isNotEmpty)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                child: Text(
                  'Contacts',
                  style: context.theme.textTheme.labelLarge?.copyWith(
                    color: context.theme.colorScheme.outline,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),

          SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, index) => SearchContactTile(
                key: ValueKey(contacts[index].nativeContactId),
                contact: contacts[index],
                controller: controller,
              ),
              childCount: contacts.length,
            ),
          ),

          // ----------------------------------------------------------------
          // Empty state (data loaded, nothing matches the query)
          // ----------------------------------------------------------------
          if (isEmpty && query.isNotEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.search_off_rounded,
                      size: 48,
                      color: context.theme.colorScheme.outline,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'No results for "$query"',
                      style: context.theme.textTheme.bodyMedium?.copyWith(
                        color: context.theme.colorScheme.outline,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            ),
        ],
        ),
      );
    });
  }
}
