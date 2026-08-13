import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// A single option in the [SyncTimeRangeDialog] time-range picker.
class SyncTimeRangeOption {
  const SyncTimeRangeOption({required this.label, required this.duration});

  /// Display label shown to the user (e.g. "1 Week").
  final String label;

  /// How far back from *now* the range extends.
  final Duration duration;
}

/// Default options shown by [showSyncTimeRangeDialog].
const List<SyncTimeRangeOption> defaultSyncTimeRangeOptions = [
  SyncTimeRangeOption(label: "1 Hour", duration: Duration(hours: 1)),
  SyncTimeRangeOption(label: "1 Day", duration: Duration(days: 1)),
  SyncTimeRangeOption(label: "1 Week", duration: Duration(days: 7)),
  SyncTimeRangeOption(label: "1 Month", duration: Duration(days: 30)),
  SyncTimeRangeOption(label: "6 Months", duration: Duration(days: 182)),
  SyncTimeRangeOption(label: "1 Year", duration: Duration(days: 365)),
];

/// Shows a dialog for selecting a time range to sync messages.
///
/// Returns the selected [DateTimeRange] with [DateTimeRange.start] set to
/// `now - option.duration` and [DateTimeRange.end] set to `now`, or `null`
/// if the user dismisses the dialog.
///
/// Pass [options] to replace the default list entirely.
Future<DateTimeRange?> showSyncTimeRangeDialog(
  BuildContext context, {
  List<SyncTimeRangeOption>? options,
}) {
  return showDialog<DateTimeRange>(
    context: context,
    builder: (context) => SyncTimeRangeDialog(options: options),
  );
}

/// A dialog presenting a list of time-range options for syncing messages.
///
/// Customise the presented options via [options]; defaults to
/// [defaultSyncTimeRangeOptions] when omitted.
class SyncTimeRangeDialog extends StatelessWidget {
  const SyncTimeRangeDialog({super.key, this.options});

  final List<SyncTimeRangeOption>? options;

  @override
  Widget build(BuildContext context) {
    final effectiveOptions = options ?? defaultSyncTimeRangeOptions;

    return AlertDialog(
      title: Text("How far back?", style: context.theme.textTheme.titleLarge),
      backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
      contentPadding: const EdgeInsets.symmetric(vertical: 8),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: effectiveOptions.map((option) {
            return ListTile(
              title: Text(option.label, style: context.theme.textTheme.bodyLarge),
              onTap: () {
                final now = DateTime.now().toUtc();
                Navigator.of(context).pop(
                  DateTimeRange(
                    start: now.subtract(option.duration),
                    end: now,
                  ),
                );
              },
            );
          }).toList(),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(
            "Cancel",
            style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary),
          ),
        ),
      ],
    );
  }
}
