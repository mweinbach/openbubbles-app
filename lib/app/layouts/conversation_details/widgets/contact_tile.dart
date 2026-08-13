import 'package:bluebubbles/app/layouts/conversation_details/dialogs/address_picker.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:dio/dio.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:get/get.dart' hide Response;
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_slidable/flutter_slidable.dart';
import 'package:universal_io/io.dart';

class ContactTile extends StatelessWidget {
  final Handle handle;
  final Chat chat;
  final bool canBeRemoved;
  final bool facetimeSupported;

  ContactV2? get contact => handle.contactsV2.firstOrNull;

  bool get hasPhones {
    return contact?.addresses.any((addr) => !addr.contains('@')) ?? false;
  }

  bool get hasEmails {
    return contact?.addresses.any((addr) => addr.contains('@')) ?? false;
  }

  const ContactTile({
    super.key,
    required this.handle,
    required this.chat,
    required this.canBeRemoved,
    required this.facetimeSupported,
  });

  void _removeParticipant(BuildContext context) {
    final navigator = Navigator.of(context, rootNavigator: true);
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
          title: Text(
            "Removing participant...",
            style: context.theme.textTheme.titleLarge,
          ),
          content: SizedBox(
            height: 70,
            child: Center(child: buildProgressIndicator(context)),
          ),
        );
      },
    );

    BackendSvc.chatParticipant(ParticipantOp.Remove, chat, handle.address).then((response) async {
      navigator.pop();
      Logger.info("Removed participant ${handle.address}");
      showSnackbar("Notice", "Removed participant from chat!");
    }).catchError((err, stack) {
      Logger.error("Failed to remove participant ${handle.address}", error: err, trace: stack);
      late final String error;
      if (err is Response) {
        error = err.data["error"]["message"].toString();
      } else {
        error = err.toString();
      }
      showSnackbar("Error", "Failed to remove participant: $error");
    });
  }

  @override
  Widget build(BuildContext context) {
    final handleState = HandleSvc.getOrCreateHandleState(handle);
    return Obx(() {
      final String displayName = handleState.displayName.value ?? handle.address;
      final String address = handleState.formattedAddress.value ?? handle.address;
      final bool isEmail = handle.address.isEmail;
      final child = InkWell(
        mouseCursor: MouseCursor.defer,
        onLongPress: () {
          Clipboard.setData(ClipboardData(text: handle.address));
          if (!Platform.isAndroid || (FilesystemSvc.androidInfo?.version.sdkInt ?? 0) < 33) {
            showToast("Address copied to clipboard");
          }
        },
        onTap: kIsDesktop
            ? null
            : () async {
                final contactV2 = handle.contactsV2.firstOrNull;
                if (contactV2 == null || !contactV2.isNative) {
                  await MethodChannelSvc.actions.openContactForm(
                    address: handle.address,
                    isEmail: handle.address.isEmail,
                  );
                } else {
                  try {
                    await MethodChannelSvc.actions.viewContactForm(nativeContactId: contactV2.nativeContactId);
                  } catch (_) {
                    showSnackbar("Error", "Failed to find contact on device!");
                  }
                }
              },
        onSecondaryTap: () {
          showModalBottomSheet(
            context: context,
            backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
            shape: const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
            ),
            builder: (ctx) {
              return SafeArea(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
                      child: Row(
                        children: [
                          const Icon(Icons.person_outlined, size: 18),
                          const SizedBox(width: 8),
                          Flexible(
                            child: Text(
                              displayName,
                              style: context.theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const Divider(height: 8),
                    ListTile(
                      mouseCursor: MouseCursor.defer,
                      leading: const Icon(Icons.copy_outlined),
                      title: const Text("Copy address"),
                      onTap: () {
                        Navigator.of(ctx).pop();
                        Clipboard.setData(ClipboardData(text: handle.address));
                        showToast("Address copied to clipboard");
                      },
                    ),
                    if (canBeRemoved)
                      ListTile(
                        mouseCursor: MouseCursor.defer,
                        leading: Icon(Icons.person_remove_outlined, color: context.theme.colorScheme.error),
                        title: Text("Remove from chat", style: TextStyle(color: context.theme.colorScheme.error)),
                        onTap: () {
                          Navigator.of(ctx).pop();
                          _removeParticipant(context);
                        },
                      ),
                    const SizedBox(height: 8),
                  ],
                ),
              );
            },
          );
        },
        child: ListTile(
          title: RichText(
            text: TextSpan(
              children: MessageHelper.buildEmojiText(
                displayName,
                context.theme.textTheme.bodyLarge!,
              ),
            ),
          ),
          subtitle: handle.contactsV2.isEmpty
              ? null
              : Text(
                  address,
                  style: context.theme.textTheme.bodyMedium!.copyWith(color: context.theme.colorScheme.outline),
                ),
          leading: ContactAvatarWidget(
            key: Key("${handle.address}-contact-tile"),
            handle: handle,
            borderThickness: 0.1,
          ),
          trailing: kIsWeb || (kIsDesktop && !isEmail) || (!isEmail && !hasPhones)
              ? Container(width: 2)
              : FittedBox(
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    mainAxisSize: MainAxisSize.max,
                    children: <Widget>[
                      if ((contact == null && isEmail) || hasEmails)
                        ButtonTheme(
                          minWidth: 1,
                          child: TextButton(
                            style: TextButton.styleFrom(
                              shape: const CircleBorder(),
                              backgroundColor: SettingsSvc.settings.skin.value != Skins.iOS
                                  ? null
                                  : context.theme.colorScheme.secondary,
                            ),
                            onLongPress: () =>
                                showAddressPicker(contact, handle, context, isEmail: true, isLongPressed: true),
                            onPressed: () => showAddressPicker(contact, handle, isEmail: true, context),
                            child: Icon(
                                SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.mail : Icons.email,
                                color: SettingsSvc.settings.skin.value != Skins.iOS
                                    ? context.theme.colorScheme.onSurface
                                    : context.theme.colorScheme.onSecondary,
                                size: SettingsSvc.settings.skin.value != Skins.iOS ? 25 : 20),
                          ),
                        ),
                      if (((contact == null && !isEmail) || hasPhones) && !kIsWeb && !kIsDesktop)
                        ButtonTheme(
                          minWidth: 1,
                          child: TextButton(
                            style: TextButton.styleFrom(
                              shape: const CircleBorder(),
                              backgroundColor: SettingsSvc.settings.skin.value != Skins.iOS
                                  ? null
                                  : context.theme.colorScheme.secondary,
                            ),
                            onLongPress: () => showAddressPicker(contact, handle, context, isLongPressed: true),
                            onPressed: () => showAddressPicker(contact, handle, context),
                            child: Icon(
                                SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.phone : Icons.call,
                                color: SettingsSvc.settings.skin.value != Skins.iOS
                                    ? context.theme.colorScheme.onSurface
                                    : context.theme.colorScheme.onSecondary,
                                size: SettingsSvc.settings.skin.value != Skins.iOS ? 25 : 20),
                          ),
                        ),
                      if (!kIsWeb && !kIsDesktop && facetimeSupported)
                        ButtonTheme(
                          minWidth: 1,
                          child: TextButton(
                            style: TextButton.styleFrom(
                              shape: const CircleBorder(),
                              backgroundColor: SettingsSvc.settings.skin.value != Skins.iOS
                                  ? null
                                  : context.theme.colorScheme.secondary,
                            ),
                            onPressed: () async {
                              var h = await chat.ensureHandle();
                              await PushSvc.placeOutgoingCall(h, [RustPushBBUtils.bbHandleToRust(handle)]);
                            },
                            child: Icon(
                                SettingsSvc.settings.skin.value == Skins.iOS
                                    ? CupertinoIcons.video_camera
                                    : Icons.video_call_outlined,
                                color: SettingsSvc.settings.skin.value != Skins.iOS
                                    ? context.theme.colorScheme.onSurface
                                    : context.theme.colorScheme.onSecondary,
                                size: SettingsSvc.settings.skin.value != Skins.iOS ? 25 : 20),
                          ),
                        ),
                    ],
                  ),
                ),
        ),
      );

      return canBeRemoved && !kIsDesktop
          ? Slidable(
              endActionPane: ActionPane(
                motion: const StretchMotion(),
                extentRatio: 0.25,
                children: [
                  SlidableAction(
                    label: 'Remove',
                    backgroundColor: Colors.red,
                    icon: SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.trash : Icons.delete_outlined,
                    onPressed: (_) => _removeParticipant(context),
                  ),
                ],
              ),
              child: child,
            )
          : child;
    });
  }
}
