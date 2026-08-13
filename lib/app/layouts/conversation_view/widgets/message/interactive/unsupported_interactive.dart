import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';

class UnsupportedInteractive extends StatefulWidget {
  const UnsupportedInteractive({
    super.key,
    required this.payloadData,
    required this.content,
    required this.balloonBundleId,
  });

  final iMessageAppData? payloadData;
  final dynamic content;
  final String? balloonBundleId;

  @override
  State<UnsupportedInteractive> createState() => _UnsupportedInteractiveState();
}

class _UnsupportedInteractiveState extends State<UnsupportedInteractive>
    with AutomaticKeepAliveClientMixin, ThemeHelpers {
  iMessageAppData? get data => widget.payloadData;
  dynamic get file => File(widget.content.path!);

  String getAppName() {
    final balloonBundleId = widget.balloonBundleId;
    final temp = balloonBundleIdMap[balloonBundleId?.split(":").first];
    String? name;
    if (temp is Map) {
      name = temp[balloonBundleId?.split(":").last];
    } else if (temp is String) {
      name = temp;
    }
    return name ?? "Unknown";
  }

  IconData getIcon() {
    final balloonBundleId = widget.balloonBundleId;
    final temp = balloonBundleIdIconMap[balloonBundleId?.split(":").first];
    IconData? icon;
    if (temp is Map) {
      icon = temp[balloonBundleId?.split(":").last];
    } else if (temp is IconData) {
      icon = temp;
    }
    return icon ?? (SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.square_grid_3x2 : Icons.apps);
  }

  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
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
        Padding(
          padding: const EdgeInsets.all(15.0),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Flexible(
                child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(
                    data?.appName ?? getAppName(),
                    style: context.theme.textTheme.bodyLarge!.apply(fontWeightDelta: 2),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (!isNullOrEmpty(data?.userInfo?.caption)) const SizedBox(height: 2.5),
                  if (!isNullOrEmpty(data?.userInfo?.caption))
                    Text(data!.userInfo!.caption!,
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                        style: context.theme.textTheme.labelMedium!.copyWith(fontWeight: FontWeight.normal)),
                  const SizedBox(height: 5),
                  Text(
                    "Unsupported interactive message",
                    style: context.theme.textTheme.labelMedium!
                        .copyWith(fontWeight: FontWeight.normal, color: context.theme.colorScheme.outline),
                    overflow: TextOverflow.clip,
                    maxLines: 2,
                  ),
                ]),
              ),
              Icon(getIcon(), color: context.theme.colorScheme.onSurfaceVariant, size: 48),
            ],
          ),
        ),
      ],
    );
  }
}
