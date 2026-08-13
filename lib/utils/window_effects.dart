import 'dart:io';

import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter_acrylic/flutter_acrylic.dart';

class WindowsVersionRange {
  final int? min;
  final int? max;

  const WindowsVersionRange(this.min, this.max);
}

class WindowEffectOpacity {
  final double dark;
  final double light;

  const WindowEffectOpacity(this.dark, this.light);
}

enum EffectDependencies { brightness, color }

class WindowEffects {
  static final _effects = [
    WindowEffect.tabbed,
    WindowEffect.mica,
    WindowEffect.aero,
    WindowEffect.acrylic,
    WindowEffect.transparent,
    WindowEffect.disabled
  ];

  static final Map<WindowEffect, WindowsVersionRange> _versions = {
    WindowEffect.tabbed: const WindowsVersionRange(22523, null),
    WindowEffect.mica: const WindowsVersionRange(22000, null),
    WindowEffect.aero: const WindowsVersionRange(0, 22523),
    WindowEffect.acrylic: const WindowsVersionRange(17134, null),
    WindowEffect.transparent: const WindowsVersionRange(0, null),
    WindowEffect.disabled: const WindowsVersionRange(0, null),
  };

  static List<WindowEffect> get effects => _effects.where((effect) {
        int version = parsedWindowsVersion();
        return version >= _versions[effect]!.min! &&
            (_versions[effect]!.max == null || (version <= _versions[effect]!.max!));
      }).toList();

  static final _descriptions = {
    WindowEffect.tabbed: "Tabbed is a Mica-like material that incorporates theme and desktop wallpaper, but is more "
        "sensitive to desktop wallpaper color. Works only on later Windows 11 versions (build 22523 or higher).",
    WindowEffect.mica: "Mica is an opaque, dynamic material that incorporates theme and desktop wallpaper to paint "
        "the background of long-lived windows. Works only on Windows 11 or greater (build 22000 or higher).",
    WindowEffect.aero: "Aero glass effect. Windows Vista & Windows 7 like glossy blur effect.",
    WindowEffect.acrylic: "Acrylic is a type of brush that creates a translucent texture. You can apply acrylic to "
        "app surfaces to add depth and help establish a visual hierarchy. Works only on Windows 10 version 1803 or "
        "higher (build 17134 or higher).",
    WindowEffect.transparent: "Transparent window background.",
    WindowEffect.disabled: "Default window background.",
  };

  static final Map<WindowEffect, List<EffectDependencies>> _dependencies = {
    WindowEffect.tabbed: [EffectDependencies.brightness],
    WindowEffect.mica: [EffectDependencies.brightness],
    WindowEffect.aero: [EffectDependencies.color],
    WindowEffect.acrylic: [EffectDependencies.color],
    WindowEffect.transparent: [EffectDependencies.color],
    WindowEffect.disabled: [],
  };

  // Map from window effect to opacity in <dark theme, light theme>
  static final Map<WindowEffect, WindowEffectOpacity> _opacities = {
    WindowEffect.tabbed: const WindowEffectOpacity(0, 0),
    WindowEffect.mica: const WindowEffectOpacity(0, 0),
    WindowEffect.aero: const WindowEffectOpacity(0.6, 0.75),
    WindowEffect.acrylic: const WindowEffectOpacity(0, 0.6),
    WindowEffect.transparent: const WindowEffectOpacity(0.7, 0.7),
    WindowEffect.disabled: const WindowEffectOpacity(1, 1),
  };

  static Map<WindowEffect, String> get descriptions {
    return Map.fromEntries(effects.map((effect) => MapEntry(effect, _descriptions[effect] ?? "")));
  }

  static double defaultOpacity({required bool dark}) {
    WindowEffect effect = SettingsSvc.settings.windowEffect.value;
    return dark ? _opacities[effect]!.dark : _opacities[effect]!.light;
  }

  static bool dependsOnColor() {
    WindowEffect effect = SettingsSvc.settings.windowEffect.value;
    return _dependencies[effect]!.contains(EffectDependencies.color);
  }

  static bool isDark({required Color color}) {
    return color.computeLuminance() <= 0.5;
  }

  static Future<void> setEffect({required Color color}) async {
    if (!kIsDesktop || !Platform.isWindows) return;
    WindowEffect effect = SettingsSvc.settings.windowEffect.value;
    if (!effects.contains(effect)) SettingsSvc.settings.windowEffect.value = WindowEffect.disabled;

    bool supportsTransparentAcrylic = parsedWindowsVersion() >= 22000;
    bool addOpacity = SettingsSvc.settings.windowEffect.value == WindowEffect.acrylic && !supportsTransparentAcrylic;

    // withOpacity uses withAlpha((255.0 * opacity).round());
    // so, the minimum nonzero alpha can be made with opacity 1 / 255
    double _extra = 1 / 255;
    bool _dark = true;

    if (_dependencies[effect]?.contains(EffectDependencies.brightness) ?? false) {
      _dark = isDark(color: color);
    }
    await Window.setEffect(effect: effect, color: color.withValues(alpha: addOpacity ? _extra : 0), dark: _dark);
    await SettingsSvc.settings.saveOneAsync('windowEffect');
  }
}

int parsedWindowsVersion() {
  String raw = Platform.operatingSystemVersion;
  return int.tryParse(raw.substring(raw.indexOf("Build") + 6, raw.length - 1)) ?? 0;
}
