import 'package:bluebubbles/helpers/ui/theme_helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// BlueBubbles base page wrapper that handles system UI overlay styling
///
/// Provides consistent status bar and navigation bar styling across the app.
class BBAnnotatedRegion extends StatelessWidget {
  /// The child widget to wrap
  final Widget child;

  /// Custom status bar icon brightness (defaults to theme-based)
  final Brightness? statusBarIconBrightness;

  /// Custom navigation bar icon brightness (defaults to theme-based)
  final Brightness? systemNavigationBarIconBrightness;

  /// Custom navigation bar color (defaults to theme background or transparent in immersive mode)
  final Color? systemNavigationBarColor;

  /// Custom status bar color (defaults to transparent)
  final Color? statusBarColor;

  const BBAnnotatedRegion({
    super.key,
    required this.child,
    this.statusBarIconBrightness,
    this.systemNavigationBarIconBrightness,
    this.systemNavigationBarColor,
    this.statusBarColor,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final brightness = colorScheme.brightness;

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle(
        // Default: transparent only in immersive mode, otherwise the theme background. The hard
        // -coded transparent default made immersive mode have no visible effect on app pages.
        systemNavigationBarColor: systemNavigationBarColor ??
            (SettingsSvc.settings.immersiveMode.value ? Colors.transparent : colorScheme.background),
        systemNavigationBarIconBrightness: systemNavigationBarIconBrightness ?? brightness.opposite,
        statusBarColor: statusBarColor ?? Colors.transparent,
        statusBarIconBrightness: statusBarIconBrightness ?? brightness.opposite,
      ),
      child: child,
    );
  }
}
