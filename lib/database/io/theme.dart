import 'dart:convert';
import 'dart:core';

import 'package:bluebubbles/helpers/ui/theme_helpers.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/generated/objectbox.g.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flex_color_scheme/flex_color_scheme.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

// (needed when generating objectbox model code)
// ignore: unnecessary_import
import 'package:objectbox/objectbox.dart';

@Entity()
class ThemeStruct {
  int? id;
  @Unique()
  String name;
  bool gradientBg = false;
  String googleFont;
  ThemeData data;

  String get dbThemeData {
    final map = toMap()['data'];
    return jsonEncode(map);
  }

  set dbThemeData(String str) {
    final map = jsonDecode(str);
    data = ThemeStruct.fromMap({"name": name, "data": map}).data;
  }

  ThemeStruct({
    this.id,
    required this.name,
    this.gradientBg = false,
    this.googleFont = 'Default',
    ThemeData? themeData,
  }) : data = themeData ?? ThemesService.whiteLightTheme {
    if (googleFont.isEmpty) googleFont = 'Default';
  }

  bool get isPreset =>
      ThemesService.defaultThemes.map((e) => e.name).contains(name) ||
      ThemesService.isAdaptiveBackgroundThemeName(name);

  ThemeStruct save({bool updateIfNotAbsent = true}) {
    Database.runInTransaction(TxMode.write, () {
      ThemeStruct? existing = ThemeStruct.findOne(name);
      if (existing != null) {
        id = existing.id;
      }
      try {
        if (id != null && existing != null && updateIfNotAbsent) {
          id = Database.themes.put(this);
        } else if (id == null || existing == null) {
          id = Database.themes.put(this);
        }
      } on UniqueViolationException catch (_) {}
    });
    return this;
  }

  void delete() {
    if (kIsWeb || isPreset || id == null) return;
    Database.runInTransaction(TxMode.write, () {
      Database.themes.remove(id!);
    });
  }

  static ThemeStruct _clonePreset(dynamic preset) {
    return ThemeStruct(
      name: preset.name,
      themeData: preset.data,
      gradientBg: preset.gradientBg,
      googleFont: preset.googleFont,
    );
  }

  static ThemeStruct getLightTheme() {
    final name = PrefsSvc.theme.getSelectedLightTheme();
    final query = Database.themes.query(ThemeStruct_.name.equals(name ?? "Bright White")).build();
    query.limit = 1;
    final result = query.findFirst();
    if (result == null) {
      final preset = ThemesService.defaultThemes.firstWhere((t) => t.name == "Bright White");
      return _clonePreset(preset);
    }
    return result;
  }

  static ThemeStruct getDarkTheme() {
    final name = PrefsSvc.theme.getSelectedDarkTheme();
    final query = Database.themes.query(ThemeStruct_.name.equals(name ?? "OLED Dark")).build();
    query.limit = 1;
    final result = query.findFirst();
    if (result == null) {
      final preset = ThemesService.defaultThemes.firstWhere((t) => t.name == "OLED Dark");
      return _clonePreset(preset);
    }
    return result;
  }

  static ThemeStruct? findOne(String name) {
    if (kIsWeb) return null;
    return Database.runInTransaction(TxMode.read, () {
      final query = Database.themes.query(ThemeStruct_.name.equals(name)).build();
      query.limit = 1;
      final result = query.findFirst();
      query.close();
      return result;
    });
  }

  static ThemeStruct resolveByName(String? name, Brightness brightness) {
    final fallback = brightness == Brightness.dark ? getDarkTheme() : getLightTheme();
    if (name == null || name.isEmpty) return fallback;
    final found = findOne(name);
    if (found != null) return found;
    for (final preset in ThemesService.defaultThemes) {
      if (preset.name == name) return _clonePreset(preset);
    }
    return fallback;
  }

  static List<ThemeStruct> getThemes() {
    if (kIsWeb) return ThemesService.defaultThemes.map(_clonePreset).toList();
    List<ThemeStruct> allThemes = Database.themes.getAll();
    // sometimes the theme box is empty, this ensures it is never empty when queried
    if (allThemes.isEmpty) Database.themes.putMany(ThemesService.defaultThemes.map(_clonePreset).toList());
    allThemes = Database.themes.getAll();
    return allThemes;
  }

  Map<String, dynamic> toMap() => {
        "ROWID": id,
        "name": name,
        "gradientBg": gradientBg ? 1 : 0,
        "data": {
          "textTheme": {
            "font": googleFont,
            "titleLarge": {
              "color": data.textTheme.titleLarge!.color!.toARGB32(),
              "fontWeight": data.textTheme.titleLarge!.fontWeight!.index,
              "fontSize": data.textTheme.titleLarge!.fontSize,
            },
            "bodyLarge": {
              "color": data.textTheme.bodyLarge!.color!.toARGB32(),
              "fontWeight": data.textTheme.bodyLarge!.fontWeight!.index,
              "fontSize": data.textTheme.bodyLarge!.fontSize,
            },
            "bodyMedium": {
              "color": data.textTheme.bodyMedium!.color!.toARGB32(),
              "fontWeight": data.textTheme.bodyMedium!.fontWeight!.index,
              "fontSize": data.textTheme.bodyMedium!.fontSize,
            },
            "bodySmall": {
              "color": data.textTheme.bodySmall!.color!.toARGB32(),
              "fontWeight": data.textTheme.bodySmall!.fontWeight!.index,
              "fontSize": data.textTheme.bodySmall!.fontSize,
            },
            "labelLarge": {
              "color": data.textTheme.labelLarge!.color!.toARGB32(),
              "fontWeight": data.textTheme.labelLarge!.fontWeight!.index,
              "fontSize": data.textTheme.labelLarge!.fontSize,
            },
            "labelSmall": {
              "color": data.textTheme.labelSmall!.color!.toARGB32(),
              "fontWeight": data.textTheme.labelSmall!.fontWeight!.index,
              "fontSize": data.textTheme.labelSmall!.fontSize,
            },
            "bubbleText": {
              "fontSize": (data.extensions[BubbleText] as BubbleText).bubbleText.fontSize,
            }
          },
          "colorScheme": {
            "primary": data.colorScheme.primary.toARGB32(),
            "onPrimary": data.colorScheme.onPrimary.toARGB32(),
            "primaryContainer": data.colorScheme.primaryContainer.toARGB32(),
            "onPrimaryContainer": data.colorScheme.onPrimaryContainer.toARGB32(),
            "secondary": data.colorScheme.secondary.toARGB32(),
            "onSecondary": data.colorScheme.onSecondary.toARGB32(),
            "secondaryContainer": data.colorScheme.secondaryContainer.toARGB32(),
            "onSecondaryContainer": data.colorScheme.onSecondaryContainer.toARGB32(),
            "tertiary": data.colorScheme.tertiary.toARGB32(),
            "onTertiary": data.colorScheme.onTertiary.toARGB32(),
            "tertiaryContainer": data.colorScheme.tertiaryContainer.toARGB32(),
            "onTertiaryContainer": data.colorScheme.onTertiaryContainer.toARGB32(),
            "error": data.colorScheme.error.toARGB32(),
            "onError": data.colorScheme.onError.toARGB32(),
            "errorContainer": data.colorScheme.errorContainer.toARGB32(),
            "onErrorContainer": data.colorScheme.onErrorContainer.toARGB32(),
            // "background" kept as compat stub for older app versions reading this JSON
            "background": data.colorScheme.surface.toARGB32(),
            "onBackground": data.colorScheme.onSurface.toARGB32(),
            "surface": data.colorScheme.surface.toARGB32(),
            "onSurface": data.colorScheme.onSurface.toARGB32(),
            "surfaceVariant": data.colorScheme.surfaceVariant.toARGB32(),
            "onSurfaceVariant": data.colorScheme.onSurfaceVariant.toARGB32(),
            "outline": data.colorScheme.outline.toARGB32(),
            "shadow": data.colorScheme.shadow.toARGB32(),
            "inverseSurface": data.colorScheme.inverseSurface.toARGB32(),
            "onInverseSurface": data.colorScheme.onInverseSurface.toARGB32(),
            "inversePrimary": data.colorScheme.inversePrimary.toARGB32(),
            "surfaceContainerHighest": data.colorScheme.surfaceContainerHighest.toARGB32(),
            "iMessageBubble": (data.extensions[BubbleColors] as BubbleColors?)?.iMessageBubbleColor?.toARGB32(),
            "oniMessageBubble": (data.extensions[BubbleColors] as BubbleColors?)?.oniMessageBubbleColor?.toARGB32(),
            "smsBubble": (data.extensions[BubbleColors] as BubbleColors?)?.smsBubbleColor?.toARGB32(),
            "onSmsBubble": (data.extensions[BubbleColors] as BubbleColors?)?.onSmsBubbleColor?.toARGB32(),
            "receivedBubble": (data.extensions[BubbleColors] as BubbleColors?)?.receivedBubbleColor?.toARGB32(),
            "onReceivedBubble": (data.extensions[BubbleColors] as BubbleColors?)?.onReceivedBubbleColor?.toARGB32(),
            "brightness": data.colorScheme.brightness.index,
          },
        },
      };

  factory ThemeStruct.fromMap(Map<String, dynamic> json) {
    final map = json["data"];
    final brightness = Brightness.values[map["colorScheme"]["brightness"]];
    final font = GoogleFonts.asMap()[map["textTheme"]["font"]] ??
        ({
          TextStyle? textStyle,
          Color? color,
          Color? backgroundColor,
          double? fontSize,
          FontWeight? fontWeight,
          FontStyle? fontStyle,
          double? letterSpacing,
          double? wordSpacing,
          TextBaseline? textBaseline,
          double? height,
          Locale? locale,
          Paint? foreground,
          Paint? background,
          List<Shadow>? shadows,
          List<FontFeature>? fontFeatures,
          TextDecoration? decoration,
          Color? decorationColor,
          TextDecorationStyle? decorationStyle,
          double? decorationThickness,
        }) {
          return textStyle!;
        };
    final typography = brightness == Brightness.light
        ? Typography.englishLike2021.merge(Typography.blackMountainView)
        : Typography.englishLike2021.merge(Typography.whiteMountainView);
    final baseFlexTheme = FlexColorScheme(
      textTheme: typography.copyWith(
        titleLarge: font(
            textStyle: typography.titleLarge!
                .copyWith(
                  color: Color(map["textTheme"]["titleLarge"]["color"]),
                  fontWeight: FontWeight.values[map["textTheme"]["titleLarge"]["fontWeight"]],
                  fontSize: map["textTheme"]["titleLarge"]["fontSize"]?.toDouble(),
                )
                .apply(letterSpacingFactor: 0)),
        bodyLarge: font(
            textStyle: typography.bodyLarge!
                .copyWith(
                  color: Color(map["textTheme"]["bodyLarge"]["color"]),
                  fontWeight: FontWeight.values[map["textTheme"]["bodyLarge"]["fontWeight"]],
                  fontSize: map["textTheme"]["bodyLarge"]["fontSize"]?.toDouble(),
                )
                .apply(letterSpacingFactor: 0)),
        bodyMedium: font(
            textStyle: typography.bodyMedium!
                .copyWith(
                  color: Color(map["textTheme"]["bodyMedium"]["color"]),
                  fontWeight: FontWeight.values[map["textTheme"]["bodyMedium"]["fontWeight"]],
                  fontSize: map["textTheme"]["bodyMedium"]["fontSize"]?.toDouble(),
                )
                .apply(letterSpacingFactor: 0)),
        bodySmall: font(
            textStyle: typography.bodySmall!
                .copyWith(
                  color: Color(map["textTheme"]["bodySmall"]["color"]),
                  fontWeight: FontWeight.values[map["textTheme"]["bodySmall"]["fontWeight"]],
                  fontSize: map["textTheme"]["bodySmall"]["fontSize"]?.toDouble(),
                )
                .apply(letterSpacingFactor: 0)),
        labelLarge: font(
            textStyle: typography.labelLarge!
                .copyWith(
                  color: Color(map["textTheme"]["labelLarge"]["color"]),
                  fontWeight: FontWeight.values[map["textTheme"]["labelLarge"]["fontWeight"]],
                  fontSize: map["textTheme"]["labelLarge"]["fontSize"]?.toDouble(),
                )
                .apply(letterSpacingFactor: 0)),
        labelSmall: font(
            textStyle: typography.labelSmall!
                .copyWith(
                  color: Color(map["textTheme"]["labelSmall"]["color"]),
                  fontWeight: FontWeight.values[map["textTheme"]["labelSmall"]["fontWeight"]],
                  fontSize: map["textTheme"]["labelSmall"]["fontSize"]?.toDouble(),
                )
                .apply(letterSpacingFactor: 0)),
        // these are not themeable and only used to override the font family
        displayLarge: font(textStyle: typography.displayLarge),
        displayMedium: font(textStyle: typography.displayMedium),
        displaySmall: font(textStyle: typography.displaySmall),
        headlineLarge: font(textStyle: typography.headlineLarge),
        headlineMedium: font(textStyle: typography.headlineMedium),
        headlineSmall: font(textStyle: typography.headlineSmall),
        titleMedium: font(textStyle: typography.titleMedium),
        titleSmall: font(textStyle: typography.titleSmall),
        labelMedium: font(textStyle: typography.labelMedium),
      ),
      colorScheme: ColorScheme(
        primary: Color(map["colorScheme"]["primary"]),
        onPrimary: Color(map["colorScheme"]["onPrimary"]),
        primaryContainer: Color(map["colorScheme"]["primaryContainer"]),
        onPrimaryContainer: Color(map["colorScheme"]["onPrimaryContainer"]),
        secondary: Color(map["colorScheme"]["secondary"]),
        onSecondary: Color(map["colorScheme"]["onSecondary"]),
        secondaryContainer: Color(map["colorScheme"]["secondaryContainer"]),
        onSecondaryContainer: Color(map["colorScheme"]["onSecondaryContainer"]),
        tertiary: Color(map["colorScheme"]["tertiary"]),
        onTertiary: Color(map["colorScheme"]["onTertiary"]),
        tertiaryContainer: Color(map["colorScheme"]["tertiaryContainer"]),
        onTertiaryContainer: Color(map["colorScheme"]["onTertiaryContainer"]),
        error: Color(map["colorScheme"]["error"]),
        onError: Color(map["colorScheme"]["onError"]),
        errorContainer: Color(map["colorScheme"]["errorContainer"]),
        onErrorContainer: Color(map["colorScheme"]["onErrorContainer"]),
        surface: Color(map["colorScheme"]["surface"] ?? map["colorScheme"]["background"]),
        onSurface: Color(map["colorScheme"]["onSurface"] ?? map["colorScheme"]["onBackground"]),
        surfaceVariant: Color(map["colorScheme"]["surfaceVariant"]),
        onSurfaceVariant: Color(map["colorScheme"]["onSurfaceVariant"]),
        outline: Color(map["colorScheme"]["outline"]),
        shadow: Color(map["colorScheme"]["shadow"]),
        inverseSurface: Color(map["colorScheme"]["inverseSurface"]),
        onInverseSurface: Color(map["colorScheme"]["onInverseSurface"]),
        inversePrimary: Color(map["colorScheme"]["inversePrimary"]),
        brightness: brightness,
      ),
      useMaterial3: true,
    ).toTheme;
    final baseTheme = map["colorScheme"]["surfaceContainerHighest"] != null
        ? baseFlexTheme.copyWith(
            colorScheme: baseFlexTheme.colorScheme.copyWith(
              surfaceContainerHighest: Color(map["colorScheme"]["surfaceContainerHighest"]),
            ),
          )
        : baseFlexTheme;
    return ThemeStruct(
        id: json["ROWID"],
        name: json["name"],
        gradientBg: json["gradientBg"] == 1,
        themeData: baseTheme.copyWith(splashFactory: InkSparkle.splashFactory, extensions: [
          BubbleColors(
            iMessageBubbleColor:
                map["colorScheme"]["iMessageBubble"] == null ? null : Color(map["colorScheme"]["iMessageBubble"]),
            oniMessageBubbleColor:
                map["colorScheme"]["oniMessageBubble"] == null ? null : Color(map["colorScheme"]["oniMessageBubble"]),
            smsBubbleColor: map["colorScheme"]["smsBubble"] == null ? null : Color(map["colorScheme"]["smsBubble"]),
            onSmsBubbleColor:
                map["colorScheme"]["onSmsBubble"] == null ? null : Color(map["colorScheme"]["onSmsBubble"]),
            receivedBubbleColor:
                map["colorScheme"]["receivedBubble"] == null ? null : Color(map["colorScheme"]["receivedBubble"]),
            onReceivedBubbleColor:
                map["colorScheme"]["onReceivedBubble"] == null ? null : Color(map["colorScheme"]["onReceivedBubble"]),
          ),
          BubbleText(
            bubbleText: font(
                textStyle: typography.bodyMedium!
                    .copyWith(
                      color: Color(map["textTheme"]["bodyMedium"]["color"]),
                      fontWeight: FontWeight.values[map["textTheme"]["bodyMedium"]["fontWeight"]],
                      fontSize: map["textTheme"]["bubbleText"]?["fontSize"]?.toDouble() ?? 15,
                      height: typography.bodyMedium!.height! * 0.85,
                    )
                    .apply(letterSpacingFactor: 0)),
          ),
        ]));
  }

  /// Returns the colors for a theme. Returns colors overwritten by Material You
  /// theming if [returnMaterialYou] is true
  Map<String, Color> colors(bool dark, {bool returnMaterialYou = true}) {
    ThemeData finalData = data;
    if (returnMaterialYou) {
      final tuple = ThemeSvc.getStructsFromData(data, data);
      if (dark) {
        finalData = tuple.dark;
      } else {
        finalData = tuple.light;
      }
    }
    return {
      "primary": finalData.colorScheme.primary,
      "onPrimary": finalData.colorScheme.onPrimary,
      "primaryContainer": finalData.colorScheme.primaryContainer,
      "onPrimaryContainer": finalData.colorScheme.onPrimaryContainer,
      "secondary": finalData.colorScheme.secondary,
      "onSecondary": finalData.colorScheme.onSecondary,
      "tertiaryContainer": finalData.colorScheme.tertiaryContainer,
      "onTertiaryContainer": finalData.colorScheme.onTertiaryContainer,
      "error": finalData.colorScheme.error,
      "onError": finalData.colorScheme.onError,
      "errorContainer": finalData.colorScheme.errorContainer,
      "onErrorContainer": finalData.colorScheme.onErrorContainer,
      "surface": finalData.colorScheme.surface,
      "onSurface": finalData.colorScheme.onSurface,
      "surfaceVariant": finalData.colorScheme.surfaceVariant,
      "onSurfaceVariant": finalData.colorScheme.onSurfaceVariant,
      "inverseSurface": finalData.colorScheme.inverseSurface,
      "onInverseSurface": finalData.colorScheme.onInverseSurface,
      "smsBubble": (finalData.extensions[BubbleColors] as BubbleColors?)?.smsBubbleColor ?? HexColor("43CC47"),
      "onSmsBubble": (finalData.extensions[BubbleColors] as BubbleColors?)?.onSmsBubbleColor ?? Colors.white,
      // the following get their own customization card, rather than
      // being paired like the above
      "outline": finalData.colorScheme.outline,
    };
  }

  /// Returns descriptions for each used color item
  static Map<String, String> get colorDescriptions => {
        "primary":
            "primary is used everywhere as the main colored element. You will see this on buttons, sliders, chips, switches, etc.",
        "onPrimary":
            "onPrimary is used for any text or icon that is on top of a primary colored element.\n\nNote: iMessage bubble colors are decided between primary / primaryContainer, whichever is more 'colorful' based on saturation and luminance. SMS bubble colors are the opposite.",
        "primaryContainer": "primaryContainer is used as a fill color for containers, buttons, and switches.",
        "onPrimaryContainer":
            "onPrimaryContainer is used for any text or icon that is on top of a primaryContainer colored elemnent.\n\nNote: iMessage bubble colors are decided between primary / primaryContainer, whichever is more 'colorful' based on saturation and luminance. SMS bubble colors are the opposite.",
        "secondary":
            "secondary is used everywhere as an accent element. Find this on buttons that we want to draw your attention to.",
        "onSecondary": "onSecondary is used for any text or icon that is on top of a secondary colored element.",
        "tertiaryContainer": "tertiaryContainer is used on pinned chats to depict mute / unmute status.",
        "onTertiaryContainer":
            "onTertiaryContainer is used for any text or icon that is on top of a tertiaryContainer colored element.",
        "error":
            "error is used for any element that indicates an error, for example the error icon next to a failed message.",
        "onError": "onError is used for any text or icon that is on top of an error colored element.",
        "errorContainer": "errorContainer is used on desktop as the hover color for the X button.",
        "onErrorContainer": "onErrorContainer is used on desktop as the icon color for the X button.",
        "surface": "surface is the main background color of the app.",
        "onSurface": "onSurface is used for any text or icon that is on top of a surface colored element.",
        "surfaceVariant":
            "surfaceVariant is an alternate background color of the app. It is also used as the divider color between tiles in settings.",
        "onSurfaceVariant":
            "onSurfaceVariant is used for any text or icon that is on top of a surfaceVariant colored element.\n\nNote: We use an algorithm internally to determine whether surface or surfaceVariant will be more visible on the background color.",
        "inverseSurface":
            "inverseSurface is an attention-grabbing background color. We use this on snackbars / toast messages.",
        "onInverseSurface":
            "onInverseSurface is used for any text or icon that is on top of an inverseSurface colored element.",
        "smsBubble": "smsBubble is used for the background color of sent SMS / Text Forwarding messages.",
        "onSmsBubble": "onSmsBubble is used for any text or icon that is on top of a smsBubble colored element.",
        // the following get their own customization card, rather than
        // being paired like the above
        "outline": "outline is used for most outlined elements, as well as most small label-style text.",
      };

  /// Returns the current text sizes for a theme
  Map<String, double> get textSizes => {
        "titleLarge": data.textTheme.titleLarge!.fontSize!,
        "bodyLarge": data.textTheme.bodyLarge!.fontSize!,
        "bodyMedium": data.textTheme.bodyMedium!.fontSize!,
        "bodySmall": data.textTheme.bodySmall!.fontSize!,
        "labelLarge": data.textTheme.labelLarge!.fontSize!,
        "labelSmall": data.textTheme.labelSmall!.fontSize!,
        "bubbleText": (data.extensions[BubbleText] as BubbleText).bubbleText.fontSize!,
      };

  /// Returns the default text sizes
  static Map<String, double> get defaultTextSizes => {
        "titleLarge": 22, // M3 default
        "bodyLarge": 16, // M3 default
        "bodyMedium": 14, // M3 default
        "bodySmall": 12, // M3 default
        "labelLarge": 14, // M3 default
        "labelSmall": 11, // M3 default
        "bubbleText": 15, // custom default
      };

  @override
  bool operator ==(Object other) => other is ThemeStruct && name == other.name;

  @override
  int get hashCode => name.hashCode;
}
