import 'package:bluebubbles/app/layouts/settings/pages/scheduling/create_scheduled_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/scheduling/scheduled_messages_mixin.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class SamsungScheduledMessagesPanel extends StatefulWidget {
  const SamsungScheduledMessagesPanel({super.key});

  @override
  State<SamsungScheduledMessagesPanel> createState() => _SamsungScheduledMessagesPanelState();
}

class _SamsungScheduledMessagesPanelState extends State<SamsungScheduledMessagesPanel>
    with ThemeHelpers, ScheduledMessagesMixin {
  @override
  void initState() {
    super.initState();
    initScheduled();
  }

  Widget _buildSamsungHeader(BuildContext context, String text) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 16, 6),
      child: Text(
        text,
        style: context.theme.textTheme.titleSmall?.copyWith(
          fontWeight: FontWeight.w700,
          color: context.theme.colorScheme.primary,
        ),
      ),
    );
  }

  Widget _buildCountBadge(BuildContext context, String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color, width: 1.5),
        color: color.withValues(alpha: 0.08),
      ),
      child: Text(
        label,
        style: TextStyle(fontSize: 12, color: color, fontWeight: FontWeight.w600),
      ),
    );
  }

  Widget _buildStatsHeader(BuildContext context) {
    final pendingCount = oneTime.length;
    final recurringCount = recurring.length;
    final completedCount = oneTimeCompleted.length;
    final next = nextScheduled;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
      child: Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: tileColor,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: context.theme.colorScheme.outline.withValues(alpha: 0.3), width: 1),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.bar_chart_rounded, size: 20, color: context.theme.colorScheme.primary),
                const SizedBox(width: 8),
                Text(
                  "Overview",
                  style: context.theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _buildCountBadge(context, "$pendingCount pending", context.theme.colorScheme.primary),
                _buildCountBadge(context, "$recurringCount recurring", Colors.teal),
                _buildCountBadge(context, "$completedCount completed", Colors.green),
              ],
            ),
            if (next != null) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: context.theme.colorScheme.outline.withValues(alpha: 0.07),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.access_time_outlined, size: 14, color: context.theme.colorScheme.outline),
                    const SizedBox(width: 6),
                    Text(
                      "Next: ${buildFullDate(next)}",
                      style: context.theme.textTheme.labelSmall?.copyWith(
                        color: context.theme.colorScheme.outline,
                      ),
                    ),
                  ],
                ),
              ),
            ] else if (scheduled.isNotEmpty) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: context.theme.colorScheme.outline.withValues(alpha: 0.07),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.access_time_outlined, size: 14, color: context.theme.colorScheme.outline),
                    const SizedBox(width: 6),
                    Text(
                      "No upcoming messages",
                      style: context.theme.textTheme.labelSmall?.copyWith(
                        color: context.theme.colorScheme.outline,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildMessageItem({
    required BuildContext context,
    required ScheduledMessage item,
    required IconData icon,
    required Color iconColor,
    required String subtitle,
    required bool isCompleted,
  }) {
    final chat = ChatsSvc.findChatByGuid(item.payload.chatGuid);
    final chatName = chat?.getTitle() ?? item.payload.chatGuid;

    return ListTile(
      key: ValueKey(item.id.toString()),
      mouseCursor: MouseCursor.defer,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      leading: SettingsLeadingIcon(
        iosIcon: icon,
        materialIcon: icon,
        containerColor: iconColor,
      ),
      title: Text(
        item.payload.message,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: context.theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
      ),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            "→ $chatName",
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: context.theme.textTheme.labelSmall?.copyWith(
              color: context.theme.colorScheme.outline,
            ),
          ),
          Text(
            subtitle,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: context.theme.textTheme.labelSmall?.copyWith(
              color: context.theme.colorScheme.outline,
            ),
          ),
        ],
      ),
      isThreeLine: true,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (!isCompleted) ...[
            IconButton(
              icon: Icon(Icons.edit_outlined, size: 20, color: context.theme.colorScheme.primary),
              onPressed: () async {
                final result = await NavigationSvc.pushSettings(
                  context,
                  CreateScheduledMessage(existing: item),
                );
                if (result is ScheduledMessage) {
                  final idx = scheduled.indexWhere((e) => e.id == item.id);
                  scheduled[idx] = result;
                }
              },
            ),
          ],
          IconButton(
            icon: Icon(Icons.delete_outlined, size: 20, color: context.theme.colorScheme.error),
            onPressed: () => deleteMessage(item),
          ),
        ],
      ),
    );
  }

  Widget _buildSection({
    required List<ScheduledMessage> items,
    required IconData Function(ScheduledMessage) iconBuilder,
    required Color Function(ScheduledMessage) iconColorBuilder,
    required String Function(ScheduledMessage) subtitleBuilder,
    required bool isCompleted,
  }) {
    return SettingsSection(
      backgroundColor: tileColor,
      children: [
        Material(
          color: const Color.from(alpha: 0, red: 0, green: 0, blue: 0),
          child: ListView.separated(
            padding: EdgeInsets.zero,
            physics: const NeverScrollableScrollPhysics(),
            shrinkWrap: true,
            findChildIndexCallback: (key) => findChildIndexByKey(items, key, (item) => item.id.toString()),
            itemCount: items.length,
            separatorBuilder: (context, _) => const Divider(height: 1, indent: 72),
            itemBuilder: (context, index) {
              final item = items[index];
              return _buildMessageItem(
                context: context,
                item: item,
                icon: iconBuilder(item),
                iconColor: iconColorBuilder(item),
                subtitle: subtitleBuilder(item),
                isCompleted: isCompleted,
              );
            },
          ),
        ),
      ],
    );
  }

  Color _iconColorForList(ScheduledMessage item, BuildContext context, Color defaultColor) {
    if (item.status == "error") return context.theme.colorScheme.error;
    return defaultColor;
  }

  IconData _iconForList(ScheduledMessage item) {
    if (item.status == "error") return Icons.error_outline;
    return Icons.check_circle_outline;
  }

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      return SettingsScaffold(
        title: "Scheduled Messages",
        initialHeader: null,
        iosSubtitle: iosSubtitle,
        materialSubtitle: materialSubtitle,
        tileColor: tileColor,
        headerColor: headerColor,
        fab: FloatingActionButton(
          backgroundColor: context.theme.colorScheme.primary,
          shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(16))),
          child: Icon(Icons.add, color: context.theme.colorScheme.onPrimary, size: 25),
          onPressed: () async {
            final result = await NavigationSvc.pushSettings(
              context,
              const CreateScheduledMessage(),
            );
            if (result is ScheduledMessage) {
              scheduled.add(result);
            }
          },
        ),
        actions: [
          IconButton(
            icon: Icon(Icons.refresh, color: context.theme.colorScheme.onSurface),
            onPressed: () {
              fetching.value = true;
              scheduled.clear();
              getExistingMessages();
            },
          ),
        ],
        bodySlivers: [
          SliverList(
            delegate: SliverChildListDelegate([
              // Empty / loading states
              if (fetching.value == null || fetching.value == true || (fetching.value == false && scheduled.isEmpty))
                Center(
                  child: Padding(
                    padding: const EdgeInsets.only(top: 100),
                    child: Column(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(20),
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: context.theme.colorScheme.outline.withValues(alpha: 0.1),
                            border: Border.all(
                              color: context.theme.colorScheme.outline.withValues(alpha: 0.3),
                              width: 1.5,
                            ),
                          ),
                          child: Icon(
                            fetching.value == null
                                ? Icons.error_outline
                                : fetching.value == false
                                    ? Icons.event_busy_outlined
                                    : Icons.hourglass_empty_outlined,
                            size: 40,
                            color: context.theme.colorScheme.outline,
                          ),
                        ),
                        const SizedBox(height: 16),
                        Text(
                          fetching.value == null
                              ? "Something went wrong!"
                              : fetching.value == false
                                  ? "No scheduled messages"
                                  : "Loading scheduled messages...",
                          style: context.theme.textTheme.labelLarge,
                        ),
                        if (fetching.value == true) ...[
                          const SizedBox(height: 16),
                          CircularProgressIndicator(
                            color: context.theme.colorScheme.primary,
                            strokeWidth: 2,
                          ),
                        ],
                      ],
                    ),
                  ),
                ),

              // Stats header
              if (scheduled.isNotEmpty) _buildStatsHeader(context),

              // One-time pending
              if (oneTime.isNotEmpty) _buildSamsungHeader(context, "One-Time Messages"),
              if (oneTime.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: _buildSection(
                    items: oneTime,
                    iconBuilder: (_) => Icons.schedule,
                    iconColorBuilder: (_) => context.theme.colorScheme.primary,
                    subtitleBuilder: (item) => buildFullDate(item.scheduledFor),
                    isCompleted: false,
                  ),
                ),

              // Recurring
              if (recurring.isNotEmpty) _buildSamsungHeader(context, "Recurring Messages"),
              if (recurring.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: _buildSection(
                    items: recurring,
                    iconBuilder: (_) => Icons.repeat,
                    iconColorBuilder: (_) => Colors.teal,
                    subtitleBuilder: (item) =>
                        "Every ${item.schedule.interval} ${frequencyToText[item.schedule.intervalType]}(s) · ${buildFullDate(item.scheduledFor)}",
                    isCompleted: false,
                  ),
                ),

              // Completed
              if (oneTimeCompleted.isNotEmpty) _buildSamsungHeader(context, "Completed Messages"),
              if (oneTimeCompleted.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: _buildSection(
                    items: oneTimeCompleted,
                    iconBuilder: _iconForList,
                    iconColorBuilder: (item) => _iconColorForList(item, context, Colors.green),
                    subtitleBuilder: (item) {
                      if (item.status == "error") return item.error ?? "Failed to send";
                      return "Sent "
                          "${item.sentAt != null ? " · ${buildFullDate(item.sentAt!)}" : ""}";
                    },
                    isCompleted: true,
                  ),
                ),
            ]),
          ),
        ],
      );
    });
  }
}
