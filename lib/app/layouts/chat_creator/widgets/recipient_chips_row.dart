import 'package:bluebubbles/app/layouts/chat_creator/chat_creator_controller.dart';
import 'package:bluebubbles/app/layouts/chat_creator/widgets/selected_contact_chip.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

/// Displays the "To:" label, the selected contact chips, and the address
/// text input in a horizontal, scrollable row.
class RecipientChipsRow extends StatefulWidget {
  const RecipientChipsRow({super.key, required this.controller});

  final ChatCreatorController controller;

  @override
  State<RecipientChipsRow> createState() => _RecipientChipsRowState();
}

class _RecipientChipsRowState extends State<RecipientChipsRow> {
  @override
  void initState() {
    super.initState();
  }

  @override
  void dispose() {
    super.dispose();
  }

  ChatCreatorController get controller => widget.controller;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 15.0, vertical: 5.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Text(
            'To: ',
            style: context.theme.textTheme.bodyMedium!.copyWith(color: context.theme.colorScheme.outline),
          ),
          Expanded(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              physics: ThemeSwitcher.getScrollPhysics(),
              child: Obx(() => Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      AnimatedSize(
                        duration: const Duration(milliseconds: 250),
                        curve: Curves.easeIn,
                        alignment: Alignment.centerLeft,
                        child: ConstrainedBox(
                          constraints: BoxConstraints(
                            maxHeight: context.theme.textTheme.bodyMedium!.fontSize! + 20,
                          ),
                          child: ListView.builder(
                            itemCount: controller.selectedContacts.length,
                            shrinkWrap: true,
                            scrollDirection: Axis.horizontal,
                            physics: const NeverScrollableScrollPhysics(),
                            findChildIndexCallback: (key) => findChildIndexByKey(
                              controller.selectedContacts,
                              key,
                              (item) => item.address,
                            ),
                            itemBuilder: (context, index) {
                              final e = controller.selectedContacts[index];
                              return SelectedContactChip(
                                key: ValueKey(e.address),
                                contact: e,
                                onRemove: () => controller.removeSelected(e),
                              );
                            },
                          ),
                        ),
                      ),
                      ConstrainedBox(
                        constraints: BoxConstraints(
                          maxWidth: NavigationSvc.width(context) - 50,
                        ),
                        child: Focus(
                          onKeyEvent: (node, event) {
                            if (event is KeyDownEvent) {
                              if (event.logicalKey == LogicalKeyboardKey.backspace &&
                                  (controller.addressController.selection.start == 0 ||
                                      controller.addressController.text.isEmpty)) {
                                if (controller.selectedContacts.isNotEmpty) {
                                  controller.removeSelected(controller.selectedContacts.last);
                                }
                                return KeyEventResult.handled;
                              } else if (!HardwareKeyboard.instance.isShiftPressed &&
                                  event.logicalKey == LogicalKeyboardKey.tab) {
                                controller.messageNode.requestFocus();
                                return KeyEventResult.handled;
                              } else if (event.logicalKey == LogicalKeyboardKey.arrowDown) {
                                // Single-line field doesn't need the down arrow for the
                                // caret, so use it to move into the suggestion list.
                                FocusScope.of(context).focusInDirection(TraversalDirection.down);
                                return KeyEventResult.handled;
                              }
                            }
                            return KeyEventResult.ignored;
                          },
                          child: CupertinoTextField(
                            focusNode: widget.controller.addressNode,
                            controller: widget.controller.addressController,
                            autocorrect: false,
                            maxLines: 1,
                            keyboardType: TextInputType.text,
                            textInputAction: TextInputAction.done,
                            placeholder: 'Name or number',
                            placeholderStyle: context.theme.textTheme.bodyMedium!.copyWith(
                              color: context.theme.colorScheme.outline,
                            ),
                            style: context.theme.textTheme.bodyMedium,
                            decoration: null,
                            enableIMEPersonalizedLearning: !SettingsSvc.settings.incognitoKeyboard.value,
                            onSubmitted: (_) => widget.controller.addressOnSubmitted(),
                          ),
                        ),
                      ),
                    ],
                  )),
            ),
          ),
        ],
      ),
    );
  }
}
