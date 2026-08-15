---
name: m3-expressive-review
description: >
  Reviews, audits or critiques Jetpack Compose UI against Material 3 Expressive guidance. Use when
  the user asks to review a screen, check whether their UI is "properly expressive", audit a
  diff or PR for Material 3 issues, find out why their app "doesn't feel like Material 3
  Expressive", get feedback on a Compose layout, or wants a checklist before shipping an
  expressive redesign.
---

# M3 Expressive Design Review

Audit Compose UI against Expressive guidance and report concrete, located findings. Be honest —
a review that says everything is fine is worthless, and so is one that flags every rounded corner.

## Method

1. **Scope it.** Ask what to review if unclear: a screen, a module, the whole app, or a diff. For
   a diff, review the changed code plus the theme it inherits from.
2. **Read the theme first.** Almost every "it doesn't feel expressive" complaint traces back to
   the theme, not the screens. Check `MaterialExpressiveTheme` vs `MaterialTheme`, the
   `motionScheme`, and whether components read theme tokens or hardcode values.
3. **Walk the checklist** in `references/review-checklist.md`. It is organised by severity so the
   report can lead with what actually matters.
4. **Report findings, ranked.** Each finding: file + line, what's wrong, why it matters, and the
   concrete fix. No vague "consider adding more motion".
5. **Say what's good.** Naming what already works stops the user from breaking it.

## The five checks that catch most problems

Run these first; they explain the majority of "this doesn't look right" reports.

1. **Is the root `MaterialExpressiveTheme` with an explicit `motionScheme`?** If it's bare
   `MaterialTheme`, every expressive component is running standard motion and no amount of screen
   work will fix the feel. This is the single highest-yield check.
2. **Is there a hero, and is the budget blown?** Material's budget is one or two hero moments
   *in the product*, not per screen — so most screens should have zero and a couple should carry
   the expression. Reviewing a single screen, apply the per-screen count as a proxy: zero
   candidates means no hierarchy (the defining failure of a naive Expressive redesign), one or
   two is right, three or more is over-expressed. If reviewing a whole app, count heroes across
   screens and flag when many screens each claim one.
3. **Are durations hardcoded?** Grep for `tween(`, `durationMillis`, `LinearEasing`,
   `FastOutSlowInEasing`. Each is a place where the theme's motion is being bypassed.
4. **Are shape and color doing work, or decoration?** A morphing shape or a `primaryContainer`
   fill should mark state or importance. If it's applied uniformly, it carries no information.
5. **Are the new container components used where they'd help?** Loose rows of buttons that should
   be a connected `ButtonGroup`; a settings list that should be segmented; a scattered set of
   actions that should be a floating toolbar or FAB menu.

## Severity model for the report

- **Broken** — will not compile, crashes, missing opt-in, wrong artifact version, contrast below
  4.5:1, touch target under 48dp, missing content description.
- **Wrong** — contradicts Material guidance in a way users will feel: spatial spring on an alpha
  animation, two nav containers, FAB menu with nine items, wavy indicator at 16dp, hardcoded
  colors that break dark mode.
- **Weak** — the code works but leaves expression on the table: no hero, no shared element
  transition where content is clearly continuous, `MaterialTheme.shapes` unused, baseline type
  scale where emphasized styles belong.
- **Note** — preference, or a valid choice worth flagging so it's deliberate.

Do not inflate severity to make the review look thorough.

## Reference

- `references/review-checklist.md` — the full checklist across theme, color, typography, shape,
  motion, components, navigation, accessibility and performance, with the specific grep patterns
  and code smells for each.
- `references/testing-expressive-ui.md` — how to verify the fix. Why expressive UI hangs naive
  Compose tests and the `LocalInspectionMode` / v2-rule fix, the complete Compose screenshot-testing
  setup, testable screen architecture, previews, the manual verification checklist, and what
  accessibility tooling does and does not catch. Load this when the user asks how to test or
  prove an expressive change, or when a finding needs a regression guard.

## Output

Lead with a one-paragraph verdict, then findings grouped by severity, then a short "what's
already good" section. If the user asked for fixes rather than a report, apply them in severity
order and say what you changed.
