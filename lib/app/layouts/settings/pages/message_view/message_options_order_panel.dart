import 'package:bluebubbles/app/wrappers/bb_app_bar.dart';
import 'package:bluebubbles/app/wrappers/bb_scaffold.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/popup/details_menu_action.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class MessageOptionsOrderPanel extends StatefulWidget {
  const MessageOptionsOrderPanel({super.key});

  @override
  State<StatefulWidget> createState() => _MessageOptionsOrderPanelState();
}

class _MessageOptionsOrderPanelState extends State<MessageOptionsOrderPanel> with ThemeHelpers {
  final RxList<DetailsMenuAction> actionList = RxList();

  @override
  void initState() {
    super.initState();

    actionList.value = SettingsSvc.settings.detailsMenuActions.platformSupportedActions;
  }

  @override
  Widget build(BuildContext context) {
    return BBScaffold(
      backgroundColor: material ? tileColor : headerColor,
      extendBodyBehindAppBar: false,
      appBar: BBAppBar(
        titleText: "Message Options Order",
        leading: buildBackButton(context),
        actions: [
          TextButton(
            child: Text("Reset",
                style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
            onPressed: () {
              actionList.value = DetailsMenuAction.values.platformSupportedActions;
              SettingsSvc.settings.resetDetailsMenuActions();
            },
          ),
        ],
      ),
      body: ColoredBox(
        color: material ? headerColor : tileColor,
        child: Obx(
          () => ReorderableListView.builder(
            padding: const EdgeInsets.only(left: 15, right: 15, bottom: 15),
            shrinkWrap: true,
            header: Padding(
              padding: const EdgeInsets.symmetric(vertical: 13),
              child: Text(
                "Drag the handle on the right side of each option to reorder how they appear in the message context menu.",
                style: context.theme.textTheme.bodyMedium!.copyWith(color: context.theme.colorScheme.outline),
              ),
            ),
            onReorder: (start, end) {
              if (start == end) return;
              actionList.insert(end, actionList.elementAt(start));
              actionList.removeAt(start + (end < start ? 1 : 0));
              SettingsSvc.settings.setDetailsMenuActions(actionList.toList());
            },
            buildDefaultDragHandles: false,
            itemBuilder: (context, index) {
              DetailsMenuAction action = actionList[index];
              return Column(
                key: Key(action.toString()),
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: AbsorbPointer(
                          child: DetailsMenuActionWidget(
                            action: action,
                          ),
                        ),
                      ),
                      MouseRegion(
                        cursor: MouseCursor.defer,
                        child: ReorderableDragStartListener(
                          index: index,
                          child: Icon(
                            Icons.drag_handle,
                            color: context.theme.colorScheme.outline,
                          ),
                        ),
                      ),
                      const SizedBox(width: 16),
                    ],
                  ),
                  if (index < actionList.length - 1)
                    Divider(
                      height: 1,
                      thickness: 1,
                      color: context.theme.colorScheme.outline.withValues(alpha: 0.15),
                    ),
                ],
              );
            },
            itemCount: actionList.length,
          ),
        ),
      ),
    );
  }
}
