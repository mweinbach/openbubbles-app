import 'dart:convert';
import 'dart:ui';

import 'package:bluebubbles/app/layouts/conversation_details/dialogs/address_picker.dart';
import 'package:bluebubbles/app/layouts/conversation_details/dialogs/change_name.dart';
import 'package:bluebubbles/app/layouts/settings/pages/theming/avatar/avatar_crop.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/components/avatars/contact_avatar_group_widget.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:defer_pointer/defer_pointer.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;

class ChatInfo extends StatefulWidget {
  const ChatInfo({super.key, required this.chat, required this.ftSupportedParticipants});

  final Chat chat;
  final List<String> ftSupportedParticipants;

  @override
  State<StatefulWidget> createState() => _ChatInfoState();
}

class _ChatInfoState extends State<ChatInfo> with ThemeHelpers {
  Chat get chat => widget.chat;
  bool get facetimeSupported => widget.ftSupportedParticipants.length == (chat.handles.length + 1);

  Future<bool?> showMethodDialog(String title) async {
    return await showBBDialog<bool>(
      context: context,
      title: title,
      content: SettingsSvc.settings.enablePrivateAPI.value && chat.isIMessage
          ? Text(
              "Local - Changes only apply to this device.\nPrivate API - Changes will apply to everyone's devices.",
              style: context.theme.textTheme.bodyLarge,
            )
          : null,
      actions: [
        BBDialogAction(
          text: "Local",
          onPressed: () => Navigator.of(context, rootNavigator: true).pop(false),
        ),
        BBDialogAction(
          text: "Private API",
          isDefault: true,
          onPressed: () => Navigator.of(context, rootNavigator: true).pop(true),
        ),
      ],
    );
  }

  void updatePhoto() async {
    bool? papi = false;
    if (SettingsSvc.settings.enablePrivateAPI.value && chat.isIMessage && chat.isGroup) {
      papi = await showMethodDialog("Group Icon Update Method");
    }
    if (papi == null) return;
    final usePrivateApi = papi;
    final String? result = await Navigator.of(context).push(
      ThemeSwitcher.buildPageRoute(
        builder: (context) => AvatarCrop(chat: chat),
      ),
    );
    if (result == null) return;

    if (!usePrivateApi) {
      await ChatsSvc.setChatCustomAvatarPath(chat, result);
      return;
    }

    if (usePrivateApi && SettingsSvc.settings.enablePrivateAPI.value && await BackendSvc.canUploadGroupPhotos()) {
      showDialog(
          context: context,
          builder: (BuildContext context) {
            return AlertDialog(
              backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
              title: Text(
                "Updating group photo...",
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
          });
      final response = await BackendSvc.setChatIcon(chat, result);
      if (response) {
        await ChatsSvc.setChatCustomAvatarPath(chat, result);
        Navigator.of(context, rootNavigator: true).pop();
        showSnackbar("Notice", "Updated group photo successfully!");
      } else {
        try {
          await File(result).delete();
        } catch (_) {}
        Navigator.of(context, rootNavigator: true).pop();
        showSnackbar("Error", "Failed to update group photo!");
      }
    } else if (usePrivateApi) {
      try {
        await File(result).delete();
      } catch (_) {}
      showSnackbar("Error", "Failed to update group photo!");
    }
  }

  void deletePhoto() async {
    bool? papi = false;
    if (SettingsSvc.settings.enablePrivateAPI.value && chat.isIMessage && chat.isGroup) {
      papi = await showMethodDialog("Group Icon Deletion Method");
    }
    if (papi == null) return;
    final usePrivateApi = papi;

    if (usePrivateApi && SettingsSvc.settings.enablePrivateAPI.value && await BackendSvc.canUploadGroupPhotos()) {
      final response = await BackendSvc.deleteChatIcon(chat);
      if (response) {
        await ChatsSvc.setChatCustomAvatarPath(chat, null);
        showSnackbar("Notice", "Deleted group photo successfully!");
      } else {
        showSnackbar("Error", "Failed to delete group photo!");
      }
      return;
    }

    await ChatsSvc.setChatCustomAvatarPath(chat, null);
  }

  @override
  Widget build(BuildContext context) {
    final chatState = ChatsSvc.getChatState(chat.guid);

    bool canCall = !kIsWeb &&
        !kIsDesktop &&
        !(chat.chatIdentifier?.startsWith("urn:biz") ?? false) &&
        (chat.handles.isNotEmpty &&
            ((chat.handles.first.contactsV2.firstOrNull?.phoneNumbers.isNotEmpty ?? false) ||
                !chat.handles.first.address.contains("@")));

    return DeferredPointerHandler(
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (chat.isGroup) const SizedBox(height: 10),
        if (iOS && chat.isGroup)
          Center(
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                GestureDetector(
                  onTap: chat.isGroup
                      ? () async {
                          updatePhoto();
                        }
                      : null,
                  child: ContactAvatarGroupWidget(
                    size: 100,
                    editable: !chat.isGroup,
                  ),
                ),
                Obx(() => chat.customAvatarPath != null
                    ? Positioned(
                        right: -5,
                        top: -5,
                        child: DeferPointer(
                          child: InkWell(
                            onTap: () async {
                              deletePhoto();
                            },
                            child: Container(
                              width: 30,
                              height: 30,
                              decoration: BoxDecoration(
                                border: Border.all(color: context.theme.colorScheme.surface, width: 1),
                                shape: BoxShape.circle,
                                color: context.theme.colorScheme.tertiaryContainer,
                              ),
                              child: Icon(
                                Icons.close,
                                color: context.theme.colorScheme.onTertiaryContainer,
                                size: 20,
                              ),
                            ),
                          ),
                        ),
                      )
                    : const SizedBox.shrink()),
              ],
            ),
          ),
        if (iOS && chat.isGroup)
          Padding(
            padding: const EdgeInsets.only(top: 12.0, left: 20.0, right: 20.0),
            child: Center(
              child: Obx(() => RichText(
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    text: TextSpan(
                      style: context.theme.textTheme.headlineMedium!.copyWith(
                        fontWeight: FontWeight.bold,
                        color: context.theme.colorScheme.onSurface,
                      ),
                      children: MessageHelper.buildEmojiText(
                        chatState?.title.value ?? chat.getTitle(),
                        context.theme.textTheme.headlineMedium!.copyWith(
                          fontWeight: FontWeight.bold,
                          color: context.theme.colorScheme.onSurface,
                        ),
                      ),
                    ),
                  )),
            ),
          ),
        if (!chat.isGroup && iOS && chatState != null && chatState.participants.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 4.0, left: 20.0, right: 20.0),
            child: Center(
              child: Obx(() {
                final address = chatState.participants.first.formattedAddress.value;
                if (address == null) return const SizedBox.shrink();
                return Text(
                  address,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  style: context.theme.textTheme.bodyMedium!.copyWith(
                    color: context.theme.colorScheme.outline,
                  ),
                );
              }),
            ),
          ),
        if (chat.isGroup && !iOS)
          Padding(
            padding: const EdgeInsets.only(left: 15.0, bottom: 5.0),
            child: Text("GROUP NAME AND PHOTO",
                style: context.theme.textTheme.bodyMedium!.copyWith(color: context.theme.colorScheme.outline)),
          ),
        if (chat.isGroup && !iOS)
          Padding(
            padding: const EdgeInsets.only(bottom: 5.0),
            child: Material(
              color: Colors.transparent,
              child: ListTile(
                mouseCursor: MouseCursor.defer,
                onTap: () async {
                  bool? papi = false;
                  if (SettingsSvc.settings.enablePrivateAPI.value && chat.isIMessage) {
                    papi = await showMethodDialog("Group Name Update Method");
                  }
                  if (papi == null) return;
                  if (!papi) {
                    showChangeName(chat, "local", context);
                  } else {
                    showChangeName(chat, "private-api", context);
                  }
                },
                title: Obx(() => RichText(
                      text: TextSpan(
                        style: context.theme.textTheme.bodyLarge,
                        children: MessageHelper.buildEmojiText(
                          chatState?.title.value ?? chat.getTitle(),
                          context.theme.textTheme.bodyLarge!,
                        ),
                      ),
                    )),
                trailing: Icon(Icons.edit_outlined, color: context.theme.colorScheme.onSurface),
              ),
            ),
          ),
        if (chat.isGroup && !iOS)
          Padding(
            padding: const EdgeInsets.only(bottom: 5.0),
            child: Material(
              color: Colors.transparent,
              child: ListTile(
                mouseCursor: MouseCursor.defer,
                onTap: () async {
                  updatePhoto();
                },
                title: Text("Update group photo", style: context.theme.textTheme.bodyLarge!),
                trailing: Icon(Icons.edit_outlined, color: context.theme.colorScheme.onSurface),
              ),
            ),
          ),
        if (chat.isGroup && !iOS)
          Obx(() => chat.customAvatarPath != null
              ? Padding(
                  padding: const EdgeInsets.only(bottom: 5.0),
                  child: Material(
                    color: Colors.transparent,
                    child: ListTile(
                      mouseCursor: MouseCursor.defer,
                      onTap: () async {
                        deletePhoto();
                      },
                      title: Text("Remove group photo",
                          style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.error)),
                      trailing: Icon(Icons.close, color: context.theme.colorScheme.error),
                    ),
                  ),
                )
              : const SizedBox.shrink()),
        if (chat.isGroup && iOS)
          Center(
            child: TextButton(
              child: Text(
                "${(chat.displayName?.isNotEmpty ?? false) ? "Change" : "Add"} Name",
                style: context.theme.textTheme.bodyMedium!.apply(color: context.theme.primaryColor),
                textScaler: const TextScaler.linear(1.15),
              ),
              onPressed: () async {
                bool? papi = false;
                if (SettingsSvc.settings.enablePrivateAPI.value && chat.isIMessage) {
                  papi = await showMethodDialog("Group Name Update Method");
                }
                if (papi == null) return;
                if (!papi) {
                  showChangeName(chat, "local", context);
                } else {
                  showChangeName(chat, "private-api", context);
                }
              },
            ),
          ),
        if (!chat.isGroup)
          Padding(
            padding: const EdgeInsets.only(left: 10.0, right: 10, top: 10),
            child: Row(
              mainAxisAlignment: kIsWeb || kIsDesktop ? MainAxisAlignment.center : MainAxisAlignment.spaceBetween,
              children: intersperse(const SizedBox(width: 5), [
                if (canCall) CallButton(tileColor: tileColor, chat: chat, iOS: iOS),
                if (facetimeSupported) VideoCallButton(tileColor: tileColor, chat: chat, iOS: iOS),
                if (chat.handles.isNotEmpty &&
                    ((chat.handles.first.contactsV2.firstOrNull?.emailAddresses.isNotEmpty ?? false) ||
                        chat.handles.first.address.contains("@")))
                  MailButton(tileColor: tileColor, chat: chat, iOS: iOS),
                if (!kIsWeb && !kIsDesktop) InfoButton(tileColor: tileColor, chat: chat, iOS: iOS),
                if (SettingsSvc.settings.macIsMine.value && chat.isRpSms)
                  ShareButton(tileColor: tileColor, chat: chat, iOS: iOS),
              ]).toList(),
            ),
          ),
        if (chat.isGroup)
          Padding(
            padding: const EdgeInsets.only(left: 15.0, top: 20.0, bottom: 5.0),
            child: Text("${chat.handles.length} ${iOS ? "OTHER MEMBERS" : "OTHER PEOPLE"}",
                style: context.theme.textTheme.bodyMedium!.copyWith(color: context.theme.colorScheme.outline)),
          ),
      ]),
    );
  }
}

class ShareButton extends StatelessWidget {
  const ShareButton({
    super.key,
    required this.tileColor,
    required this.chat,
    required this.iOS,
  });

  final Color tileColor;
  final Chat chat;
  final bool iOS;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: blurredCard(
        context: context,
        child: InkWell(
          onTap: () async {
            var ctx = context;
            showDialog(
              context: Get.context!,
              builder: (context) => AlertDialog(
                title: const Text('Choose your friends wisely'),
                content: Text(
                  "Apple may block devices due to spam or exceeding 20 users.",
                  style: Get.textTheme.bodyLarge,
                ),
                actions: <Widget>[
                  TextButton(
                      onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
                      child: Text("Cancel",
                          style:
                              context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary))),
                  TextButton(
                      onPressed: () async {
                        Navigator.of(context, rootNavigator: true).pop();
                        String code =
                            await PushSvc.uploadCode(false, await api.getDeviceInfo(config: PushSvc.state!.osConfig));
                        String text = "$rpApiRoot/$code";
                        cvc(chat).textController.text = text;
                        Navigator.of(ctx).pop();
                      },
                      child: Text("Invite",
                          style:
                              context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary))),
                ],
              ),
            );
          },
          borderRadius: BorderRadius.circular(15),
          child: SizedBox(
            height: 60,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(CupertinoIcons.arrow_up_right_diamond, color: context.theme.colorScheme.onSurface, size: 20),
                const SizedBox(height: 7.5),
                Text("Invite",
                    style: context.theme.textTheme.bodySmall!.copyWith(color: context.theme.colorScheme.onSurface)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class InfoButton extends StatelessWidget {
  const InfoButton({
    super.key,
    required this.tileColor,
    required this.chat,
    required this.iOS,
  });

  final Color tileColor;
  final Chat chat;
  final bool iOS;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: blurredCard(
        context: context,
        child: InkWell(
          onTap: () async {
            final contact = chat.handles.first.contactsV2.firstOrNull;
            final handle = chat.handles.first;
            if (contact == null || !contact.isNative) {
              final parameters = <String, dynamic>{
                'address': handle.address,
                'address_type': handle.address.isEmail ? 'email' : 'phone'
              };
              if (contact != null) {
                parameters["name"] = contact.computedDisplayName.replaceFirst("Maybe: ", "");
                if (contact.avatarPath != null && await File(contact.avatarPath!).exists()) {
                  parameters["image"] = base64Encode(await File(contact.avatarPath!).readAsBytes());
                }
              }
              await MethodChannelSvc.invokeMethod("open-contact-form", parameters);
            } else {
              try {
                await MethodChannelSvc.actions.viewContactForm(nativeContactId: contact.nativeContactId);
              } catch (_) {
                showSnackbar("Error", "Failed to find contact on device!");
              }
            }
          },
          borderRadius: BorderRadius.circular(15),
          child: SizedBox(
            height: 60,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  chat.handles.isNotEmpty &&
                          chat.handles.first.contactsV2.isNotEmpty &&
                          chat.handles.first.contactsV2.first.isNative
                      ? (iOS ? CupertinoIcons.info : Icons.info)
                      : (iOS ? CupertinoIcons.plus_circle : Icons.add_circle_outline),
                  color: context.theme.colorScheme.onSurface,
                  size: 20,
                ),
                const SizedBox(height: 7.5),
                Text(
                    chat.handles.isNotEmpty &&
                            chat.handles.first.contactsV2.isNotEmpty &&
                            chat.handles.first.contactsV2.first.isNative
                        ? "Info"
                        : "Add Contact",
                    style: context.theme.textTheme.bodySmall!.copyWith(color: context.theme.colorScheme.onSurface)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class MailButton extends StatelessWidget {
  const MailButton({
    super.key,
    required this.tileColor,
    required this.chat,
    required this.iOS,
  });

  final Color tileColor;
  final Chat chat;
  final bool iOS;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: blurredCard(
        context: context,
        child: InkWell(
          onTap: () {
            final contact = chat.handles.first.contactsV2.firstOrNull;
            showAddressPicker(contact, chat.handles.first, context, isEmail: true);
          },
          onLongPress: () {
            final contact = chat.handles.first.contactsV2.firstOrNull;
            showAddressPicker(contact, chat.handles.first, context, isEmail: true, isLongPressed: true);
          },
          borderRadius: BorderRadius.circular(15),
          child: SizedBox(
            height: 60,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(iOS ? CupertinoIcons.mail : Icons.email, color: context.theme.colorScheme.onSurface, size: 20),
                const SizedBox(height: 7.5),
                Text("Mail",
                    style: context.theme.textTheme.bodySmall!.copyWith(color: context.theme.colorScheme.onSurface)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class VideoCallButton extends StatelessWidget {
  const VideoCallButton({
    super.key,
    required this.tileColor,
    required this.chat,
    required this.iOS,
  });

  final Color tileColor;
  final Chat chat;
  final bool iOS;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: blurredCard(
        context: context,
        child: InkWell(
          onTap: () async {
            final data = await chat.getConversationData();
            final handle = await chat.ensureHandle();
            final handles = data.participants;
            handles.remove(handle);
            await PushSvc.placeOutgoingCall(handle, handles);
          },
          borderRadius: BorderRadius.circular(15),
          child: SizedBox(
            height: 60,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(iOS ? CupertinoIcons.video_camera : Icons.video_call_outlined,
                    color: context.theme.colorScheme.onSurface, size: 25),
                const SizedBox(height: 2.5),
                Text("Video",
                    style: context.theme.textTheme.bodySmall!.copyWith(color: context.theme.colorScheme.onSurface)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

const List<double> darkMatrix = <double>[
  1.385, -0.56, -0.112, 0.0, 0.3, //
  -0.315, 1.14, -0.112, 0.0, 0.3, //
  -0.315, -0.56, 1.588, 0.0, 0.3, //
  0.0, 0.0, 0.0, 1.0, 0.0
];

const List<double> lightMatrix = <double>[
  1.74, -0.4, -0.17, 0.0, 0.0, //
  -0.26, 1.6, -0.17, 0.0, 0.0, //
  -0.26, -0.4, 1.83, 0.0, 0.0, //
  0.0, 0.0, 0.0, 1.0, 0.0
];

Widget blurredCard({required Widget child, required BuildContext context}) {
  return ClipRRect(
    borderRadius: BorderRadius.circular(15),
    child: BackdropFilter(
        filter: ImageFilter.compose(
            outer: ImageFilter.blur(sigmaX: 30, sigmaY: 30),
            inner: ColorFilter.matrix(
              CupertinoTheme.maybeBrightnessOf(context) == Brightness.dark ? darkMatrix : lightMatrix,
            )),
        child: Container(
            decoration: BoxDecoration(
              color: context.theme.colorScheme.surfaceContainerHighest.withOpacity(0.3),
            ),
            clipBehavior: Clip.hardEdge,
            child: child)),
  );
}

class CallButton extends StatelessWidget {
  const CallButton({
    super.key,
    required this.tileColor,
    required this.chat,
    required this.iOS,
  });

  final Color tileColor;
  final Chat chat;
  final bool iOS;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: blurredCard(
        context: context,
        child: InkWell(
          onTap: () {
            final contact = chat.handles.first.contactsV2.firstOrNull;
            showAddressPicker(contact, chat.handles.first, context);
          },
          onLongPress: () {
            final contact = chat.handles.first.contactsV2.firstOrNull;
            showAddressPicker(contact, chat.handles.first, context, isLongPressed: true);
          },
          borderRadius: BorderRadius.circular(15),
          child: SizedBox(
            height: 60,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(iOS ? CupertinoIcons.phone : Icons.call, color: context.theme.colorScheme.onSurface, size: 20),
                const SizedBox(height: 7.5),
                Text("Call",
                    style: context.theme.textTheme.bodySmall!.copyWith(color: context.theme.colorScheme.onSurface)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
