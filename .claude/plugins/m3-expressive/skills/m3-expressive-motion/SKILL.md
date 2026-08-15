---
name: m3-expressive-motion
description: >
  Implements Material 3 Expressive motion in Jetpack Compose — MotionScheme.expressive(),
  MaterialTheme.motionScheme, spatial vs effects springs, fast/default/slow specs, spring-based
  animation instead of tween/easing curves, shared element transitions, SharedTransitionLayout,
  sharedBounds, container transform, predictive back, veilOut/unveilIn, and animateBounds. Use
  when the user asks about Compose animation feel, springs, bounce, overshoot, transitions
  between screens, "make it feel snappier/bouncier", motion tokens, or reduced motion
  accessibility.
---

# M3 Expressive Motion

## The core shift

Expressive motion is **physics, not curves**. Stop reaching for `tween(durationMillis = 300,
easing = FastOutSlowInEasing)`. Read specs off the theme:

```kotlin
val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
val offset by animateFloatAsState(target, animationSpec = spec)
```

Six specs, two families:

| | Fast | Default | Slow |
| --- | --- | --- | --- |
| **Spatial** (things that move/resize) | `fastSpatialSpec()` | `defaultSpatialSpec()` | `slowSpatialSpec()` |
| **Effects** (alpha, color, elevation) | `fastEffectsSpec()` | `defaultEffectsSpec()` | `slowEffectsSpec()` |

**Spatial springs overshoot and bounce into place. Effects springs must not.** This is Material's
own rule, stated in the `MotionScheme` KDoc: effects motion "shouldn't have any overshoot."
Animating opacity, color or elevation with a spatial spec is a real defect, not a style choice —
color channels interpolate out of gamut and land wrong, and the bounce reads as a flicker rather
than as movement. Alpha specifically is clamped to [0,1] by the render node, so an alpha
overshoot won't render past white; the damage there is timing and the mismatch with every other
fade in the app. Match the family to what you are animating.

Exact spring constants (useful when you must hand-roll or explain the feel):

| Scheme | Spec | Damping | Stiffness |
| --- | --- | --- | --- |
| Expressive | Default spatial | 0.8 | 380 |
| Expressive | Fast spatial | 0.6 | 800 |
| Expressive | Slow spatial | 0.8 | 200 |
| Standard | Default spatial | 0.9 | 700 |
| Standard | Fast spatial | 0.9 | 1400 |
| Standard | Slow spatial | 0.9 | 300 |
| Both | Default / Fast / Slow effects | 1.0 | 1600 / 3800 / 800 |

Read that table: **effects motion is identical between schemes.** The only thing
`MotionScheme.expressive()` changes is spatial — underdamped (overshoot) and softer. That is the
entire mechanical difference.

## Picking a tier

- **Fast** — small elements, high-frequency interactions, anything under the user's finger.
  Press states, chip selection, icon swaps.
- **Default** — the standard choice. Component-scale movement: FAB menu opening, sheet settling,
  list item reordering.
- **Slow** — large surfaces and full-screen changes where the motion itself is the content.
  Use sparingly; slow motion on a small element reads as lag.

## Reference files

| Task | Read |
| --- | --- |
| Full `MotionScheme` API, spring values, per-component motion defaults, reduced-motion handling | `references/motion-scheme.md` |
| Working code: shared element transitions, container transform, predictive back, `veilOut`/`unveilIn`, `animateBounds`, gesture-driven motion, real app excerpts | `references/motion-recipes.md` |

## Rules that hold across the codebase

1. **Never mix schemes.** One `motionScheme` per theme. Component motion becomes incoherent when
   half the screen bounces and half doesn't.
2. **Do not animate layout with effects specs or color/alpha with spatial specs.** See above.
3. **Overshoot is for arrivals, not departures.** An element bouncing as it leaves reads as a
   glitch.
4. **Shared element transitions are the highest-value expressive motion** and the most commonly
   skipped. If a tap navigates from a card to a detail screen showing the same content, wire
   `SharedTransitionLayout` + `sharedBounds`. `references/motion-recipes.md` has a reusable
   `Modifier.sharedBoundsReveal` helper lifted from a shipping app.
5. **Respect reduced motion.** Check the system setting and degrade spatial springs to
   near-critically-damped or to `snap()`. A `MotionPolicy` CompositionLocal that swaps schemes at
   runtime is the cleanest pattern — one is in the recipes file.
6. **Gesture-driven beats time-driven.** Where the user is dragging, drive the value off the
   gesture and let the spring only handle the release.

## Verification

- Confirm the root theme is `MaterialExpressiveTheme` with `motionScheme = MotionScheme.expressive()`.
  Without it, every `MaterialTheme.motionScheme` read returns standard springs and the work is invisible.
- Grep for `tween(` and `LinearEasing` in newly written UI code — each one is a candidate for a
  motion-scheme spec.
- Test with "Remove animations" / reduced motion enabled in developer settings.
- Watch for spatial specs on color/alpha/elevation properties — the giveaway is a colour that
  visibly overshoots its target hue before settling.
