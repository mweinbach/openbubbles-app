import 'dart:async';

import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:universal_io/io.dart';

class StickerHolder extends StatefulWidget {
  const StickerHolder({super.key, required this.stickerMessages, required this.controller});
  final Iterable<Message> stickerMessages;
  final ConversationViewController controller;

  @override
  State<StickerHolder> createState() => _StickerHolderState();
}

class _StickerHolderState extends State<StickerHolder> {
  Iterable<Message> get messages => widget.stickerMessages;
  ConversationViewController get controller => widget.controller;

  bool _visible = true;
  bool _dismissed = false;
  final Map<String, Attachment> _stickerPaths = {};
  int renderedStickers = 0;

  @override
  void initState() {
    super.initState();
    loadStickers();
  }

  Future<void> loadStickers() async {
    if (renderedStickers == messages.length) return;
    renderedStickers = messages.length;

    for (Message msg in messages) {
      for (Attachment attachment in msg.dbAttachments) {
        final pathName = attachment.path;
        if (_stickerPaths.containsKey(pathName)) continue;

        if (await FileSystemEntity.type(pathName) == FileSystemEntityType.notFound) {
          AttachmentDownloader.startDownload(attachment, onComplete: (_) async {
            if (!mounted) return;
            setState(() => _stickerPaths[pathName] = attachment);
            await checkImage(msg, attachment);
          });
        } else {
          if (mounted) setState(() => _stickerPaths[pathName] = attachment);
          await checkImage(msg, attachment);
        }
      }
    }
  }

  Future<void> checkImage(Message message, Attachment attachment) async {
    final bytes = await File(attachment.path).readAsBytes();
    final stickerData = message.attributedBody.firstOrNull?.runs
        .firstWhere((element) => element.attributes?.attachmentGuid == attachment.guid)
        .attributes
        ?.stickerData;
    controller.stickerData[message.guid!] = {
      attachment.guid!: (bytes, stickerData),
    };
    if (mounted) setState(() {});
  }

  @override
  void didUpdateWidget(covariant StickerHolder oldWidget) {
    super.didUpdateWidget(oldWidget);
    unawaited(loadStickers());
  }

  @override
  Widget build(BuildContext context) {
    final guids = messages.map((e) => e.guid!);
    final stickers = controller.stickerData.entries.where((element) => guids.contains(element.key)).map((e) => e.value);
    if ((stickers.isEmpty && _stickerPaths.isEmpty) || _dismissed) return const SizedBox.shrink();

    final data = stickers.map((e) => e.values).expand((element) => element);

    return Positioned(
        top: -20,
        left: -20,
        right: -20,
        bottom: -20,
        child: GestureDetector(
          onTap: () => setState(() => _visible = !_visible),
          onLongPress: () {
            HapticFeedback.mediumImpact();
            setState(() => _dismissed = true);
          },
          child: AnimatedOpacity(
            duration: const Duration(milliseconds: 150),
            opacity: _visible ? 1.0 : 0.25,
            child: Stack(
              children: data
                  .map(
                    (e) => Container(
                      alignment: FractionalOffset(e.$2?.normalizedX ?? .5, e.$2?.normalizedY ?? .5),
                      child: Transform.rotate(
                        angle: e.$2?.rotation ?? 0,
                        alignment: FractionalOffset(e.$2?.normalizedX ?? .5, e.$2?.normalizedY ?? .5),
                        child: Transform.scale(
                          scale: e.$2?.scale ?? 1,
                          child: Image.memory(
                            e.$1,
                            scale: .01,
                            gaplessPlayback: true,
                            cacheHeight: 200,
                            filterQuality: FilterQuality.none,
                          ),
                        ),
                      ),
                    ),
                  )
                  .toList(),
            ),
          ),
        ));
  }
}
