import 'dart:math';

import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/url_preview.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

/// Widget that handles locations section display
class LocationsSection extends StatefulWidget {
  final List<Attachment> locations;
  final bool isLoading;

  const LocationsSection({
    super.key,
    required this.locations,
    this.isLoading = false,
  });

  @override
  State<LocationsSection> createState() => _LocationsSectionState();
}

class _LocationsSectionState extends State<LocationsSection> {
  static const int _chunkSize = 10;
  int _displayCount = _chunkSize;

  @override
  void didUpdateWidget(LocationsSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.locations.length != widget.locations.length) {
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
              "LOCATIONS",
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
        else if (widget.locations.isEmpty)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 20.0, horizontal: 20.0),
              child: Center(
                child: Text(
                  "No locations",
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
                sliver: SliverToBoxAdapter(
                  child: MasonryGridView.count(
                    crossAxisCount: max(2, NavigationSvc.width(context) ~/ 200),
                    mainAxisSpacing: 10,
                    crossAxisSpacing: 10,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemBuilder: (context, index) {
                      if (AttachmentsSvc.getContent(widget.locations[index]) is! PlatformFile) {
                        return const Text("Failed to load location!");
                      }
                      return Material(
                        color: context.theme.colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(20),
                        clipBehavior: Clip.antiAlias,
                        child: InkWell(
                          borderRadius: BorderRadius.circular(20),
                          onTap: () async {
                            final attachment = widget.locations[index];
                            if (attachment.mimeType?.contains("location") ?? false) {
                              // Launch the location in maps
                              final location = attachment.transferName;
                              if (location != null) {
                                final uri = Uri.parse("https://maps.google.com/?q=$location");
                                await launchUrl(uri, mode: LaunchMode.externalApplication);
                              }
                            }
                          },
                          child: Center(
                            child: UrlPreview(
                              data: UrlPreviewData(
                                title:
                                    "Location from ${DateFormat.yMd().format(widget.locations[index].message.target!.dateCreated!)}",
                                siteName: "Tap to open",
                              ),
                              file: AttachmentsSvc.getContent(widget.locations[index]),
                            ),
                          ),
                        ),
                      );
                    },
                    itemCount: min(_displayCount, widget.locations.length),
                  ),
                ),
              )),
          if (_displayCount < widget.locations.length)
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
