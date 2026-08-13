import 'dart:async';
import 'dart:convert';
import 'dart:ui';

import 'package:async_task/async_task_extension.dart';
import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/app/layouts/conversation_details/conversation_details.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/header/header_widgets.dart';
import 'package:bluebubbles/app/components/avatars/contact_avatar_group_widget.dart';
import 'package:bluebubbles/app/state/chat_state_scope.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:gesture_x_detector/gesture_x_detector.dart';
import 'package:flutter/material.dart' hide BackButton;
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';

class CupertinoHeader extends StatelessWidget implements PreferredSizeWidget {
  const CupertinoHeader({super.key, required this.controller});

  final ConversationViewController controller;

  // simulate apple's saturatioon
  static const List<double> darkMatrix = <double>[
    1.385, -0.56, -0.112, 0.0, 0.3, //
    -0.315, 1.14, -0.112, 0.0, 0.3, //
    -0.315, -0.56, 1.588, 0.0, 0.3, //
    0.0, 0.0, 0.0, 1.0, 0.0
  ];

  static const List<double> lightMatrix = <double>[
    1.74, -0.4, -0.17, 0.0, 0.0, //
    -0.26, 1.6, -0.17, 0.0, 0.0, //
    -0.26, -0.4, 1.83, 0.0, 0.0, //
    0.0, 0.0, 0.0, 1.0, 0.0
  ];

  @override
  Widget build(BuildContext context) {
    void goBack() {
      if (controller.inSelectMode.value) {
        controller.inSelectMode.value = false;
        controller.selected.clear();
        return;
      }
      if (LifecycleSvc.isBubble) {
        SystemNavigator.pop();
        return;
      }
      controller.close();
      if (Get.isSnackbarOpen) {
        Get.closeAllSnackbars();
      }
      Navigator.of(context).pop();
    }

    return ClipRect(
      child: BackdropFilter(
          filter: ImageFilter.compose(
              outer: ImageFilter.blur(sigmaX: 30, sigmaY: 30, tileMode: TileMode.mirror),
              inner: ColorFilter.matrix(
                CupertinoTheme.maybeBrightnessOf(context) == Brightness.dark ? darkMatrix : lightMatrix,
              )),
          child: Stack(
            children: [
              Column(children: [
                Expanded(
                    child: Container(
                  decoration: BoxDecoration(
                    color: context.theme.colorScheme.surfaceContainerHighest.withOpacity(0.7),
                    border: Border(
                      bottom: BorderSide(
                          color: context.theme.colorScheme.outlineVariant.withValues(alpha: 0.25), width: 0.5),
                    ),
                  ),
                  child: Material(
                    color: Colors.transparent,
                    child: Padding(
                      padding: EdgeInsets.only(
                          left: 20.0,
                          right: 20,
                          top: (MediaQuery.of(context).viewPadding.top - 2).clamp(0, double.infinity)),
                      child: Stack(alignment: Alignment.center, children: [
                        Padding(
                            padding: const EdgeInsets.only(top: 5),
                            child: Align(
                              alignment: Alignment.topLeft,
                              child: XGestureDetector(
                                supportTouch: true,
                                onTap: !kIsDesktop
                                    ? null
                                    : (details) {
                                        goBack();
                                      },
                                child: CallbackShortcuts(
                                  bindings: {
                                    const SingleActivator(LogicalKeyboardKey.enter): goBack,
                                    const SingleActivator(LogicalKeyboardKey.select): goBack,
                                    const SingleActivator(LogicalKeyboardKey.space): goBack,
                                    // Left or down from the back button moves focus to the attachment (+) button.
                                    const SingleActivator(LogicalKeyboardKey.arrowLeft):
                                        controller.attachmentPickerFocusNode.requestFocus,
                                    const SingleActivator(LogicalKeyboardKey.arrowDown):
                                        controller.attachmentPickerFocusNode.requestFocus,
                                  },
                                  child: InkWell(
                                    focusNode: controller.headerBackFocusNode,
                                    borderRadius: BorderRadius.circular(10),
                                    onTap: () {
                                      if (kIsDesktop) return;
                                      goBack();
                                    },
                                    child: Padding(
                                      padding: const EdgeInsets.all(3.0),
                                      child: _UnreadIcon(controller: controller),
                                    ),
                                  ),
                                ),
                              ),
                            )),
                        Align(
                          alignment: Alignment.center,
                          child: XGestureDetector(
                            supportTouch: true,
                            onTap: !kIsDesktop
                                ? null
                                : (details) {
                                    Navigator.of(context).push(
                                      ThemeSwitcher.buildPageRoute(
                                        builder: (context) => ConversationDetails(
                                          chat: controller.chat,
                                        ),
                                      ),
                                    );
                                  },
                            child: InkWell(
                              onTap: () {
                                if (kIsDesktop) return;
                                Navigator.of(context).push(
                                  ThemeSwitcher.buildPageRoute(
                                    builder: (context) => ConversationDetails(
                                      chat: controller.chat,
                                    ),
                                  ),
                                );
                              },
                              borderRadius: BorderRadius.circular(10),
                              child: Padding(
                                padding: const EdgeInsets.all(3.0),
                                child: _ChatIconAndTitle(parentController: controller),
                              ),
                            ),
                          ),
                        ),
                        Padding(
                            padding: const EdgeInsets.only(top: 5),
                            child: Align(
                                alignment: Alignment.topRight,
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    FaceTimeBtn(controller: controller),
                                    ManualMark(controller: controller),
                                  ],
                                ))),
                      ]),
                    ),
                  ),
                )),
                Obx(() => controller.suggestedContact.value != null
                    ? Container(
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
                                  style: context.theme.textTheme.bodyMedium
                                      ?.copyWith(color: context.theme.colorScheme.outline),
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
                                final existingContact =
                                    existingParticipant.contactsV2.firstWhereOrNull((c) => c.isNative);
                                final displayName = contact.displayName.replaceFirst("Maybe: ", "");

                                if (Platform.isAndroid) {
                                  final avatarBytes =
                                      contact.avatarPath != null && await File(contact.avatarPath!).exists()
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
                                    contact.avatarPath = null;
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
                                  contact.avatarPath = null;
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
                                    var handle = contact.handles.copy();
                                    ContactsSvcV2.notifyHandlesUpdated(handle.map((i) => i.id!).toList());
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
                Obx(() => controller.suggestedContact.value == null && controller.suggestShare.value
                    ? Container(
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
                                  style: context.theme.textTheme.bodyMedium
                                      ?.copyWith(color: context.theme.colorScheme.outline),
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
                                  message: api.Message.shareProfile(await api.decodeProfileMessage(
                                      s: SettingsSvc.settings.shareProfileMessage.value!)),
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
              ]),
              const Positioned(
                bottom: 0,
                left: 0,
                right: 0,
                child: HeaderProgressIndicator(),
              ),
            ],
          )),
    );
  }

  @override
  Size get preferredSize =>
      Size.fromHeight((Get.context!.orientation == Orientation.landscape && Platform.isAndroid ? 55 : 75) *
          SettingsSvc.settings.avatarScale.value);
}

class _UnreadIcon extends StatefulWidget {
  const _UnreadIcon({required this.controller});

  final ConversationViewController controller;

  @override
  State<StatefulWidget> createState() => _UnreadIconState();
}

class _UnreadIconState extends State<_UnreadIcon> {
  late final StreamSubscription<Query<Chat>> sub;
  bool hasStream = false;

  @override
  void dispose() {
    if (!kIsWeb && hasStream) sub.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.only(top: 3.0, right: 3),
          child: Obx(() {
            final icon = widget.controller.inSelectMode.value ? CupertinoIcons.xmark : CupertinoIcons.back;
            return Text(
              String.fromCharCode(icon.codePoint),
              style: TextStyle(
                fontFamily: icon.fontFamily,
                package: icon.fontPackage,
                fontSize: 36,
                color: context.theme.colorScheme.primary,
              ),
            );
          }),
        ),
        const SizedBox(width: 2),
        Obx(() {
          final _count =
              widget.controller.inSelectMode.value ? widget.controller.selected.length : ChatsSvc.unreadCount.value;
          if (_count == 0) return const SizedBox.shrink();
          return Padding(
              padding: const EdgeInsets.only(top: 3),
              child: Container(
                  height: 25.0,
                  width: 25.0,
                  constraints: const BoxConstraints(minWidth: 20),
                  decoration: BoxDecoration(
                    color: context.theme.colorScheme.primary,
                    borderRadius: BorderRadius.circular(15),
                  ),
                  alignment: Alignment.center,
                  child: Padding(
                    padding: _count > 99 ? const EdgeInsets.symmetric(horizontal: 2.5) : EdgeInsets.zero,
                    child: Text(
                      _count.toString(),
                      style: context.textTheme.bodyMedium!.copyWith(
                          color: context.theme.colorScheme.onPrimary,
                          fontSize: _count > 99
                              ? context.textTheme.bodyMedium!.fontSize! - 1.0
                              : context.textTheme.bodyMedium!.fontSize),
                    ),
                  )));
        }),
      ],
    );
  }
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
    return Obx(() {
      // Get chat state from scope - handles title logic including redacted mode
      final chatState = ChatStateScope.of(context);
      final _title = chatState.title.value ?? controller.chat.getTitle();

      final children = [
        const IgnorePointer(
          ignoring: true,
          child: ContactAvatarGroupWidget(
            size: 54,
          ),
        ),
        const SizedBox(height: 5, width: 5),
        Row(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: NavigationSvc.width(context) / 2.5,
                ),
                child: RichText(
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  text: TextSpan(
                    style: context.theme.textTheme.bodyMedium,
                    children: MessageHelper.buildEmojiText(
                      _title,
                      context.theme.textTheme.bodyMedium!,
                    ),
                  ),
                ),
              ),
              Icon(
                CupertinoIcons.chevron_right,
                size: context.theme.textTheme.bodyMedium!.fontSize!,
                color: context.theme.colorScheme.outline.withValues(alpha: 0.5),
              ),
            ]),
      ];

      if (context.orientation == Orientation.landscape && Platform.isAndroid) {
        return Row(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: children,
        );
      } else {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: children,
        );
      }
    });
  }
}
