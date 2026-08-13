import 'dart:async';

import 'package:bluebubbles/app/layouts/chat_creator/widgets/chat_creator_tile.dart';
import 'package:bluebubbles/app/wrappers/bb_app_bar.dart';
import 'package:bluebubbles/app/wrappers/bb_scaffold.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:get/get.dart' hide Response;
import 'package:slugify/slugify.dart';

class ChatSelectorView extends StatefulWidget {
  const ChatSelectorView({
    super.key,
    required this.onSelect,
  });

  final void Function(Chat) onSelect;

  @override
  ChatSelectorViewState createState() => ChatSelectorViewState();
}

class ChatSelectorViewState extends State<ChatSelectorView> with ThemeHelpers {
  final TextEditingController searchController = TextEditingController();
  final FocusNode searchNode = FocusNode();
  final ScrollController addressScrollController = ScrollController();

  List<Chat> filteredChats = [];
  String? oldSearch;
  Timer? _debounce;

  @override
  void initState() {
    super.initState();

    // Handle searching for a chat
    searchController.addListener(() {
      _debounce?.cancel();
      _debounce = Timer(const Duration(milliseconds: 250), () async {
        final searchChats = await SchedulerBinding.instance.scheduleTask(() async {
          final query = slugify(searchController.text, delimiter: "");
          return ChatsSvc.searchChats(query);
        }, Priority.animation);

        _debounce = null;
        setState(() {
          filteredChats = List<Chat>.from(searchChats);
        });
      });
    });

    if (ChatsSvc.loadedAllChats.isCompleted) {
      if (mounted) {
        setState(() {
          filteredChats = ChatsSvc.allChats;
        });
      }
    } else {
      ChatsSvc.loadedAllChats.future.then((_) {
        if (mounted) {
          setState(() {
            filteredChats = ChatsSvc.allChats;
          });
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return BBScaffold(
      appBar: BBAppBar(
        titleText: "Select a Chat",
        leading: buildBackButton(context),
        backgroundColor: Colors.transparent,
        toolbarHeight: kIsDesktop ? 90 : 50,
      ),
      body: FocusScope(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10),
              child: TextField(
                controller: searchController,
                focusNode: searchNode,
                style: context.theme.textTheme.bodyLarge,
                decoration: InputDecoration(
                    hintText: "Search for a chat...",
                    hintStyle: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.outline),
                    prefixIcon: Icon(
                      Icons.search,
                      color: context.theme.colorScheme.outline,
                    ),
                    suffixIcon: searchController.text.isNotEmpty
                        ? IconButton(
                            icon: const Icon(Icons.clear),
                            onPressed: () {
                              searchController.clear();
                            },
                          )
                        : null,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: BorderSide.none,
                    ),
                    filled: false),
              ),
            ),
            Expanded(
              child: Obx(() {
                return Align(
                    alignment: Alignment.topCenter,
                    child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 150),
                        child: CustomScrollView(
                          shrinkWrap: true,
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
                                          "Loading chats...",
                                          style: context.theme.textTheme.labelLarge,
                                        ),
                                      ),
                                      buildProgressIndicator(context, size: 15),
                                    ],
                                  );
                                }
                                final chat = filteredChats[index];
                                final chatState = ChatsSvc.getChatState(chat.guid);
                                final _title = chatState?.title.value ?? chat.getTitle();
                                return Material(
                                  color: Colors.transparent,
                                  child: InkWell(
                                    onTap: () {
                                      widget.onSelect(chat);
                                      Navigator.of(context).pop();
                                    },
                                    child: ChatCreatorTile(
                                      key: ValueKey(chat.guid),
                                      title: _title,
                                      subtitle: chatState?.chatCreatorSubtitle.value ?? chat.getChatCreatorSubtitle(),
                                      chat: chat,
                                      showTrailing: false,
                                    ),
                                  ),
                                );
                              },
                                  childCount: filteredChats.length
                                      .clamp(ChatsSvc.loadedAllChats.isCompleted ? 0 : 1, double.infinity)
                                      .toInt()),
                            )
                          ],
                        )));
              }),
            ),
          ],
        ),
      ),
    );
  }
}
