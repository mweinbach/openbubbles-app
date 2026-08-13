# lib/app/animations/ — iMessage Send Effects

Rendered as full-screen overlays when a message is sent with an iMessage effect.

## Pattern: Two Files Per Effect

| File suffix | Purpose |
|-------------|---------|
| `*_classes.dart` | Data/controller classes — particle state, physics math, animation controllers |
| `*_rendering.dart` | `LeafRenderObjectWidget` + `RenderBox` — the actual custom paint/draw logic |

## Effects

| Effect | Classes | Rendering |
|--------|---------|-----------|
| Celebration / Confetti | `celebration_class.dart` | `celebration_rendering.dart` |
| Fireworks | `fireworks_classes.dart` | `fireworks_rendering.dart` |
| Balloons | `balloon_classes.dart` | `balloon_rendering.dart` |
| Love / Hearts | `love_classes.dart` | `love_rendering.dart` |
| Lasers | `laser_classes.dart` | `laser_rendering.dart` |
| Spotlight | `spotlight_classes.dart` | `spotlight_rendering.dart` |

## Effect Name → Apple Code Mapping
`helpers/types/constants.dart` — `effectMap` and `stringToMessageEffect`

## Trigger Point
Effects are triggered from `lib/app/layouts/conversation_view/widgets/effects/`

## Adding a New Effect
1. `myeffect_classes.dart` — particle/animation data classes
2. `myeffect_rendering.dart` — `LeafRenderObjectWidget` + `RenderBox` subclass
3. Add to `effectMap` in `helpers/types/constants.dart`
4. Wire into the effects trigger widget in `conversation_view/widgets/effects/`
