# utils/color_engine/ — Color Space Math & Theme Generation

## Files
| File | Contents |
|------|---------|
| `engine.dart` | Barrel export — re-exports `colors.dart` and `theme.dart` |
| `colors.dart` | Color space implementations: `LinearSrgb`, `Srgb`, `Oklab` with conversion math |
| `theme.dart` | Theme color derivation — generates complementary/harmonious colors from a seed |

## Purpose

This is the low-level color science layer that powers the dynamic theme system. It converts between color spaces (linear sRGB ↔ Oklab via matrix transforms) to do perceptually-uniform color operations — mixing, lightening, darkening — that produce better results than naive RGB blending.

The `ThemesService` (`lib/services/ui/theme/themes_service.dart`) calls into this layer to generate message bubble colors, avatar colors, and accent colors from a user-chosen seed color.

## When to Use

- **Don't call directly from widgets.** Use `Theme.of(context).extension<BubbleColors>()` or the theme data provided by `ThemesService`.
- If adding a new derived color to the theme, add the derivation logic to `theme.dart` and expose it through `ThemesService`.
- If you need to convert between color spaces (e.g. for a custom color picker), import from `colors.dart`.

## Related
- Theme management: `lib/services/ui/theme/themes_service.dart`
- Bubble color extension: `lib/helpers/ui/theme_helpers.dart` (`BubbleColors`)
- Theme settings: `lib/app/layouts/settings/pages/theming/`
