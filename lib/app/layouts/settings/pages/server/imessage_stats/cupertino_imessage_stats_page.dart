import 'package:bluebubbles/app/layouts/settings/pages/server/imessage_stats/imessage_stats_helpers.dart';
import 'package:bluebubbles/app/layouts/settings/pages/server/server_management_panel.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class CupertinoIMessageStatsPage extends CustomStateful<ServerManagementPanelController> {
  const CupertinoIMessageStatsPage({super.key, required super.parentController});

  @override
  State<StatefulWidget> createState() => _CupertinoIMessageStatsPageState();
}

class _CupertinoIMessageStatsPageState
    extends CustomState<CupertinoIMessageStatsPage, void, ServerManagementPanelController>
    with IMessageStatsHelpersMixin {
  @override
  void initState() {
    super.initState();
    forceDelete = false;
  }

  Widget _buildLoadingState() {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 60),
      child: Center(
        child: CircularProgressIndicator(
          valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
        ),
      ),
    );
  }

  Widget _buildStatCard(StatItemConfig item, dynamic rawValue) {
    final count = formatCount(rawValue);
    return Container(
      margin: const EdgeInsets.all(5),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: tileColor,
        borderRadius: BorderRadius.circular(14),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.07),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: item.containerColor,
              borderRadius: BorderRadius.circular(9),
            ),
            child: Icon(item.iosIcon, color: Colors.white, size: 20),
          ),
          const SizedBox(height: 12),
          Text(
            count,
            style: context.theme.textTheme.headlineSmall!.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            item.label,
            style: context.theme.textTheme.bodySmall!.copyWith(
              color: context.theme.colorScheme.onSurface.withValues(alpha: 0.65),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMediaRow(StatItemConfig item, dynamic rawValue) {
    final count = formatCount(rawValue);
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SettingsTile(
          backgroundColor: tileColor,
          leading: SettingsLeadingIcon(
            iosIcon: item.iosIcon,
            materialIcon: item.materialIcon,
            containerColor: item.containerColor,
          ),
          title: item.label,
          trailing: Text(
            count,
            style: context.theme.textTheme.bodyLarge!.copyWith(
              color: context.theme.colorScheme.outline.withValues(alpha: 0.85),
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        if (item != IMessageStatsHelpersMixin.kStatItems.where((e) => e.isFullWidth).last) const SettingsDivider(),
      ],
    );
  }

  Widget _buildBody() {
    final activeStats = controller.getActiveStatsMap();
    final hasStats = activeStats.isNotEmpty;
    final isLoading = controller.isActiveStatsLoading();
    final error = controller.getActiveStatsError();

    if (isLoading && !hasStats) return _buildLoadingState();

    final gridItems = IMessageStatsHelpersMixin.kStatItems.where((e) => !e.isFullWidth).toList();
    final mediaItems = IMessageStatsHelpersMixin.kStatItems.where((e) => e.isFullWidth).toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14),
          child: Center(
            child: CupertinoSlidingSegmentedControl<IMessageStatsSource>(
              groupValue: controller.selectedStatsSource.value,
              thumbColor: context.theme.colorScheme.primary,
              children: {
                IMessageStatsSource.server: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                  child: Text(
                    "Server",
                    style: TextStyle(
                      color: controller.selectedStatsSource.value == IMessageStatsSource.server
                          ? context.theme.colorScheme.onPrimary
                          : null,
                    ),
                  ),
                ),
                IMessageStatsSource.local: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                  child: Text(
                    "Local DB",
                    style: TextStyle(
                      color: kIsWeb
                          ? context.theme.colorScheme.outline
                          : controller.selectedStatsSource.value == IMessageStatsSource.local
                              ? context.theme.colorScheme.onPrimary
                              : null,
                    ),
                  ),
                ),
              },
              onValueChanged: (value) {
                if (value == null) return;
                if (value == IMessageStatsSource.local && kIsWeb) return;
                controller.setStatsSource(value);
              },
            ),
          ),
        ),
        if (kIsWeb)
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 4),
            child: Text(
              "Local DB stats are unavailable on web builds.",
              style: context.theme.textTheme.bodySmall,
            ),
          ),
        if (error != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 4),
            child: Text(
              error,
              style: context.theme.textTheme.bodySmall!.copyWith(color: context.theme.colorScheme.error),
            ),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 4, 20, 0),
          child: Text(
            "Tip: Pull down to refresh these stats.",
            style: context.theme.textTheme.bodySmall!.copyWith(
              color: context.theme.colorScheme.outline,
            ),
          ),
        ),
        SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Totals"),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final itemWidth = (constraints.maxWidth - 10) / 2;
              return Wrap(
                spacing: 0,
                runSpacing: 0,
                children: gridItems.map((item) {
                  return SizedBox(
                    width: itemWidth,
                    child: _buildStatCard(item, activeStats[item.key]),
                  );
                }).toList(),
              );
            },
          ),
        ),
        SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Media"),
        SettingsSection(
          backgroundColor: tileColor,
          children: mediaItems.map((item) => _buildMediaRow(item, activeStats[item.key])).toList(),
        ),
        const SizedBox(height: 8),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return SettingsScaffold(
      title: "iMessage Stats",
      initialHeader: null,
      iosSubtitle: iosSubtitle,
      materialSubtitle: materialSubtitle,
      tileColor: tileColor,
      headerColor: headerColor,
      bodySlivers: [
        CupertinoSliverRefreshControl(
          onRefresh: () async {
            await controller.refreshSelectedStats();
          },
        ),
        SliverToBoxAdapter(
          child: Obx(() => _buildBody()),
        ),
      ],
    );
  }
}
