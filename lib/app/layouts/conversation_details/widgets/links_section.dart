import 'dart:math';

import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/interactive/url_preview.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:get/get.dart';
import 'package:url_launcher/url_launcher.dart';

/// Widget that handles links section display with loading state
class LinksSection extends StatefulWidget {
  final Chat chat;

  const LinksSection({
    super.key,
    required this.chat,
  });

  @override
  State<LinksSection> createState() => _LinksSectionState();
}

class _LinksSectionState extends State<LinksSection> with ThemeHelpers {
  static const int _chunkSize = 20;
  int _displayCount = _chunkSize;
  List<Message> links = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    if (!kIsWeb) {
      _fetchLinks();
    } else {
      _isLoading = false;
    }
  }

  Future<void> _fetchLinks() async {
    if (kIsWeb || widget.chat.id == null) {
      setState(() {
        _isLoading = false;
      });
      return;
    }

    try {
      final query = (Database.messages.query(Message_.dateDeleted.isNull() &
              Message_.dbPayloadData.notNull() &
              Message_.balloonBundleId.contains("URLBalloonProvider"))
            ..link(Message_.chat, Chat_.id.equals(widget.chat.id!))
            ..order(Message_.dateCreated, flags: Order.descending))
          .build();
      final fetchedLinks = await query.findAsync();
      query.close();

      if (mounted) {
        setState(() {
          links = fetchedLinks;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
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
              "LINKS",
              style: context.theme.textTheme.bodyMedium!.copyWith(
                color: context.theme.colorScheme.outline,
              ),
            ),
          ),
        ),
        if (_isLoading)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 20.0, horizontal: 20.0),
              child: Center(
                child: buildProgressIndicator(context, size: 24),
              ),
            ),
          )
        else if (links.isEmpty)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 20.0, horizontal: 20.0),
              child: Center(
                child: Text(
                  "No links",
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
                      if (links[index].payloadData?.urlData?.firstOrNull == null) {
                        return const Text("Failed to load link!");
                      }
                      return Material(
                        color: context.theme.colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(20),
                        clipBehavior: Clip.antiAlias,
                        child: InkWell(
                          borderRadius: BorderRadius.circular(20),
                          onTap: () async {
                            final data = links[index].payloadData!.urlData!.first;
                            if ((data.url ?? data.originalUrl) == null) return;
                            await launchUrl(
                              Uri.parse((data.url ?? data.originalUrl)!),
                              mode: LaunchMode.externalApplication,
                            );
                          },
                          child: Center(
                            child: UrlPreview(
                              data: links[index].payloadData!.urlData!.first,
                            ),
                          ),
                        ),
                      );
                    },
                    itemCount: min(_displayCount, links.length),
                  ),
                ),
              )),
          if (_displayCount < links.length)
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
