import 'dart:async';
import 'dart:convert';

import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/app/layouts/conversation_details/conversation_details.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/header/header_widgets.dart';
import 'package:bluebubbles/app/components/avatars/contact_avatar_group_widget.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/reply/reply_thread_popup.dart';
import 'package:bluebubbles/app/state/chat_state_scope.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart' hide BackButton;
import 'package:flutter/services.dart';
import 'package:flutter_acrylic/flutter_acrylic.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';
import 'package:url_launcher/url_launcher.dart';

class MaterialHeader extends StatelessWidget implements PreferredSizeWidget {
  const MaterialHeader({super.key, required this.controller});

  final ConversationViewController controller;

  @override
  Widget build(BuildContext context) {
    final Rx<Color> _backgroundColor = context.theme.colorScheme.surfaceContainerHighest
        .withValues(alpha: (kIsDesktop && SettingsSvc.settings.windowEffect.value != WindowEffect.disabled) ? 0.4 : 1)
        .obs;
    final Color _foregroundColor = context.theme.colorScheme.onSurfaceVariant;

    return Stack(children: [
      Obx(() => AppBar(
            backgroundColor: _backgroundColor.value,
            surfaceTintColor: Colors.transparent,
            scrolledUnderElevation: 0,
            systemOverlayStyle: context.theme.colorScheme.brightness == Brightness.dark
                ? SystemUiOverlayStyle.light
                : SystemUiOverlayStyle.dark,
            automaticallyImplyLeading: false,
            toolbarHeight: (kIsDesktop ? 25 : 0) + kToolbarHeight,
            leadingWidth: 30,
            leading: Padding(
              padding: EdgeInsets.only(left: 5.0, top: kIsDesktop ? 20 : 0),
              child: CallbackShortcuts(
                bindings: {
                  const SingleActivator(LogicalKeyboardKey.enter): controller.headerBackFocusNode.requestFocus,
                  // Left or down from the back button moves focus to the attachment (+) button.
                  const SingleActivator(LogicalKeyboardKey.arrowLeft): controller.attachmentPickerFocusNode.requestFocus,
                  const SingleActivator(LogicalKeyboardKey.arrowDown): controller.attachmentPickerFocusNode.requestFocus,
                },
                child: Focus(
                  focusNode: controller.headerBackFocusNode,
                  child: BackButton(
                    color: _foregroundColor,
                    onPressed: () {
                      if (controller.inSelectMode.value) {
                        controller.inSelectMode.value = false;
                        controller.selected.clear();
                        return true;
                      }
                      if (LifecycleSvc.isBubble) {
                        SystemNavigator.pop();
                        return true;
                      }
                      controller.close();
                      return false;
                    },
                  ),
                ),
              ),
            ),
            title: Padding(
              padding: EdgeInsets.only(top: kIsDesktop ? 20 : 0),
              child: InkWell(
                borderRadius: BorderRadius.circular(10),
                onTap: controller.chat.isGroup
                    ? () {
                        Navigator.of(context).push(
                          ThemeSwitcher.buildPageRoute(
                            builder: (context) => ConversationDetails(
                              chat: controller.chat,
                            ),
                          ),
                        );
                      }
                    : () async {
                        final handle = controller.chat.handles.first;
                        final contact = handle.contactsV2.firstOrNull;
                        if (contact == null || !contact.isNative) {
                          await MethodChannelSvc.actions.openContactForm(
                            address: handle.address,
                            isEmail: handle.address.isEmail,
                          );
                        } else {
                          try {
                            await MethodChannelSvc.actions.viewContactForm(nativeContactId: contact.nativeContactId);
                          } catch (_) {
                            showSnackbar("Error", "Failed to find contact on device!");
                          }
                        }
                      },
                child: Padding(
                  padding: const EdgeInsets.all(5.0),
                  child: _ChatIconAndTitle(parentController: controller),
                ),
              ),
            ),
            actions: [
              Padding(
                padding: EdgeInsets.only(top: kIsDesktop ? 20 : 0),
                child: ManualMark(controller: controller),
              ),
              if (Platform.isAndroid && !controller.chat.isGroup && controller.chat.handles.first.address.isPhoneNumber)
                IconButton(
                  icon: Icon(Icons.call_outlined, color: _foregroundColor),
                  onPressed: () {
                    launchUrl(Uri(scheme: "tel", path: controller.chat.handles.first.address));
                  },
                ),
              if (Platform.isAndroid && !controller.chat.isGroup && controller.chat.handles.first.address.isEmail)
                IconButton(
                  icon: Icon(Icons.mail_outlined, color: _foregroundColor),
                  onPressed: () {
                    launchUrl(Uri(scheme: "mailto", path: controller.chat.handles.first.address));
                  },
                ),
              FaceTimeBtn(controller: controller),
              Padding(
                padding: EdgeInsets.only(top: kIsDesktop ? 20 : 0),
                child: PopupMenuButton<int>(
                  color: context.theme.colorScheme.surfaceContainerHighest,
                  shape: SettingsSvc.settings.skin.value != Skins.Material
                      ? const RoundedRectangleBorder(
                          borderRadius: BorderRadius.all(
                            Radius.circular(20.0),
                          ),
                        )
                      : null,
                  onSelected: (int value) {
                    if (value == 0) {
                      Navigator.of(context).push(
                        ThemeSwitcher.buildPageRoute(
                          builder: (context) => ConversationDetails(
                            chat: controller.chat,
                          ),
                        ),
                      );
                    } else if (value == 1) {
                      ChatsSvc.setChatArchived(controller.chat, !controller.chat.isArchived!);
                      if (Get.isSnackbarOpen) {
                        Get.closeAllSnackbars();
                      }
                      Navigator.of(context).pop();
                    } else if (value == 2) {
                      showBBDialog(
                        barrierDismissible: false,
                        context: context,
                        title: "Are you sure?",
                        body: "This chat will be moved to trash on all synced devices",
                        actions: <BBDialogAction>[
                          BBDialogAction(
                            text: "No",
                            onPressed: () {
                              if (Get.isSnackbarOpen) {
                                Get.closeAllSnackbars();
                              }
                              Navigator.of(context, rootNavigator: true).pop();
                            },
                          ),
                          BBDialogAction(
                            text: "Yes",
                            isDestructive: true,
                            onPressed: () async {
                              ChatsSvc.removeChat(controller.chat);
                              ChatsSvc.softDeleteChat(controller.chat);
                              if (Get.isSnackbarOpen) {
                                Get.closeAllSnackbars();
                              }
                              Navigator.of(context, rootNavigator: true).pop();
                            },
                          ),
                        ],
                      );
                    } else if (value == 3) {
                      showBookmarksThread(controller, context);
                    }
                  },
                  itemBuilder: (context) {
                    return <PopupMenuItem<int>>[
                      PopupMenuItem(
                        value: 0,
                        child: Text(
                          'Details',
                          style: context.textTheme.bodyLarge!.apply(color: context.theme.colorScheme.onSurfaceVariant),
                        ),
                      ),
                      if (!LifecycleSvc.isBubble)
                        PopupMenuItem(
                          value: 1,
                          child: Text(
                            controller.chat.isArchived! ? 'Unarchive' : 'Archive',
                            style:
                                context.textTheme.bodyLarge!.apply(color: context.theme.colorScheme.onSurfaceVariant),
                          ),
                        ),
                      if (!LifecycleSvc.isBubble)
                        PopupMenuItem(
                          value: 2,
                          child: Text(
                            'Delete',
                            style:
                                context.textTheme.bodyLarge!.apply(color: context.theme.colorScheme.onSurfaceVariant),
                          ),
                        ),
                      PopupMenuItem(
                        value: 3,
                        child: Text(
                          'Bookmarks',
                          style: context.textTheme.bodyLarge!.apply(color: context.theme.colorScheme.onSurfaceVariant),
                        ),
                      ),
                    ];
                  },
                  icon: Icon(
                    Icons.more_vert,
                    color: _foregroundColor,
                  ),
                ),
              )
            ],
          )),
      Positioned(
        top: kIsDesktop ? 90 : kToolbarHeight,
        left: 0,
        right: 0,
        child: Obx(() => controller.suggestedContact.value != null
            ? Container(
                color: context.theme.colorScheme.surface.withAlpha(128),
                padding: const EdgeInsets.all(12),
                child: Row(
                  children: [
                    if (controller.suggestedContact.value!.avatarPath != null)
                      ContactAvatarWidget(
                        contact: controller.suggestedContact.value,
                        size: 38,
                        preferHighResAvatar: true,
                        scaleSize: false,
                      ),
                    if (controller.suggestedContact.value!.avatarPath != null) const SizedBox(width: 15),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text("New Contact Info", style: context.theme.textTheme.titleMedium),
                        Text(
                          controller.suggestedContact.value!.displayName.replaceFirst("Maybe: ", ""),
                          style: context.theme.textTheme.bodyMedium?.copyWith(color: context.theme.colorScheme.outline),
                        ),
                      ],
                    ),
                    const Spacer(),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: context.theme.colorScheme.outline.withAlpha(64),
                        padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 13),
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        elevation: 0.0,
                        minimumSize: Size.zero,
                      ),
                      onPressed: () async {
                        final contact = controller.suggestedContact.value!;
                        final existingParticipant = Handle.findOne(id: controller.chat.handles.first.id)!;
                        final existingContact = existingParticipant.contactsV2.firstWhereOrNull((c) => c.isNative);
                        final displayName = contact.displayName.replaceFirst("Maybe: ", "");

                        if (Platform.isAndroid) {
                          final avatarBytes = contact.avatarPath != null && await File(contact.avatarPath!).exists()
                              ? await File(contact.avatarPath!).readAsBytes()
                              : null;
                          final parameters = <String, dynamic>{
                            'address': existingParticipant.address,
                            'address_type': existingParticipant.address.isEmail ? 'email' : 'phone',
                            'name': displayName,
                            'first_name': contact.firstName,
                            'middle_name': contact.middleName,
                            'last_name': contact.lastName,
                          };
                          if (avatarBytes != null) {
                            parameters["image"] = base64Encode(avatarBytes);
                          }
                          if (existingContact != null) {
                            existingContact.displayName = displayName;
                            existingContact.nickname = contact.nickname;
                            existingContact.firstName = contact.firstName;
                            existingContact.lastName = contact.lastName;
                            existingContact.middleName = contact.middleName;
                            existingContact.namePrefix = contact.namePrefix;
                            existingContact.nameSuffix = contact.nameSuffix;
                            existingContact.company = contact.company;
                            existingContact.avatarPath = contact.avatarPath;
                            existingContact.save();
                            parameters["existing"] = existingContact.nativeContactId;
                          }
                          await MethodChannelSvc.invokeMethod("open-contact-form", parameters);
                        } else {
                          final targetContact = existingContact ?? contact;
                          targetContact.displayName = displayName;
                          targetContact.nickname = contact.nickname;
                          targetContact.firstName = contact.firstName;
                          targetContact.lastName = contact.lastName;
                          targetContact.middleName = contact.middleName;
                          targetContact.namePrefix = contact.namePrefix;
                          targetContact.nameSuffix = contact.nameSuffix;
                          targetContact.company = contact.company;
                          targetContact.avatarPath = contact.avatarPath;
                          targetContact.isNative = true;
                          targetContact.save();
                        }
                        if (contact.posterPath != "alreadyset") {
                          existingParticipant.setPoster(contact.posterPath);
                        }
                        contact.posterPath = null;
                        if (contact.avatarPath != null) {
                          try {
                            await File(contact.avatarPath!).delete();
                          } catch (_) {}
                        }
                        Database.contactsV2.remove(contact.id);
                        controller.suggestedContact.value = null;

                        ContactsSvcV2.notifyHandlesUpdated([existingParticipant.id!]);
                      },
                      child: Text(
                        controller.chat.handles.first.contactsV2.firstWhereOrNull((c) => c.isNative) == null
                            ? "Add"
                            : "Update",
                        style: context.theme.textTheme.titleMedium,
                      ),
                    ),
                    const SizedBox(width: 5),
                    Opacity(
                      opacity: 0.5,
                      child: IconButton(
                        icon: Icon(
                          CupertinoIcons.clear,
                          color: context.theme.colorScheme.outline,
                          size: 24,
                        ),
                        style: ElevatedButton.styleFrom(splashFactory: NoSplash.splashFactory),
                        visualDensity: Platform.isAndroid ? VisualDensity.compact : null,
                        onPressed: () async {
                          final contact = controller.suggestedContact.value;
                          if (contact?.posterPath != null) {
                            if (contact!.posterPath != "alreadyset") {
                              await PushSvc.deletePoster(contact.posterPath!);
                            }
                            contact.posterPath = null;
                          }
                          if (contact?.avatarPath != null) {
                            try {
                              await File(contact!.avatarPath!).delete();
                            } catch (_) {}
                          }
                          if (contact != null) {
                            Database.contactsV2.remove(contact.id);
                          }
                          controller.suggestedContact.value = null;
                        },
                      ),
                    ),
                  ],
                ),
              )
            : const SizedBox.shrink()),
      ),
      Positioned(
        top: kIsDesktop ? 90 : kToolbarHeight,
        left: 0,
        right: 0,
        child: Obx(() => controller.suggestedContact.value == null && controller.suggestShare.value
            ? Container(
                color: context.theme.colorScheme.surface.withAlpha(128),
                padding: const EdgeInsets.all(12),
                child: Row(
                  children: [
                    const ContactAvatarWidget(
                      size: 38,
                      preferHighResAvatar: true,
                      scaleSize: false,
                    ),
                    const SizedBox(width: 15),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text("Share your name and photo?", style: context.theme.textTheme.titleMedium),
                        Text(
                          SettingsSvc.settings.userName.value,
                          style: context.theme.textTheme.bodyMedium?.copyWith(color: context.theme.colorScheme.outline),
                        ),
                      ],
                    ),
                    const Spacer(),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: context.theme.colorScheme.outline.withAlpha(64),
                        padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 13),
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        elevation: 0.0,
                        minimumSize: Size.zero,
                      ),
                      onPressed: () async {
                        SettingsSvc.settings.sharedContacts.add(controller.chat.handles.first.address);
                        await SettingsSvc.settings.saveOneAsync('sharedContacts');
                        controller.suggestShare.value = false;
                        await PushSvc.updateShareState();

                        var msg = await api.newMsg(
                          conversation: api.ConversationData(
                              participants: [RustPushBBUtils.bbHandleToRust(controller.chat.handles.first)]),
                          sender: await controller.chat.ensureHandle(),
                          message: api.Message.shareProfile(
                              await api.decodeProfileMessage(s: SettingsSvc.settings.shareProfileMessage.value!)),
                        );
                        await (BackendSvc as RustPushBackend).sendMsg(msg);
                      },
                      child: Text("Share", style: context.theme.textTheme.titleMedium),
                    ),
                    const SizedBox(width: 5),
                    Opacity(
                      opacity: 0.5,
                      child: IconButton(
                        icon: Icon(
                          CupertinoIcons.clear,
                          color: context.theme.colorScheme.outline,
                          size: 24,
                        ),
                        style: ElevatedButton.styleFrom(splashFactory: NoSplash.splashFactory),
                        visualDensity: Platform.isAndroid ? VisualDensity.compact : null,
                        onPressed: () async {
                          SettingsSvc.settings.dismissedContacts.add(controller.chat.handles.first.address);
                          await SettingsSvc.settings.saveOneAsync('dismissedContacts');
                          controller.suggestShare.value = false;
                          await PushSvc.updateShareState();
                        },
                      ),
                    ),
                  ],
                ),
              )
            : const SizedBox.shrink()),
      ),
      const Positioned(
        bottom: 0,
        left: 0,
        right: 0,
        child: HeaderProgressIndicator(),
      ),
    ]);
  }

  @override
  Size get preferredSize => Size.fromHeight(kIsDesktop ? 90 : kToolbarHeight);
}

class _ChatIconAndTitle extends CustomStateful<ConversationViewController> {
  const _ChatIconAndTitle({required super.parentController});

  @override
  State<StatefulWidget> createState() => _ChatIconAndTitleState();
}

class _ChatIconAndTitleState extends CustomState<_ChatIconAndTitle, void, ConversationViewController> {
  late final StreamSubscription<ContactV2?> sub2;

  @override
  void initState() {
    super.initState();
    sub2 = controller.suggestedContact.listen((_) {
      if (mounted) setState(() {});
    });
    tag = controller.chat.guid;
    // keep controller in memory since the widget is part of a list
    // (it will be disposed when scrolled out of view)
    forceDelete = false;
  }

  @override
  void dispose() {
    sub2.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final chatState = ChatStateScope.of(context);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.only(right: 12.5),
          child: IgnorePointer(
            ignoring: true,
            child: ContactAvatarGroupWidget(
              size: !controller.chat.isGroup ? 35 : 40,
            ),
          ),
        ),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Obx(() {
                // Get title from ChatState - it handles all title logic including redacted mode
                final _title = controller.inSelectMode.value
                    ? "${controller.selected.length} selected"
                    : chatState.title.value ?? controller.chat.getTitle();
                return Text(
                  _title,
                  style: context.theme.textTheme.titleLarge!
                      .apply(color: context.theme.colorScheme.onSurfaceVariant, fontSizeFactor: 0.85),
                  maxLines: 1,
                  overflow: TextOverflow.fade,
                );
              }),
              if (samsung &&
                  (controller.chat.isGroup ||
                      (!controller.chat.getTitle().isPhoneNumber && !controller.chat.getTitle().isEmail)))
                Text(
                  controller.chat.isGroup
                      ? "${controller.chat.handles.length} recipients"
                      : controller.chat.handles[0].address,
                  style: context.theme.textTheme.labelLarge!.apply(color: context.theme.colorScheme.outline),
                  maxLines: 1,
                  overflow: TextOverflow.fade,
                ),
            ],
          ),
        ),
      ],
    );
  }
}
