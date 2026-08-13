import 'package:bluebubbles/app/layouts/settings/pages/scheduling/create_scheduled_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/scheduling/scheduled_messages_mixin.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class CupertinoScheduledMessagesPanel extends StatefulWidget {
  const CupertinoScheduledMessagesPanel({super.key});

  @override
  State<CupertinoScheduledMessagesPanel> createState() => _CupertinoScheduledMessagesPanelState();
}

class _CupertinoScheduledMessagesPanelState extends State<CupertinoScheduledMessagesPanel>
    with ThemeHelpers, ScheduledMessagesMixin {
  @override
  void initState() {
    super.initState();
    initScheduled();
  }

  Widget _buildStatsHeader(BuildContext context) {
    final pendingCount = oneTime.length;
    final recurringCount = recurring.length;
    final completedCount = oneTimeCompleted.length;
    final next = nextScheduled;

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 4),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: tileColor,
          borderRadius: BorderRadius.circular(10),
          boxShadow: [
            BoxShadow(
              color: tileColor.darkenAmount(0.1).withValues(alpha: 0.25),
              spreadRadius: 5,
              blurRadius: 7,
              offset: const Offset(0, 3),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(CupertinoIcons.calendar, size: 18),
                const SizedBox(width: 8),
                Text(
                  "Overview",
                  style: context.theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                _buildCountChip(context, "$pendingCount pending", context.theme.colorScheme.primary),
                const SizedBox(width: 8),
                _buildCountChip(context, "$recurringCount recurring", Colors.teal),
                const SizedBox(width: 8),
                _buildCountChip(context, "$completedCount completed", Colors.green),
              ],
            ),
            if (next != null) ...[
              const SizedBox(height: 8),
              Row(
                children: [
                  Icon(CupertinoIcons.clock, size: 13, color: context.theme.colorScheme.outline),
                  const SizedBox(width: 4),
                  Text(
                    "Next: ${buildFullDate(next)}",
                    style: context.theme.textTheme.labelSmall?.copyWith(
                      color: context.theme.colorScheme.outline,
                    ),
                  ),
                ],
              ),
            ] else if (scheduled.isNotEmpty) ...[
              const SizedBox(height: 8),
              Row(
                children: [
                  Icon(CupertinoIcons.clock, size: 13, color: context.theme.colorScheme.outline),
                  const SizedBox(width: 4),
                  Text(
                    "No upcoming messages",
                    style: context.theme.textTheme.labelSmall?.copyWith(
                      color: context.theme.colorScheme.outline,
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildCountChip(BuildContext context, String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: color.withValues(alpha: 0.12),
        border: Border.all(color: color.withValues(alpha: 0.5), width: 1),
      ),
      child: Text(
        label,
        style: TextStyle(fontSize: 11, color: color, fontWeight: FontWeight.w600),
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
      contentPadding: const EdgeInsets.symmetric(horizontal: 15, vertical: 4),
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
            CupertinoButton(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              minSize: 0,
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
              child: Icon(CupertinoIcons.pencil, size: 20, color: context.theme.colorScheme.primary),
            ),
          ],
          CupertinoButton(
            padding: const EdgeInsets.only(left: 4),
            minSize: 0,
            onPressed: () => deleteMessage(item),
            child: Icon(CupertinoIcons.trash, size: 20, color: context.theme.colorScheme.error),
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
          color: Colors.transparent,
          child: ListView.separated(
            padding: EdgeInsets.zero,
            physics: const NeverScrollableScrollPhysics(),
            shrinkWrap: true,
            findChildIndexCallback: (key) => findChildIndexByKey(items, key, (item) => item.id.toString()),
            itemCount: items.length,
            separatorBuilder: (context, _) => const Divider(height: 1, indent: 60),
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
          child: Icon(CupertinoIcons.add, color: context.theme.colorScheme.onPrimary, size: 25),
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
          CupertinoButton(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            onPressed: () {
              fetching.value = true;
              scheduled.clear();
              getExistingMessages();
            },
            child: Icon(CupertinoIcons.arrow_counterclockwise, color: context.theme.colorScheme.onSurface),
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
                        Icon(
                          fetching.value == null
                              ? CupertinoIcons.exclamationmark_circle
                              : fetching.value == false
                                  ? CupertinoIcons.calendar_badge_minus
                                  : CupertinoIcons.clock,
                          size: 48,
                          color: context.theme.colorScheme.outline,
                        ),
                        const SizedBox(height: 12),
                        Text(
                          fetching.value == null
                              ? "Something went wrong!"
                              : fetching.value == false
                                  ? "No scheduled messages"
                                  : "Loading scheduled messages...",
                          style: context.theme.textTheme.labelLarge,
                        ),
                        if (fetching.value == true) ...[
                          const SizedBox(height: 12),
                          const CupertinoActivityIndicator(),
                        ],
                      ],
                    ),
                  ),
                ),

              // Stats header
              if (scheduled.isNotEmpty) _buildStatsHeader(context),

              // One-time pending
              if (oneTime.isNotEmpty)
                SettingsHeader(
                  iosSubtitle: iosSubtitle,
                  materialSubtitle: materialSubtitle,
                  text: "One-Time Messages",
                ),
              if (oneTime.isNotEmpty)
                _buildSection(
                  items: oneTime,
                  iconBuilder: (_) => CupertinoIcons.calendar,
                  iconColorBuilder: (_) => context.theme.colorScheme.primary,
                  subtitleBuilder: (item) => buildFullDate(item.scheduledFor),
                  isCompleted: false,
                ),

              // Recurring
              if (recurring.isNotEmpty)
                SettingsHeader(
                  iosSubtitle: iosSubtitle,
                  materialSubtitle: materialSubtitle,
                  text: "Recurring Messages",
                ),
              if (recurring.isNotEmpty)
                _buildSection(
                  items: recurring,
                  iconBuilder: (_) => CupertinoIcons.arrow_clockwise,
                  iconColorBuilder: (_) => Colors.teal,
                  subtitleBuilder: (item) =>
                      "Every ${item.schedule.interval} ${frequencyToText[item.schedule.intervalType]}(s) · ${buildFullDate(item.scheduledFor)}",
                  isCompleted: false,
                ),

              // Completed
              if (oneTimeCompleted.isNotEmpty)
                SettingsHeader(
                  iosSubtitle: iosSubtitle,
                  materialSubtitle: materialSubtitle,
                  text: "Completed Messages",
                ),
              if (oneTimeCompleted.isNotEmpty)
                _buildSection(
                  items: oneTimeCompleted,
                  iconBuilder: (item) =>
                      item.status == "error" ? CupertinoIcons.exclamationmark_circle : CupertinoIcons.checkmark_circle,
                  iconColorBuilder: (item) => item.status == "error" ? context.theme.colorScheme.error : Colors.green,
                  subtitleBuilder: (item) {
                    if (item.status == "error") return item.error ?? "Failed to send";
                    return "Sent "
                        "${item.sentAt != null ? " · ${buildFullDate(item.sentAt!)}" : ""}";
                  },
                  isCompleted: true,
                ),
            ]),
          ),
        ],
      );
    });
  }
}
