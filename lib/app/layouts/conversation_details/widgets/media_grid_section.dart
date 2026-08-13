import 'dart:math';

import 'package:bluebubbles/app/layouts/conversation_details/widgets/media_gallery_card.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// Widget that handles media grid display with selection functionality
class MediaGridSection extends StatefulWidget {
  final List<Attachment> media;
  final RxList<String> selected;
  final bool isLoading;

  const MediaGridSection({
    super.key,
    required this.media,
    required this.selected,
    required this.isLoading,
  });

  @override
  State<MediaGridSection> createState() => _MediaGridSectionState();
}

class _MediaGridSectionState extends State<MediaGridSection> with ThemeHelpers {
  static const int _chunkSize = 24;
  int _displayCount = _chunkSize;

  @override
  void didUpdateWidget(MediaGridSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.media.length != widget.media.length) {
      _displayCount = _chunkSize;
    }
    // Rebuild when isLoading changes
    if (oldWidget.isLoading != widget.isLoading) {
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    if (kIsWeb) {
      return const SliverToBoxAdapter(child: SizedBox.shrink());
    }

    return SliverMainAxisGroup(
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.only(top: 20, bottom: 5, left: 20),
          sliver: SliverToBoxAdapter(
            child: Text(
              "IMAGES & VIDEOS",
              style: context.theme.textTheme.bodyMedium!.copyWith(
                color: context.theme.colorScheme.outline,
              ),
            ),
          ),
        ),
        if (widget.isLoading)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 20.0, horizontal: 20.0),
              child: Center(
                child: buildProgressIndicator(context, size: 24),
              ),
            ),
          )
        else if (widget.media.isEmpty)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 20.0, horizontal: 20.0),
              child: Center(
                child: Text(
                  "No images or videos",
                  style: context.theme.textTheme.bodyMedium!.copyWith(
                    color: context.theme.colorScheme.outline,
                  ),
                ),
              ),
            ),
          )
        else ...[
          Obx(() => SliverPadding(
                padding: EdgeInsets.only(
                  left: SettingsSvc.settings.skin.value == Skins.iOS ? 20 : 10,
                  right: SettingsSvc.settings.skin.value == Skins.iOS ? 20 : 10,
                  top: 10,
                  bottom: 10,
                ),
                sliver: SliverGrid(
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: max(2, NavigationSvc.width(context) ~/ 200),
                    mainAxisSpacing: 10,
                    crossAxisSpacing: 10,
                  ),
                  delegate: SliverChildBuilderDelegate(
                    (context, int index) {
                      return Obx(() => AnimatedContainer(
                            duration: const Duration(milliseconds: 250),
                            margin: EdgeInsets.all(
                              widget.selected.contains(widget.media[index].guid) ? 10 : 0,
                            ),
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(20),
                            ),
                            clipBehavior: Clip.antiAlias,
                            child: GestureDetector(
                              onTap: widget.selected.isNotEmpty
                                  ? () {
                                      if (widget.selected.contains(widget.media[index].guid)) {
                                        widget.selected.remove(widget.media[index].guid!);
                                      } else {
                                        widget.selected.add(widget.media[index].guid!);
                                      }
                                    }
                                  : null,
                              onLongPress: () {
                                if (widget.selected.contains(widget.media[index].guid)) {
                                  widget.selected.remove(widget.media[index].guid!);
                                } else {
                                  widget.selected.add(widget.media[index].guid!);
                                }
                              },
                              child: AbsorbPointer(
                                absorbing: widget.selected.isNotEmpty,
                                child: Stack(
                                  alignment: Alignment.center,
                                  children: [
                                    MediaGalleryCard(
                                      attachment: widget.media[index],
                                    ),
                                    if (widget.selected.contains(widget.media[index].guid))
                                      Container(
                                        decoration: BoxDecoration(
                                          shape: BoxShape.circle,
                                          color: context.theme.colorScheme.primary,
                                        ),
                                        child: Padding(
                                          padding: const EdgeInsets.all(5.0),
                                          child: Icon(
                                            iOS ? CupertinoIcons.check_mark : Icons.check,
                                            color: context.theme.colorScheme.onPrimary,
                                            size: 18,
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                              ),
                            ),
                          ));
                    },
                    childCount: min(_displayCount, widget.media.length),
                  ),
                ),
              )),
          if (_displayCount < widget.media.length)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Center(
                  child: TextButton(
                    onPressed: () => setState(() => _displayCount += _chunkSize),
                    child: const Text("Show more"),
                  ),
                ),
              ),
            ),
        ],
      ],
    );
  }
}
