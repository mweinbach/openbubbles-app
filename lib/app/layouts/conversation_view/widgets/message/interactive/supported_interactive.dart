import 'dart:convert';

import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/polls.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/services/ui/extension_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';

class SupportedInteractive extends StatefulWidget {
  final iMessageAppData data;
  final dynamic content;
  final String? guid;

  const SupportedInteractive({
    super.key,
    required this.data,
    required this.content,
    required this.guid,
  });

  @override
  State<StatefulWidget> createState() => _SupportedInteractiveState();
}

class _SupportedInteractiveState extends State<SupportedInteractive> with AutomaticKeepAliveClientMixin {
  iMessageAppData get data => widget.data;
  dynamic get file => File(widget.content.path!);

  static final List<_SupportedInteractiveState> aliveExtensions = [];

  bool forcedDead = false;

  @override
  void initState() {
    super.initState();
    aliveExtensions.add(this);
    if (aliveExtensions.length > 20) {
      final oldExt = aliveExtensions.removeAt(0);
      oldExt.forcedDead = true;
      oldExt.setState(() {});
    }
  }

  @override
  void dispose() {
    if (aliveExtensions.contains(this)) {
      aliveExtensions.remove(this);
    }
    super.dispose();
  }

  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    if (widget.data.isLive == true && widget.data.appId != null && ExtensionSvc.isAppAvailable(widget.data.appId!)) {
      final nativeData = widget.data.toNative(null);
      nativeData["messageGuid"] = widget.guid;
      nativeData["user-count"] = ChatsSvc.activeChat!.chat.handles.length + 1;
      return SizedBox(
          height: 250,
          child: forcedDead
              ? const SizedBox.shrink()
              : RepaintBoundary(
                  child: AndroidView(
                    key: ValueKey(jsonEncode(nativeData)),
                    viewType: "extension-live",
                    layoutDirection: TextDirection.ltr,
                    creationParams: nativeData,
                    creationParamsCodec: const StandardMessageCodec(),
                  ),
                ));
    }
    if (widget.data.appName == "Polls" && widget.data.appId == null) {
      return Polls(data: widget.data, messageState: null);
    }
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Stack(
          alignment: Alignment.bottomLeft,
          children: [
            if (widget.content is PlatformFile && widget.content.bytes != null)
              Image.memory(
                widget.content.bytes!,
                gaplessPlayback: true,
                filterQuality: FilterQuality.none,
                errorBuilder: (context, object, stacktrace) => Center(
                  heightFactor: 1,
                  child: Text("Failed to display image", style: context.theme.textTheme.bodyLarge),
                ),
              ),
            if (widget.content is PlatformFile && widget.content.bytes == null && widget.content.path != null)
              Image.file(
                file,
                gaplessPlayback: true,
                filterQuality: FilterQuality.none,
                errorBuilder: (context, object, stacktrace) => Center(
                  heightFactor: 1,
                  child: Text("Failed to display image", style: context.theme.textTheme.bodyLarge),
                ),
              ),
            if (!isNullOrEmpty(data.userInfo?.imageTitle) || !isNullOrEmpty(data.userInfo?.imageSubtitle))
              Positioned(
                bottom: 5,
                left: 15,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (!isNullOrEmpty(data.userInfo?.imageTitle))
                      Text(
                        data.userInfo!.imageTitle!,
                        style: context.theme.textTheme.bodyMedium!.apply(fontWeightDelta: 2),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    if (!isNullOrEmpty(data.userInfo?.imageSubtitle))
                      Text(data.userInfo!.imageSubtitle!,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: context.theme.textTheme.labelMedium!.copyWith(fontWeight: FontWeight.normal)),
                  ],
                ),
              ),
          ],
        ),
        Padding(
          padding: const EdgeInsets.all(15.0),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              mainAxisSize: MainAxisSize.min,
              children: [
                if (!isNullOrEmpty(data.userInfo?.caption))
                  Flexible(
                    fit: !isNullOrEmpty(data.userInfo?.secondarySubcaption) ? FlexFit.tight : FlexFit.loose,
                    child: Text(
                      data.userInfo!.caption!,
                      style: context.theme.textTheme.bodyLarge!.apply(fontWeightDelta: 2),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                if (isNullOrEmpty(data.userInfo?.caption) && !isNullOrEmpty(data.ldText))
                  Flexible(
                    fit: !isNullOrEmpty(data.userInfo?.secondarySubcaption) ? FlexFit.tight : FlexFit.loose,
                    child: Text(
                      data.ldText!,
                      style: context.theme.textTheme.bodyLarge!.apply(fontWeightDelta: 2),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                if (!isNullOrEmpty(data.userInfo?.secondarySubcaption))
                  Text(
                    data.userInfo!.secondarySubcaption!,
                    style: context.theme.textTheme.bodyLarge!.apply(fontWeightDelta: 2),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
              ],
            ),
            if (!isNullOrEmpty(data.userInfo?.subcaption)) const SizedBox(height: 2.5),
            if (!isNullOrEmpty(data.userInfo?.subcaption))
              Text(data.userInfo!.subcaption!,
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                  style: context.theme.textTheme.labelMedium!.copyWith(fontWeight: FontWeight.normal)),
            if (!isNullOrEmpty(data.appName)) const SizedBox(height: 5),
            if (!isNullOrEmpty(data.appName))
              Text(
                data.appName!,
                style: context.theme.textTheme.labelMedium!
                    .copyWith(fontWeight: FontWeight.normal, color: context.theme.colorScheme.outline),
                overflow: TextOverflow.clip,
                maxLines: 1,
              ),
          ]),
        )
      ],
    );
  }
}
