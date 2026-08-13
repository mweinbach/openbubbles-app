import 'package:bluebubbles/app/state/attachment_state_scope.dart';
import 'package:bluebubbles/app/state/message_state_scope.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// Attachment not yet loaded (waiting to download or redacted).
/// When not redacted the Obx reacts to error-state changes only.
class NotLoadedContent extends StatelessWidget {
  const NotLoadedContent({
    super.key,
    required this.hideAttachments,
    required this.isiOS,
  });

  final bool hideAttachments;
  final bool isiOS;

  @override
  Widget build(BuildContext context) {
    final attachmentState = AttachmentStateScope.of(context);
    final attachment = attachmentState.attachment;

    if (hideAttachments) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: 5),
          Text(
            attachment.mimeType ?? "",
            style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.onSurfaceVariant),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 10),
          Text(
            attachment.getFriendlySize(),
            style: context.theme.textTheme.bodyMedium!.copyWith(color: context.theme.colorScheme.onSurfaceVariant),
            maxLines: 1,
          ),
        ],
      );
    }

    return Obx(() {
      final hasError = MessageStateScope.of(context).hasError.value;
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            height: 40,
            width: 40,
            child: Center(
              child: Icon(
                hasError
                    ? (isiOS ? CupertinoIcons.exclamationmark_circle : Icons.error_outline)
                    : (isiOS ? CupertinoIcons.cloud_download : Icons.cloud_download_outlined),
                size: 30,
              ),
            ),
          ),
          const SizedBox(height: 5),
          Text(
            hasError ? "Send Failed!" : (attachmentState.mimeType.value ?? ""),
            style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.onSurfaceVariant),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 10),
          Text(
            attachment.getFriendlySize(),
            style: context.theme.textTheme.bodyMedium!.copyWith(color: context.theme.colorScheme.onSurfaceVariant),
            maxLines: 1,
          ),
        ],
      );
    });
  }
}
