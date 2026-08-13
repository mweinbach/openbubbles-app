import 'package:bluebubbles/app/components/avatars/contact_avatar_group_widget.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// A simplified chat tile used in reorderable lists (e.g. pinned chat order).
///
/// Shows only the avatar and chat title — no subtitle, date, or trailing arrow.
/// The drag handle on the trailing side is wrapped in [ReorderableDragStartListener]
/// so only that area initiates a drag.
class DraggableConversationTile extends StatelessWidget {
  final Chat chat;
  final int index;

  const DraggableConversationTile({
    super.key,
    required this.chat,
    required this.index,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 8),
          child: ContactAvatarGroupWidget(
            chat: chat,
            size: 45,
            editable: false,
          ),
        ),
        Expanded(
          child: Obx(() {
            final chatState = ChatsSvc.getChatState(chat.guid);
            final title = chatState?.title.value ?? chat.getTitle();
            return RichText(
              text: TextSpan(
                children: MessageHelper.buildEmojiText(
                  title,
                  context.theme.textTheme.bodyLarge!,
                ),
              ),
              overflow: TextOverflow.ellipsis,
            );
          }),
        ),
        MouseRegion(
          cursor: SystemMouseCursors.grab,
          child: ReorderableDragStartListener(
            index: index,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Icon(
                Icons.drag_handle,
                color: context.theme.colorScheme.outline,
              ),
            ),
          ),
        ),
      ],
    );
  }
}
