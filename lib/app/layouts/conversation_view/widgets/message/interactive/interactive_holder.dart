import 'dart:convert';
import 'dart:typed_data';

import 'package:bluebubbles/app/state/message_state.dart';
import 'package:bluebubbles/app/state/message_state_scope.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/apple_pay.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/embedded_media.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/find_my.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/passwords.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/polls.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/supported_interactive.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/unsupported_interactive.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/url_preview.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/misc/tail_clipper.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/popup/message_popup_holder.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart' hide PayloadType;
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/services/ui/extension_service.dart';
import 'package:collection/collection.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';
import 'package:url_launcher/url_launcher.dart';

class InteractiveHolder extends StatefulWidget {
  const InteractiveHolder({
    super.key,
    required this.message,
  });

  final MessagePart message;

  @override
  State<StatefulWidget> createState() => _InteractiveHolderState();
}

class _InteractiveHolderState extends State<InteractiveHolder> with AutomaticKeepAliveClientMixin, ThemeHelpers {
  late MessageState _ms;
  MessageState get controller => _ms;

  MessagePart get part => widget.message;
  Message get message => controller.message;
  PayloadData? get payloadData => message.payloadData;
  dynamic content;
  bool hasImage = false;
  Uint8List? appIcon;

  @override
  void initState() {
    super.initState();
    _ms = MessageStateScope.readStateOnce(context);
    if (payloadData?.appData?.first.icon != null) {
      appIcon = base64Decode(payloadData!.appData!.first.icon!);
    }
    final attachment = message.dbAttachments.firstOrNull;
    if (attachment != null) {
      content = AttachmentsSvc.getContent(
        attachment,
        forExtension: true,
        autoDownload: true,
        onComplete: (file) {
          if (mounted) {
            setState(() {
              content = file;
              hasImage = true;
            });
          }
        },
      );
      if (content is PlatformFile &&
          (((content as PlatformFile).path != null) || ((content as PlatformFile).bytes != null))) {
        hasImage = true;
      }
    }
  }

  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Obx(() {
      if (message.amkSessionId != null &&
          ExtensionSvc.getLatest(message.amkSessionId!).firstOrNull != (message.stagingGuid ?? message.guid)) {
        var latestItems = ExtensionSvc.getLatest(message.amkSessionId!);

        SettingsSvc.settings.alwaysShowAvatars.value; // needs this to assuage Obx.
        if (!latestItems.contains(message.stagingGuid ?? message.guid)) {
          return const SizedBox.shrink();
        }

        var appData = payloadData!.appData!.first;
        return Padding(
          padding: EdgeInsets.only(
            left: message.isFromMe! ? 0 : 10,
            right: message.isFromMe! ? 10 : 0,
            top: 10,
            bottom: 10,
          ),
          child: Row(
            children: [
              if (appIcon != null)
                ClipRRect(
                  borderRadius: BorderRadius.circular(100),
                  child: Image.memory(
                    appIcon!,
                    width: 30,
                  ),
                ),
              const SizedBox(width: 5),
              Text(
                appData.ldText ?? "",
                style: context.theme.textTheme.labelMedium!.copyWith(
                  fontWeight: FontWeight.normal,
                  color: context.theme.colorScheme.outline,
                ),
              ),
            ],
          ),
        );
      }

      // Observe selection state
      final selected = !iOS && (controller.cvController?.selected.any((m) => m.guid == message.guid) ?? false);

      return ColorFiltered(
        colorFilter: ColorFilter.mode(
            !selected ? Colors.transparent : context.theme.colorScheme.tertiaryContainer.withValues(alpha: 0.5),
            BlendMode.srcOver),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: (payloadData == null && !message.isLegacyUrlPreview)
                ? null
                : () async {
                    String? url;
                    if (payloadData == null) {
                      url = message.url;
                    } else if (payloadData!.type == PayloadType.url) {
                      url = payloadData!.urlData!.first.url ?? payloadData!.urlData!.first.originalUrl;
                    } else {
                      url = payloadData!.appData!.first.url;
                      if (url != null &&
                          payloadData!.appData!.first.appId != null &&
                          ExtensionSvc.isAppSupported(payloadData!.appData!.first.appId!)) {
                        ExtensionSvc.engageApp(message);
                        return;
                      }
                    }
                    if (message.interactiveText == "Live Location") return;
                    if (url != null && Uri.tryParse(url) != null) {
                      await launchUrl(
                        Uri.parse(url),
                        mode: LaunchMode.externalApplication,
                      );
                    }
                  },
            child: CustomPaint(
              painter: iOS
                  ? null
                  : TailPainter(
                      isFromMe: message.isFromMe!,
                      showTail: false,
                      color: (context.theme.extensions[BubbleColors] as BubbleColors?)?.receivedBubbleColor ??
                          context.theme.colorScheme.surfaceContainerHighest,
                      width: 1.5,
                    ),
              child: Ink(
                color: iOS
                    ? ((context.theme.extensions[BubbleColors] as BubbleColors?)?.receivedBubbleColor ??
                        context.theme.colorScheme.surfaceContainerHighest)
                    : null,
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    maxWidth: NavigationSvc.width(context) * (NavigationSvc.isTabletMode(context) ? 0.5 : 0.6),
                    maxHeight: context.height * 0.6,
                    minHeight: 40,
                    minWidth: 40,
                  ),
                  child: Padding(
                    padding: EdgeInsets.only(left: message.isFromMe! ? 0 : 10, right: message.isFromMe! ? 10 : 0),
                    child: Center(
                      heightFactor: 1,
                      widthFactor: 1,
                      child: _ms.shouldHideAttachments.value
                          ? const Padding(padding: EdgeInsets.all(15), child: Text("Interactive Message"))
                          : Obx(() {
                              final isTempMessage = controller.isSending.value;
                              return Opacity(
                                opacity: isTempMessage ? 0.5 : 1,
                                child: Stack(
                                  children: [
                                    Builder(builder: (context) {
                                      if (payloadData == null && !(message.isLegacyUrlPreview)) {
                                        switch (message.interactiveText) {
                                          case "Handwritten Message":
                                          case "Handwriten Message":
                                          case "Digital Touch Message":
                                            if (SettingsSvc.settings.enablePrivateAPI.value &&
                                                SettingsSvc.serverDetails.isMinBigSur &&
                                                SettingsSvc.serverDetails.supportsGroupChatManagement) {
                                              return const EmbeddedMedia();
                                            } else {
                                              return UnsupportedInteractive(
                                                payloadData: null,
                                                content: content,
                                                balloonBundleId: message.balloonBundleId,
                                              );
                                            }
                                          default:
                                            return UnsupportedInteractive(
                                              payloadData: null,
                                              content: content,
                                              balloonBundleId: message.balloonBundleId,
                                            );
                                        }
                                      } else if (payloadData?.type == PayloadType.url || message.isLegacyUrlPreview) {
                                        if (payloadData == null) {
                                          return UrlPreview(
                                            data: UrlPreviewData(
                                              url: message.url,
                                              originalUrl: message.url,
                                            ),
                                          );
                                        }
                                        final urlData = payloadData!.urlData!.first;
                                        return UrlPreview(data: urlData);
                                      } else {
                                        final data = payloadData!.appData!.first;
                                        switch (message.interactiveText) {
                                          case "Polls":
                                            return Polls(
                                              data: data,
                                              messageState: controller,
                                            );
                                          case "Apple Pay":
                                            return ApplePay(
                                              data: data,
                                            );
                                          case "Live Location":
                                            return FindMy(
                                              data: data,
                                              message: message,
                                              isPopup: PopupScope.maybeOf(
                                                    context,
                                                  ) !=
                                                  null,
                                            );
                                          case "com.openbubbles.passwords":
                                            return SharedPasswords(
                                              data: data,
                                              message: message,
                                            );
                                          default:
                                            if (data.appId != null && ExtensionSvc.isAppSupported(data.appId!)) {
                                              return SupportedInteractive(
                                                data: data,
                                                content: content,
                                                guid: message.stagingGuid ?? message.guid,
                                              );
                                            }
                                            return UnsupportedInteractive(
                                              payloadData: data,
                                              content: content,
                                              balloonBundleId: message.balloonBundleId,
                                            );
                                        }
                                      }
                                    }),
                                    if (appIcon != null &&
                                        (hasImage ||
                                            ((payloadData?.appData?.first.isLive ?? false) &&
                                                (payloadData?.appData?.first.appId != null &&
                                                    ExtensionSvc.isAppSupported(payloadData!.appData!.first.appId!)))))
                                      Positioned(
                                        left: 12,
                                        top: 12,
                                        child: ClipRRect(
                                          borderRadius: BorderRadius.circular(100),
                                          child: Image.memory(
                                            appIcon!,
                                            width: 28,
                                            height: 28,
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                              );
                            }),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
    });
  }
}
