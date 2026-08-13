import 'package:bluebubbles/app/components/custom/custom_cupertino_alert_dialog.dart';
import 'package:bluebubbles/helpers/types/constants.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart' hide CupertinoAlertDialog, CupertinoDialogAction;
import 'package:flutter/material.dart';

/// A single action button for use in [showBBDialog].
class BBDialogAction {
  final String text;
  final VoidCallback? onPressed;

  /// iOS skin only — renders the button text in red (destructive style).
  final bool isDestructive;

  /// iOS skin only — renders the button text bold (default/primary action).
  final bool isDefault;

  /// Material/Samsung skin only — overrides the button text color.
  final Color? color;

  const BBDialogAction({
    required this.text,
    this.onPressed,
    this.isDestructive = false,
    this.isDefault = false,
    this.color,
  });
}

/// Shows a skin-aware dialog.
///
/// On the iOS skin this renders a [CupertinoAlertDialog]; on Material and
/// Samsung skins it renders a standard [AlertDialog].
///
/// Supply either [content] (a Widget) or [body] (a plain string) for the
/// dialog body — [content] takes precedence if both are provided.
Future<T?> showBBDialog<T>({
  required BuildContext context,
  String? title,
  Widget? content,
  String? body,
  List<BBDialogAction> actions = const [],
  bool barrierDismissible = true,
}) {
  final skin = SettingsSvc.settings.skin.value;
  final bodyWidget = content ?? (body != null ? Text(body) : null);

  if (skin == Skins.iOS) {
    return showDialog<T>(
      context: context,
      barrierDismissible: barrierDismissible,
      useRootNavigator: true,
      builder: (ctx) => CupertinoTheme(
        // Bridge Material theme brightness into the Cupertino color system so
        // CupertinoDynamicColor.resolve picks the correct light/dark variants
        // for both the dialog background and the action-button backgrounds.
        data: CupertinoThemeData(brightness: Theme.of(ctx).brightness),
        child: CupertinoAlertDialog(
          backgroundColor: Theme.of(ctx).colorScheme.surfaceContainerHighest,
          title: title != null ? Text(title) : null,
          // Wrap in a transparent Material so that Material widgets (e.g.
          // TextField) embedded in custom content can find their ancestor.
          content: bodyWidget != null ? Material(type: MaterialType.transparency, child: bodyWidget) : null,
          actions: actions
              .map(
                (a) => CupertinoDialogAction(
                  isDestructiveAction: a.isDestructive,
                  isDefaultAction: a.isDefault,
                  onPressed: a.onPressed,
                  child: Text(a.text),
                ),
              )
              .toList(),
        ),
      ),
    );
  }

  // Material / Samsung
  return showDialog<T>(
    context: context,
    barrierDismissible: barrierDismissible,
    useRootNavigator: true,
    builder: (ctx) => AlertDialog(
      title: title != null ? Text(title, style: ctx.textTheme.titleLarge) : null,
      content: bodyWidget,
      backgroundColor: ctx.colorScheme.surfaceContainerHighest,
      actions: actions
          .map(
            (a) => TextButton(
              onPressed: a.onPressed,
              child: Text(
                a.text,
                style: ctx.textTheme.bodyLarge!.copyWith(
                  color: a.color ?? ctx.colorScheme.primary,
                ),
              ),
            ),
          )
          .toList(),
    ),
  );
}

/// Convenience wrapper around [showBBDialog] for simple yes/no confirmation dialogs.
///
/// Prefer this over the deprecated [areYouSure] helper.
Future<void> showAreYouSure(
  BuildContext context, {
  Widget? content,
  String? title = "Are you sure?",
  String? noText = "No",
  String? yesText = "Yes",
  Color? noColor,
  Color? yesColor,
  TextAlign textAlign = TextAlign.center,
  required Function onNo,
  required Function onYes,
}) {
  final wrappedContent = content != null ? DefaultTextStyle.merge(textAlign: textAlign, child: content) : null;
  return showBBDialog(
    context: context,
    title: title,
    content: wrappedContent,
    actions: [
      BBDialogAction(
        text: noText ?? "No",
        color: noColor,
        onPressed: () => onNo.call(),
      ),
      BBDialogAction(
        text: yesText ?? "Yes",
        color: yesColor,
        isDefault: true,
        onPressed: () => onYes.call(),
      ),
    ],
  );
}

// BuildContext extensions for convenient theme access inside this file
extension _ContextTheme on BuildContext {
  TextTheme get textTheme => Theme.of(this).textTheme;
  ColorScheme get colorScheme => Theme.of(this).colorScheme;
}
