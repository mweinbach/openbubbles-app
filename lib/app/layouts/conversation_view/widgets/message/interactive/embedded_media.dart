import 'package:bluebubbles/app/state/message_state.dart';
import 'package:bluebubbles/app/components/circle_progress_bar.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/attachment/other_file.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/attachment/video_player.dart';
import 'package:bluebubbles/app/state/message_state_scope.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:path/path.dart' as p;
import 'package:universal_io/io.dart';

class _DownloadProgress {
  final int current;
  final int total;
  const _DownloadProgress(this.current, this.total);
}

class EmbeddedMedia extends StatefulWidget {
  const EmbeddedMedia({
    super.key,
  });

  @override
  State<EmbeddedMedia> createState() => _EmbeddedMediaState();
}

class _EmbeddedMediaState extends State<EmbeddedMedia> with AutomaticKeepAliveClientMixin, ThemeHelpers {
  late MessageState _ms;
  MessageState get controller => _ms;
  Worker? _refreshWorker;
  Message get message => controller.message;

  dynamic content;

  @override
  void initState() {
    super.initState();
    _ms = MessageStateScope.readStateOnce(context);
    _refreshWorker = ever(_ms.embeddedMediaRefreshKey, (_) {
      if (File(message.interactiveMediaPath!).existsSync()) {
        File(message.interactiveMediaPath!).deleteSync();
        content = null;
        setState(() {});
        getContent();
      }
    });
    getContent();
  }

  @override
  void dispose() {
    _refreshWorker?.dispose();
    super.dispose();
  }

  void getContent() async {
    final path = message.interactiveMediaPath!;
    if (await File(path).exists()) {
      final bytes = await File(path).readAsBytes();
      content = PlatformFile(
        name: p.basename(path),
        path: path,
        size: bytes.length,
        bytes: bytes,
      );
      setState(() {});
    } else {
      content = Rx<_DownloadProgress>(const _DownloadProgress(0, 0));
      setState(() {});
      HttpSvc.message.downloadEmbeddedMedia(message.guid!, onReceiveProgress: (current, total) {
        if (content is Rx) {
          (content as Rx<_DownloadProgress>).value = _DownloadProgress(current, total);
        }
      }).then((response) async {
        await File(path).create(recursive: true);
        await File(path).writeAsBytes(response.data);
        content = PlatformFile(
          name: p.basename(path),
          path: path,
          size: response.data.length,
          bytes: response.data,
        );
        setState(() {});
      }).catchError((err) {
        content = "failed";
        setState(() {});
      });
    }
  }

  String getAppName() {
    final balloonBundleId = message.balloonBundleId;
    final temp = balloonBundleIdMap[balloonBundleId?.split(":").first];
    String? name;
    if (temp is Map) {
      name = temp[balloonBundleId?.split(":").last];
    } else if (temp is String) {
      name = temp;
    }
    return name ?? "Unknown";
  }

  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (content is PlatformFile && content.bytes != null && content.name.endsWith(".png"))
          Image.memory(
            content.bytes!,
            gaplessPlayback: true,
            filterQuality: FilterQuality.none,
            errorBuilder: (context, object, stacktrace) => Center(
              heightFactor: 1,
              child: Text("Failed to display image", style: context.theme.textTheme.bodyLarge),
            ),
          ),
        if (content is PlatformFile &&
            content.bytes != null &&
            content.name.toLowerCase().endsWith(".mov") &&
            !kIsDesktop)
          VideoPlayer(
            file: content,
            attachment: Attachment(
              guid: message.guid,
            ),
            controller: controller.cvController,
            isFromMe: message.isFromMe!,
          ),
        if (content is PlatformFile &&
            content.bytes != null &&
            content.name.toLowerCase().endsWith(".mov") &&
            kIsDesktop)
          OtherFile(
            attachment: Attachment(
              guid: message.guid,
            ),
            file: content,
          ),
        if (content is! PlatformFile)
          InkWell(
            onTap: content is String
                ? () {
                    getContent();
                  }
                : null,
            child: Padding(
              padding: const EdgeInsets.all(15.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Flexible(
                    child:
                        Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(
                        getAppName(),
                        style: context.theme.textTheme.bodyLarge!.apply(fontWeightDelta: 2),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 5),
                      Text(
                        content is Rx ? "Downloading media..." : "Failed to load media!",
                        style: context.theme.textTheme.labelMedium!
                            .copyWith(fontWeight: FontWeight.normal, color: context.theme.colorScheme.outline),
                        overflow: TextOverflow.clip,
                        maxLines: 2,
                      ),
                    ]),
                  ),
                  if (content is Rx<_DownloadProgress>)
                    SizedBox(
                      height: 40,
                      width: 40,
                      child: Center(
                        child: Obx(() => CircleProgressBar(
                              value: content.value.total > 0 ? content.value.current / content.value.total : 0,
                              backgroundColor: context.theme.colorScheme.outline,
                              foregroundColor: context.theme.colorScheme.onSurfaceVariant,
                            )),
                      ),
                    ),
                  if (content is String)
                    SizedBox(
                      height: 40,
                      width: 40,
                      child: Center(
                        child: Icon(iOS ? CupertinoIcons.arrow_clockwise : Icons.refresh, size: 30),
                      ),
                    ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}
