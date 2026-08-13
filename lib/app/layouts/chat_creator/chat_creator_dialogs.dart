import 'package:bluebubbles/helpers/helpers.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart' hide Response;

class ChatCreatorDialogs {
  static Future<void> showGroupChatCreationDialog(BuildContext context) {
    return showBBDialog(
      barrierDismissible: false,
      context: context,
      title: "Group Chat Creation",
      body:
          "Creating group chats from BlueBubbles is not possible on macOS 11 (Big Sur) and later due to limitations from Apple. You must setup the Private API to gain this feature.",
      actions: [
        BBDialogAction(
          text: "Close",
          onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
        ),
      ],
    );
  }

  static Future<void> showCannotForwardAttachmentDialog(BuildContext context) {
    return showBBDialog(
      context: context,
      title: "Cannot Forward Attachment",
      body: "Attachments cannot be forwarded to a new conversation. Please select an existing contact.",
      actions: [
        BBDialogAction(
          text: "OK",
          isDefault: true,
          onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
        ),
      ],
    );
  }

  static Widget buildCreatingChatDialog(BuildContext context, String method) {
    return AlertDialog(
      backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
      title: Text(
        "Creating a new $method chat...",
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
  }

  static Widget buildCreateChatErrorDialog(BuildContext context, Object error) {
    return AlertDialog(
      backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
      title: Text(
        "Failed to create chat!",
        style: context.theme.textTheme.titleLarge,
      ),
      content: Text(
        error is Response
            ? "Reason: (${error.data["error"]["type"]}) -> ${error.data["error"]["message"]}"
            : error.toString(),
        style: context.theme.textTheme.bodyLarge,
      ),
      actions: [
        TextButton(
          child: Text("OK",
              style: context.theme.textTheme.bodyLarge!.copyWith(color: Get.context!.theme.colorScheme.primary)),
          onPressed: () => Navigator.of(context).pop(),
        )
      ],
    );
  }
}
