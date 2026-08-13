import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:emoji_picker_flutter/emoji_picker_flutter.dart' hide Emoji;
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// Animated emoji picker panel driven by [ConversationViewController.showEmojiPicker].
///
/// Wraps the entire [EmojiPicker] in an [Obx] so only this widget tree rebuilds
/// when the picker visibility toggles.
class TextFieldEmojiPickerSection extends StatelessWidget {
  const TextFieldEmojiPickerSection({
    super.key,
    required this.controller,
    required this.proxyController,
    required this.emojiScrollController,
    required this.emojiPickerHeight,
    required this.emojiColumns,
  });

  final ConversationViewController controller;
  final TextEditingController proxyController;
  final ScrollController emojiScrollController;
  final double emojiPickerHeight;
  final int emojiColumns;

  @override
  Widget build(BuildContext context) {
    final bool iOS = SettingsSvc.settings.skin.value == Skins.iOS;
    return AnimatedSize(
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeIn,
      alignment: Alignment.bottomCenter,
      child: Obx(() {
        return controller.showEmojiPicker.value
            ? Theme(
                data: context.theme.copyWith(canvasColor: Colors.transparent),
                child: EmojiPicker(
                  textEditingController: proxyController,
                  scrollController: emojiScrollController,
                  config: Config(
                    height: emojiPickerHeight,
                    emojiSet: (_) => emojiSetEnglish,
                    checkPlatformCompatibility: true,
                    emojiViewConfig: EmojiViewConfig(
                      emojiSizeMax: 28,
                      backgroundColor: Colors.transparent,
                      columns: emojiColumns,
                      noRecents: Text("No Recents",
                          style: context.textTheme.headlineMedium!.copyWith(color: context.theme.colorScheme.outline)),
                    ),
                    viewOrderConfig: const ViewOrderConfig(
                      top: EmojiPickerItem.categoryBar,
                      middle: EmojiPickerItem.emojiView,
                      bottom: EmojiPickerItem.searchBar,
                    ),
                    skinToneConfig: const SkinToneConfig(enabled: false),
                    categoryViewConfig: const CategoryViewConfig(
                      backgroundColor: Colors.transparent,
                      dividerColor: Colors.transparent,
                    ),
                    bottomActionBarConfig: BottomActionBarConfig(
                      customBottomActionBar: (Config config, EmojiViewState state, VoidCallback showSearchView) {
                        return Container(
                          margin: const EdgeInsets.only(top: 10),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const SizedBox(width: 8),
                              Expanded(
                                child: Material(
                                  child: InkWell(
                                    onTap: showSearchView,
                                    child: Padding(
                                      padding: const EdgeInsets.all(12),
                                      child: Row(children: [
                                        Icon(
                                          iOS ? CupertinoIcons.search : Icons.search,
                                          color: context.theme.colorScheme.outline,
                                        ),
                                        const SizedBox(width: 8),
                                        Expanded(
                                          child: Text(
                                            "Search...",
                                            style: context.theme.textTheme.bodyLarge!.copyWith(
                                              color: context.theme.colorScheme.outline,
                                            ),
                                          ),
                                        ),
                                      ]),
                                    ),
                                  ),
                                ),
                              ),
                              Padding(
                                padding: const EdgeInsets.all(12),
                                child: IconButton(
                                  icon: Icon(
                                    iOS ? CupertinoIcons.xmark : Icons.close,
                                    color: context.theme.colorScheme.outline,
                                  ),
                                  onPressed: () {
                                    controller.showEmojiPicker.value = false;
                                    controller.lastFocusedNode.requestFocus();
                                  },
                                ),
                              ),
                              const SizedBox(width: 8),
                            ],
                          ),
                        );
                      },
                    ),
                    searchViewConfig: SearchViewConfig(
                      backgroundColor: Colors.transparent,
                      buttonIconColor: context.theme.colorScheme.outline,
                    ),
                  ),
                ),
              )
            : const SizedBox.shrink();
      }),
    );
  }
}
