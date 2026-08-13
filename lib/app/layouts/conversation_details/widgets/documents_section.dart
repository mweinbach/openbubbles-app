import 'dart:math';

import 'package:bluebubbles/app/layouts/conversation_details/widgets/media_gallery_card.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// Widget that handles documents/files section display
class DocumentsSection extends StatefulWidget {
  final List<Attachment> docs;
  final bool isLoading;

  const DocumentsSection({
    super.key,
    required this.docs,
    this.isLoading = false,
  });

  @override
  State<DocumentsSection> createState() => _DocumentsSectionState();
}

class _DocumentsSectionState extends State<DocumentsSection> {
  static const int _chunkSize = 24;
  int _displayCount = _chunkSize;

  @override
  void didUpdateWidget(DocumentsSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.docs.length != widget.docs.length) {
      _displayCount = _chunkSize;
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
              "OTHER FILES",
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
        else if (widget.docs.isEmpty)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 20.0, horizontal: 20.0),
              child: Center(
                child: Text(
                  "No files",
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
                    childAspectRatio: 1.75,
                  ),
                  delegate: SliverChildBuilderDelegate(
                    (context, int index) {
                      return MediaGalleryCard(
                        attachment: widget.docs[index],
                      );
                    },
                    childCount: min(_displayCount, widget.docs.length),
                  ),
                ),
              )),
          if (_displayCount < widget.docs.length)
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
