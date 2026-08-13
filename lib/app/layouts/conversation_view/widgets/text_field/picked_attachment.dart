import 'dart:async';
import 'dart:typed_data';

import 'package:animations/animations.dart';
import 'package:bluebubbles/app/layouts/fullscreen_media/fullscreen_holder.dart';
import 'package:bluebubbles/helpers/ui/theme_helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:mime_type/mime_type.dart';
import 'package:universal_io/io.dart';

class PickedAttachment extends StatefulWidget {
  const PickedAttachment({
    super.key,
    required this.data,
    required this.controller,
    required this.onRemove,
    required this.pickedAttachmentIndex,
  });
  final PlatformFile data;
  final ConversationViewController? controller;
  final Function(PlatformFile) onRemove;
  final int pickedAttachmentIndex;

  @override
  State<PickedAttachment> createState() => _PickedAttachmentState();
}

class _PickedAttachmentState extends State<PickedAttachment> with AutomaticKeepAliveClientMixin, ThemeHelpers {
  Uint8List? imageBytes;
  String? imagePath;
  bool isLoading = true;
  bool isEmpty = false;
  final FocusNode _deleteFocusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    _deleteFocusNode.dispose();
    super.dispose();
  }

  void _removeAttachment() {
    if (widget.controller != null) {
      widget.controller!.pickedAttachments.removeAt(widget.pickedAttachmentIndex);
      final remaining =
          widget.controller!.pickedAttachments.where((e) => e.path != null).map((e) => e.path!).toList();
      unawaited(ChatsSvc.setChatTextFieldAttachments(widget.controller!.chat, remaining));
      // Don't request focus if attachment picker is open
      if (!widget.controller!.showAttachmentPicker.value) {
        widget.controller!.lastFocusedNode.requestFocus();
      }
    } else {
      widget.onRemove.call(widget.data);
    }
  }

  /// Wraps the delete button for D-pad use: left/right move between delete buttons, down
  /// returns to the text field, and the button is rebuilt with the focused state so it can
  /// recolor. The inner button keeps [_deleteFocusNode] so the select key activates it.
  Widget _focusableDelete(Widget Function(bool focused) builder) {
    return Focus(
      canRequestFocus: false,
      onKeyEvent: (node, event) {
        if (event is! KeyDownEvent) return KeyEventResult.ignored;
        if (event.logicalKey == LogicalKeyboardKey.arrowDown) {
          widget.controller?.lastFocusedNode.requestFocus();
          return KeyEventResult.handled;
        }
        // Move between delete buttons by their order under the shared parent node — more
        // reliable than directional geometry, which missed left moves.
        if (event.logicalKey == LogicalKeyboardKey.arrowLeft ||
            event.logicalKey == LogicalKeyboardKey.arrowRight) {
          final nodes = widget.controller?.pickedAttachmentsListNode.traversalDescendants.toList() ?? <FocusNode>[];
          final idx = nodes.indexOf(_deleteFocusNode);
          if (idx != -1) {
            final target = event.logicalKey == LogicalKeyboardKey.arrowLeft ? idx - 1 : idx + 1;
            if (target >= 0 && target < nodes.length) {
              nodes[target].requestFocus();
              return KeyEventResult.handled;
            }
          }
        }
        return KeyEventResult.ignored;
      },
      child: AnimatedBuilder(
        animation: _deleteFocusNode,
        builder: (context, _) => builder(_deleteFocusNode.hasFocus),
      ),
    );
  }

  Future<void> load() async {
    final file = widget.data;
    final mimeType = mime(widget.data.name) ?? "";
    if (mimeType.startsWith("video/") && Platform.isAndroid) {
      try {
        imageBytes = await AttachmentsSvc.getVideoThumbnail(file.path!, useCachedFile: false);
      } catch (ex) {
        imageBytes = FilesystemSvc.noVideoPreviewIcon;
      }
      setState(() {
        isLoading = false;
      });
    } else if (mimeType == "image/heic" ||
        mimeType == "image/heif" ||
        mimeType == "image/tif" ||
        mimeType == "image/tiff") {
      // Use ensureImageCompatibility to get a compatible file path
      try {
        final fakeAttachment = Attachment(
          transferName: file.path,
          mimeType: mimeType,
          metadata: widget.data.path == null ? null : {'source_path': widget.data.path},
        );
        imagePath = await AttachmentsSvc.ensureImageCompatibility(fakeAttachment);
        if (imagePath == null && file.bytes != null) {
          // Fallback to bytes if conversion returns null
          imageBytes = file.bytes;
        }
      } catch (ex) {
        // Fallback to bytes if conversion fails
        imageBytes = await file.getBytes();
      }
      setState(() {
        isLoading = false;
      });
    } else if (mimeType.startsWith("image/")) {
      imageBytes = await file.getBytes();
      setState(() {
        if (imageBytes == null || imageBytes!.isEmpty) {
          isEmpty = true;
        }
        isLoading = false;
      });
    } else {
      setState(() {
        isEmpty = true;
        isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Padding(
      padding: iOS ? const EdgeInsets.all(5) : const EdgeInsets.only(top: 15, left: 7.5, right: 7.5, bottom: 15),
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(20),
            ),
            constraints: BoxConstraints(
                maxWidth: isLoading
                    ? 0
                    : isEmpty
                        ? 100
                        : 200),
            clipBehavior: Clip.antiAlias,
            child: OpenContainer(
                tappable: false,
                openColor: Colors.black,
                closedColor: context.theme.colorScheme.surface,
                openBuilder: (_, closeContainer) {
                  // Use the full file path as transferName so getContent can locate
                  // the file on disk when bytes are not pre-loaded (e.g. gallery picker).
                  // Prefer state-cached bytes (imageBytes) over the raw widget bytes so
                  // that HEIC/TIFF files, which have already been decoded into imagePath,
                  // still have their fallback bytes available.
                  final fakeAttachment = Attachment(
                    transferName: widget.data.path ?? widget.data.name,
                    mimeType: mime(widget.data.name) ?? "",
                    bytes: imageBytes ?? widget.data.bytes,
                    metadata: widget.data.path == null ? null : {'source_path': widget.data.path},
                  );
                  return FullscreenMediaHolder(
                    attachment: fakeAttachment,
                    showInteractions: false,
                  );
                },
                closedBuilder: (_, openContainer) {
                  return InkWell(
                    // Don't take D-pad focus — only the delete button should be focusable.
                    canRequestFocus: false,
                    onTap: mime(widget.data.name)?.startsWith("image") ?? false ? openContainer : null,
                    child: Stack(
                      clipBehavior: Clip.none,
                      alignment: Alignment.topRight,
                      children: <Widget>[
                        if (!isEmpty && !isLoading) _buildImage(),
                        if (isEmpty)
                          Positioned.fill(
                            child: Container(
                              color: context.theme.colorScheme.surfaceContainerHighest,
                              alignment: Alignment.center,
                              child: Padding(
                                padding: const EdgeInsets.all(8.0),
                                child: Text(
                                  widget.data.name,
                                  maxLines: 3,
                                  textAlign: TextAlign.center,
                                ),
                              ),
                            ),
                          ),
                        if (!isLoading && iOS)
                          Positioned(
                            top: 5,
                            right: 5,
                            child: _focusableDelete(
                              (focused) => TextButton(
                                focusNode: _deleteFocusNode,
                                style: TextButton.styleFrom(
                                  backgroundColor: focused ? Colors.black : context.theme.colorScheme.outline,
                                  shape: const CircleBorder(),
                                  padding: const EdgeInsets.all(0),
                                  maximumSize: const Size(32, 32),
                                  minimumSize: const Size(32, 32),
                                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                ),
                                child: const Icon(
                                  CupertinoIcons.xmark,
                                  color: Colors.white,
                                  size: 18,
                                ),
                                onPressed: _removeAttachment,
                              ),
                            ),
                          ),
                      ],
                    ),
                  );
                }),
          ),
          if (!iOS)
            Positioned(
              top: -7,
              right: -7,
              child: _focusableDelete(
                (focused) => TextButton(
                  focusNode: _deleteFocusNode,
                  style: TextButton.styleFrom(
                    backgroundColor: focused ? Colors.black : context.theme.colorScheme.secondary,
                    shape: CircleBorder(side: BorderSide(color: context.theme.colorScheme.surfaceContainerHighest)),
                    padding: const EdgeInsets.all(0),
                    maximumSize: const Size(25, 25),
                    minimumSize: const Size(25, 25),
                    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  ),
                  child: Icon(
                    Icons.close,
                    color: context.theme.colorScheme.surface,
                    size: 18,
                  ),
                  onPressed: _removeAttachment,
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildImage() {
    // Use Image.file when we have a path (memory efficient)
    if (imagePath != null) {
      return Image.file(
        File(imagePath!),
        key: ValueKey(widget.data.path),
        gaplessPlayback: true,
        fit: iOS ? BoxFit.fitHeight : BoxFit.cover,
        height: iOS ? 150 : 75,
        width: iOS ? null : 75,
        cacheWidth: 300,
      );
    }

    // Fall back to Image.memory when we have bytes
    if (imageBytes != null) {
      return Image.memory(
        imageBytes!,
        key: ValueKey(widget.data.path),
        gaplessPlayback: true,
        fit: iOS ? BoxFit.fitHeight : BoxFit.cover,
        height: iOS ? 150 : 75,
        width: iOS ? null : 75,
        cacheWidth: 300,
      );
    }

    return const SizedBox.shrink();
  }

  @override
  bool get wantKeepAlive => true;
}
