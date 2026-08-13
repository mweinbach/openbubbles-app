import 'package:bluebubbles/app/layouts/settings/widgets/tiles/contact_upload_progress.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart' hide Response;
import 'package:in_app_review/in_app_review.dart';
import 'package:url_launcher/url_launcher.dart';

class SettingsItemsActions {
  static const String donateHost = "bluebubbles.app";
  static const String donatePath = "donate";
  static const String discordHost = "discord.gg";
  static const String discordPath = "hbx7EhNFjp";

  static Future<void> exportContacts({
    required BuildContext context,
    required RxnDouble progress,
    required RxnInt totalSize,
    required RxBool uploadingContacts,
  }) async {
    BuildContext? dialogCtx;

    void closeDialog() {
      Get.closeAllSnackbars();
      if (dialogCtx != null) {
        Navigator.of(dialogCtx!).pop();
      }
      Future.delayed(const Duration(milliseconds: 400), () {
        progress.value = null;
        totalSize.value = null;
      });
    }

    showDialog(
      context: context,
      builder: (dialogContext) {
        dialogCtx = dialogContext;
        return ContactUploadProgress(
          progress: progress,
          totalSize: totalSize,
          uploadingContacts: uploadingContacts,
          onClose: closeDialog,
        );
      },
    );

    final contacts = <Map<String, dynamic>>[];
    final allContacts = await ContactsSvcV2.getAllContacts();
    for (final contact in allContacts) {
      contacts.add(contact.toMap());
    }

    HttpSvc.contact.create(
      contacts,
      onSendProgress: (count, total) {
        uploadingContacts.value = true;
        progress.value = count / total;
        totalSize.value = total;
        if (progress.value == 1.0) {
          uploadingContacts.value = false;
          showSnackbar("Notice", "Successfully exported contacts to server");
        }
      },
    ).catchError((err, stack) {
      if (err is Response) {
        Logger.error(err.data["error"]["message"].toString(), error: err, trace: stack);
      } else {
        Logger.error("Failed to create contact!", error: err, trace: stack);
      }

      closeDialog();
      showSnackbar("Error", "Failed to export contacts to server");
      return Response(requestOptions: RequestOptions(path: ''));
    });
  }

  static Future<void> openStoreReview() async {
    final inAppReview = InAppReview.instance;
    await inAppReview.openStoreListing(microsoftStoreId: '9P3XF8KJ0LSM');
  }

  static Future<void> openDonationPage() async {
    await launchUrl(
      Uri(scheme: "https", host: donateHost, path: donatePath),
      mode: LaunchMode.externalApplication,
    );
  }

  static Future<void> openDiscord() async {
    await launchUrl(
      Uri(scheme: "https", host: discordHost, path: discordPath),
      mode: LaunchMode.externalApplication,
    );
  }
}
