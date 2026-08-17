# Worked Screens — Settings, Forms, Chat, Search

The four screens most apps actually spend their time on. Each is complete: state shape, composables,
annotated decisions, an accessibility block, and what was deliberately left calm.

**Provenance `[API-alpha26]`.** Every `material3` / `material3-adaptive` composable name and
`*Defaults` member name in this file was machine-checked against
`compose/material3/material3/api/current.txt` and `compose/material3/adaptive/*/api/current.txt`
at the alpha26 / 1.3.0 SHA. Non-material3 APIs (foundation, ui, animation, navigation3,
third-party) were **not** checked; anything that could not be confirmed is flagged `[UNVERIFIED]`
inline at its use site.

| § | Screen | The one thing it teaches |
| --- | --- | --- |
| 1 | Settings | The segmented-list idiom, and shape-on-interaction as the whole expressive payload |
| 2 | Form | The current text-field API, validation timing, IME insets, scroll-to-error |
| 3 | Chat | Asymmetric bubble shapes, streaming into state, announcing a stream once |
| 4 | Search | The `SearchBarState` slot API and the four states a search screen really has |

These are the screens where Expressive is easy to get *wrong in the other direction* — over-applied
until nothing reads as emphatic. A settings list has no hero moment. A form has no hero moment. The
expression lives in **containment, shape-on-interaction and motion**, not in polygons and display
type. Compare `references/worked-screens-media-and-hero.md`, where the hero budget is the point, and
`references/worked-screens-list-detail-feed.md` for lists, detail pages, feeds and the adaptive pair.

Target artifacts: `material3 1.5.0-alpha26`, `material3-adaptive 1.3.0`, `compose-ui 1.12.0`.

## Provenance markers

- `[CORPUS <repo>: path]` — verbatim (or lightly condensed) from a shipping open-source app.
- `[ANDROIDX]` — signature or `@Sampled` function from the androidx tree.
- `[COMPOSED]` — assembled here from verified pieces. Compile it.
- `[canonical-form]` — API shape is right, exact signature not readable from source. **Compile-check
  before shipping.** Used heavily in §2: the Expressive text-field APIs have **zero corpus usage**.

## Opt-in policy used throughout

At alpha26 the Expressive list-item, button, split-button, search-bar and app-bar families have all
graduated. `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` appears in this file only for
`LoadingIndicator` (§3) and `MaterialShapes` (§3, §4 empty states). `ExperimentalMaterial3Api` is a
different annotation and is still required for `TimePickerDialog` (§2) and `AppBarWithSearch` (§4).

`SegmentedListItem` / `segmentedShapes` / `segmentedColors` / `SegmentedGap` became
**non-experimental in alpha23**. On 1.4.0 → alpha22 add the Expressive opt-in; on alpha23+ remove it.

## Deep material lives elsewhere

| Topic | Read |
| --- | --- |
| `ListItem` overloads, segmented shapes, cards, sheets, dialogs, menus | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/lists-cards-containers.md` |
| Text fields, `TimePickerState`, switches, sliders | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/sliders-and-inputs.md` |
| `SplitButtonLayout`, connected groups, button size scale | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/buttons.md` |
| Search bars, flexible app bars, `AppBarRow` | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/app-bars.md` |
| `LoadingIndicator` | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/progress-and-loading.md` |
| Shape tokens, `MaterialShapes` catalog | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-shapes/references/shapes-catalog.md` |
| Spring tiers, `animateItem`, shared bounds | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-motion/references/motion-recipes.md` |

---

# 1. Settings screen — the segmented-list idiom done properly

## 1.0 What this demonstrates

The single highest-value Expressive tactic on an ordinary screen: **containment**. A settings screen
is a run of rows grouped into a shared container, where the group's outer corners are large, the
inner corners are small, and each row **swells into a rounder shape under the finger**. That last
part is the expression, and it costs zero animation code — it comes out of a `ListItemShapes`.

| Tactic | Where | Why |
| --- | --- | --- |
| Segmented containment | every section | "One container = one concept." Ungrouped rows blend together |
| Shape-on-interaction | `pressedShape = shapes.extraLargeIncreased` | The row rounds out under the finger. No animation code |
| Connected toggle group | theme picker inside a row's `supportingContent` | Three mutually exclusive alternatives in one set |
| `thumbContent` on `Switch` | every boolean row | State legible without relying on colour alone |

**Deliberately calm:** no polygons, no display type, no hero button, no elevation. Separation comes
from **surface tier + shape + spacing**, never from shadows — `surfaceContainerLow` →
`surfaceContainer` → `surfaceContainerHigh` → `surfaceContainerHighest` is a four-step depth ladder
that survives dark mode and AMOLED, which shadows do not.

## 1.1 Shape and colour tokens — define once, per app

`[CORPUS Tomato: shared/src/commonMain/.../ui/theme/Shape.kt and .../ui/theme/Color.kt — the best
segmented system in the corpus]`

```kotlin
object AppListDefaults {
    /**
     * Positional corners from segmentedShapes(index, count), PLUS per-interaction-state shapes.
     * The per-state entries are what produce the morph — nothing else animates.
     */
    @Composable
    fun segmentedShapes(index: Int, count: Int): ListItemShapes =
        ListItemDefaults.segmentedShapes(
            index,
            count,
            ListItemDefaults.shapes(
                shape = MaterialTheme.shapes.extraSmall,
                selectedShape = MaterialTheme.shapes.extraLargeIncreased,   // Expressive-only token
                pressedShape = MaterialTheme.shapes.extraLargeIncreased,
                focusedShape = MaterialTheme.shapes.large,
                hoveredShape = MaterialTheme.shapes.extraLarge,
                draggedShape = MaterialTheme.shapes.extraLargeIncreased,
            ),
        )

    /**
     * segmentedColors, NOT colors(). The segmented factory supplies the selected/pressed
     * container and content colours the segmented item expects; plain colors() leaves the
     * selected state with no container treatment at all.
     */
    val listItemColors: ListItemColors
        @Composable get() = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
}
```

**Notes**

- Build from `MaterialTheme.shapes.*`, not literal dp, so theme overrides propagate.
- Tomato's original carries a `singleElement: Boolean = count == 1` parameter that forces
  `shapes.large` for a one-item group. **Delete it on alpha25+** — I2ea1c fixed `segmentedShapes`
  for `count == 1` library-side. Med's equivalent workaround
  (`modifier = if (count == 1) Modifier.clip(RoundedCornerShape(20.dp)) else Modifier`) is worse: a
  `Modifier.clip` actively suppresses the press morph.
- `count` is the count of items **in the group**, not in the whole list. Deriving it from a paginated
  `list.size` gives you two "last" items.

## 1.2 A grouped section

`[COMPOSED — structure verbatim from `[CORPUS Tomato: .../settingsScreen/screens/SettingsMainScreen.kt]`]`

```kotlin
@Immutable
data class SettingsRow(
    val key: String,
    val label: String,
    val supporting: String? = null,
    val icon: ImageVector,
)

@Composable
fun SettingsSection(
    title: String,
    rows: List<SettingsRow>,
    selectedKey: String?,
    onRowClick: (SettingsRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.semantics { isTraversalGroup = true }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp),
        )
        Column(
            // The canonical inter-segment gap, tuned against the inner corner radius.
            // Do not substitute an arbitrary dp — 12dp makes the group read as loose cards.
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            rows.forEachIndexed { index, row ->
                SegmentedListItem(
                    selected = selectedKey == row.key,
                    onClick = { onRowClick(row) },
                    leadingContent = { Icon(row.icon, contentDescription = null) },
                    supportingContent = row.supporting?.let {
                        { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    },
                    shapes = AppListDefaults.segmentedShapes(index, rows.size),
                    colors = AppListDefaults.listItemColors,
                ) {
                    // Expressive overload: the headline is the TRAILING lambda, not a
                    // `headlineContent =` first parameter. Both overload sets are on the
                    // classpath, so mixing them up gives "no applicable overload".
                    Text(row.label)
                }
            }
        }
    }
}
```

**Sub-groups within one screen are just separate `index`/`count` runs** separated by a `Spacer`:
`segmentedShapes(0, 2)`, `segmentedShapes(1, 2)`, `Spacer(Modifier.height(12.dp))`, then
`segmentedShapes(0, 1)` for a standalone row.

**`selected` doubles as the navigation indicator on a list/detail layout.** Tomato sets
`selected = currentScreen == item.route` and drops `trailingContent` (the chevron) when the detail
pane is already visible — the same component doing two jobs at two window sizes.

## 1.3 A boolean row

`[CORPUS Tomato + Med — the `thumbContent` pattern appears at six sites in Med]`

```kotlin
@Composable
fun SettingsSwitchRow(
    label: String,
    supporting: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        selected = false,
        // Tapping the ROW toggles it. Wire onClick and onCheckedChange to the same action —
        // a 56dp row whose only hit target is a 52dp switch is a usability bug.
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
                enabled = enabled,
                // The cheapest expressive move in the whole system: state is legible
                // without relying on colour alone.
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        // ALWAYS this. A 24dp icon overflows the thumb.
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
                colors = SwitchDefaults.colors(
                    checkedIconColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        shapes = AppListDefaults.segmentedShapes(index, count),
        colors = AppListDefaults.listItemColors,
        // The row and the switch would otherwise be two toggle nodes announcing the same state.
        modifier = Modifier.semantics(mergeDescendants = true) {
            stateDescription = if (checked) "On" else "Off"
        },
    ) { Text(label) }
}
```

> `SegmentedListItem`'s `enabled` parameter is `[UNVERIFIED]` — the expressive `ListItem` overloads
> all carry `enabled: Boolean = true` and `SegmentedListItem` almost certainly mirrors them, but no
> corpus app passes it. If it does not resolve at your pin, drop it and gate the callbacks instead.
> Note also that alpha23 added **non-interactive variants** of the standard and segmented list items,
> so on alpha23+ check whether a no-`onClick` overload resolves before reaching for Tomato's
> `Box(Modifier.matchParentSize().clickable(false) {})` workaround.

## 1.4 Theme picker — a connected toggle group inside a row

A connected group nested inside a segmented group. This is the Expressive form of a radio group; a
`RadioButton` column is the pre-Expressive form.

`[CORPUS Tomato: shared/src/commonMain/.../settingsScreen/components/ThemePickerListItem.kt — core
verbatim]`

```kotlin
enum class ThemeMode(val label: String, val icon: ImageVector) {
    System("System", Icons.Rounded.BrightnessAuto),
    Light("Light", Icons.Rounded.LightMode),
    Dark("Dark", Icons.Rounded.DarkMode),
}

@Composable
fun ThemePickerRow(
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    index: Int,
    count: Int,
) {
    val options = remember { ThemeMode.entries }

    SegmentedListItem(
        selected = false,
        onClick = {},                       // the group inside owns the interaction
        leadingContent = {
            AnimatedContent(targetState = theme, label = "themeIcon") {
                Icon(it.icon, contentDescription = null)
            }
        },
        supportingContent = {
            Row(
                // 2dp. ButtonGroupDefaults.HorizontalArrangement is the ~12dp NON-connected
                // spacing; using it here turns a segmented control into three loose buttons.
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                options.forEachIndexed { i, option ->
                    ToggleButton(
                        checked = theme == option,
                        onCheckedChange = { onThemeChange(option) },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier
                            .weight(1f)
                            // NOT OPTIONAL. Without it a row of ToggleButtons announces as
                            // N independent checkboxes instead of one single-select group.
                            .semantics { role = Role.RadioButton },
                    ) {
                        Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        shapes = AppListDefaults.segmentedShapes(index, count),
        colors = AppListDefaults.listItemColors,
    ) { Text("Theme") }
}
```

**Shape API landmines**

- Never write `ToggleButtonDefaults.shapes(...)`. Both overloads are `DeprecationLevel.HIDDEN` at
  alpha25+ — invisible to Kotlin source, so it is a **hard compile failure**, not a warning. Use
  `ToggleButtonDefaults.shapesFor(buttonHeight: Dp)` for the default set, or the
  `ToggleButtonShapes(shape, pressedShape, checkedShape)` **constructor** for a custom silhouette.
  `ButtonGroupDefaults.connected*ButtonShapes()` above were not deprecated and need no change.
- `TonalToggleButton` was renamed **`FilledTonalToggleButton`** in alpha25. Pure rename, identical
  parameter list; the old name warns.
- The `when` must test `options.lastIndex` **before** `else`, and handle `count == 1` explicitly, or
  a single-item group gets trailing shapes and looks broken:
  `actions.size == 1 -> connectedLeadingButtonShapes()` as the first branch
  `[CORPUS vivi-music: .../ui/component/NewMenuComponents.kt]`.
- Multi-select groups use `Role.Checkbox`, not `Role.RadioButton`. Med's reusable helpers omit the
  role entirely — fix that when you copy them.

## 1.5 A destructive confirmation dialog

`AlertDialog` is structurally unchanged by Expressive. What it gains: shape from
`MaterialTheme.shapes` (so an Expressive scale changes dialog corners for free), enter/exit springs
from `MaterialTheme.motionScheme`, and `AlertDialogDefaults.IconSize` (alpha16) instead of a literal
24.dp.

`[COMPOSED]`

```kotlin
@Composable
fun ClearDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.DeleteForever,
                contentDescription = null,             // the title states the action
                modifier = Modifier.size(AlertDialogDefaults.IconSize),
            )
        },
        title = { Text("Clear all data?") },
        text = { Text("Your history and preferences will be permanently deleted. This cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Clear data") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

The Expressive upgrade available here is the **button row**: replace the two `TextButton`s with a
connected `ButtonGroup` of Cancel / Clear / Keep, tinting the destructive member with
`ToggleButtonDefaults.colors(contentColor = MaterialTheme.colorScheme.error)` and expressing the recommended
option as `checked = true` rather than a different style
`[CORPUS vivi-music: .../ui/screens/settings/AccountSettingsScreen.kt]`. Do **not** over-animate the
dialog itself — no overshoot on a scrim's alpha.

Building a fully custom dialog? Reuse `AlertDialogDefaults.shape` / `.containerColor` /
`.TonalElevation` / `.iconContentColor` / `.titleContentColor` on a `Surface` rather than inventing
colours, so it matches the built-in one under every theme
`[CORPUS vivi-music: .../ui/component/Dialog.kt]`. `BasicAlertDialog` graduated from experimental in
alpha25 — drop any `@OptIn` kept solely for it.

## 1.6 The manual corner fallback — for pins below alpha21

If `ListItemDefaults.segmentedShapes` is not available, compute the corners and pass them as `shape`
to a `Surface` / `Card` / clipped `Row`.

`[CORPUS vivi-music: .../utils/ShapesCurve.kt — with the singleton bug fixed]`

```kotlin
private const val ConnectedCornerRadius = 4      // inner corners
private const val EndCornerRadius = 16           // outer corners (20 in vivi-music's onboarding)

fun leadingItemShape() = RoundedCornerShape(
    topStart = EndCornerRadius.dp, topEnd = EndCornerRadius.dp,
    bottomStart = ConnectedCornerRadius.dp, bottomEnd = ConnectedCornerRadius.dp,
)
fun middleItemShape() = RoundedCornerShape(ConnectedCornerRadius.dp)
fun endItemShape() = RoundedCornerShape(
    topStart = ConnectedCornerRadius.dp, topEnd = ConnectedCornerRadius.dp,
    bottomStart = EndCornerRadius.dp, bottomEnd = EndCornerRadius.dp,
)
fun onlyItemShape() = RoundedCornerShape(EndCornerRadius.dp)

fun getGroupedShape(index: Int, count: Int): Shape = when {
    count <= 1 -> onlyItemShape()        // <- MUST come first. The shipped version tests
    index == 0 -> leadingItemShape()     //    index == 0 first and gives a one-item group a
    index == count - 1 -> endItemShape() //    rounded top with a 4dp bottom.
    else -> middleItemShape()
}
```

The **4dp inner / 16–20dp outer ratio is the tell of the idiom** — two independent implementations
(vivi-music and Tomato) landed on it. Space manual groups by ~2dp; a 12dp gap makes the group read as
separate cards and the 4dp inner corners look like a mistake.

**The alpha25 fix does not reach this code.** I2ea1c fixed the *library's* `segmentedShapes` for
`count == 1`; hand-rolled helpers keep the ordering bug until you fix it. On alpha25+ the better move
is to delete the helper entirely.

## 1.7 Accessibility block — settings

- **`contentDescription = null` on every leading icon and chevron.** They are decorative next to a
  text label. Icons that duplicate their label are the most common source of double-announcement in
  a settings list.
- **Merge each row.** `Modifier.semantics(mergeDescendants = true)` on the switch row so it reads as
  one target ("Notifications, On, switch") instead of two ("Notifications" then "On, switch"). The
  segmented list item already merges its own slots.
- **`stateDescription`, not colour, carries state.** `"On"` / `"Off"` on switch rows; `"Selected"` is
  supplied automatically by `selected = true` on a `SegmentedListItem`, so do not add it manually.
- **Roles on the toggle group.** `Role.RadioButton` for single-select, `Role.Checkbox` for
  multi-select. Without one, a three-member theme picker announces as three unrelated checkboxes with
  no indication that choosing one clears the others.
- **Traversal order.** Wrap each section (header + its rows) in
  `Modifier.semantics { isTraversalGroup = true }` so the header is read with its group rather than
  drifting. The section header itself does not need `heading()` on a `SegmentedListItem`-based list
  — but if you use plain `Text` headers, add `Modifier.semantics { heading() }` so TalkBack's
  heading navigation works.
- **Touch targets.** A `SegmentedListItem` is comfortably ≥48dp. The 2dp `SegmentedGap` is *below*
  the 8dp target-separation guidance — that is intentional in the spec (the group reads as one
  control), but it means you must not shrink rows.
- **The dialog** traps focus and handles predictive back for you. Verify the confirm button is not
  the first focused node — a destructive action should not be one tap from a screen reader's
  default landing point. If it is, reorder or use `traversalIndex`.
- **What TalkBack should announce** on a settings section: "Appearance, heading" → "Theme, System,
  Light, Dark" is *wrong* — the three toggle buttons must announce individually as
  "System, radio button, selected" / "Light, radio button, not selected" / "Dark, radio button, not
  selected", nested under "Theme". Verify this specifically; a nested group inside a merged list item
  is exactly where merging goes wrong.

---

# 2. Form screen

## 2.0 What this demonstrates

A form has **no hero moment**. Its expressive surface is: the rounded/tonal text-field treatment,
shape-on-press on the submit affordance, and motion that is entirely functional (scroll-to-error,
error-text reveal). Everything else is baseline.

> **The Expressive text-field APIs have ZERO usage across the whole reference corpus.** All four apps
> still use `TextFieldDefaults.colors()` / `OutlinedTextFieldDefaults.colors()` and nothing else. The
> API names below are verified from release notes; the exact parameter shapes are `[canonical-form]`.
> **Compile-check every one before shipping.**

## 2.1 State shape and validation

`[COMPOSED]`

```kotlin
enum class FieldKey { Name, Email, Dose, Time }

@Immutable
data class FieldError(val key: FieldKey, val message: String)

@Stable
class ReminderFormState {
    val name = TextFieldState()
    val email = TextFieldState()
    val dose = TextFieldState()
    var timeHour by mutableIntStateOf(9)
    var timeMinute by mutableIntStateOf(0)

    /** Errors are only shown after a submit attempt — never while the user is still typing. */
    var submitAttempted by mutableStateOf(false)
        private set

    val errors: List<FieldError> by derivedStateOf {
        buildList {
            if (name.text.isBlank()) add(FieldError(FieldKey.Name, "Enter a name"))
            if (!email.text.contains("@")) add(FieldError(FieldKey.Email, "Enter a valid email"))
            if (dose.text.toIntOrNull() == null) add(FieldError(FieldKey.Dose, "Enter a whole number"))
        }
    }

    fun errorFor(key: FieldKey): String? =
        if (submitAttempted) errors.firstOrNull { it.key == key }?.message else null

    /** @return the first invalid field, or null if the form is valid. */
    fun attemptSubmit(): FieldKey? {
        submitAttempted = true
        return errors.firstOrNull()?.key
    }
}

@Composable
fun rememberReminderFormState(): ReminderFormState = remember { ReminderFormState() }
```

**Why `submitAttempted`.** Showing "Enter a valid email" on the third keystroke is the single most
common form defect. Errors appear on submit; after that they update live, because at that point the
user knows what is wrong and wants feedback. `isError` is also announced by TalkBack, so a field
that flips to error state mid-typing interrupts the user's own input.

`TextFieldState` (foundation) replaces the `String` + `onValueChange` pair. `state.text` is a
`CharSequence`; `.toString()` when you need a `String`. **It is not a drop-in** — you cannot swap it
into a `value`/`onValueChange` `TextField` without migrating.

## 2.2 The fields

`[canonical-form — API names verified from the alpha21 release note; parameter shapes NOT read from
source. `TextFieldLabelPosition.Attached` is deprecated in favour of `Inside`/`Cutout`;
`OutlinedTextFieldDefaults.contentPadding()` is deprecated in favour of
`contentPaddingWithLabel()` / `contentPaddingWithoutLabel()`.]`

```kotlin
@Composable
fun FormField(
    state: TextFieldState,
    label: String,
    error: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val isError = error != null
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        // Cutout = the notch-in-the-outline look. Inside keeps the label within the container.
        // They look different — choose deliberately, do not ship `Attached`.
        labelPosition = TextFieldLabelPosition.Cutout(),
        // roundedShape is for SINGLE-LINE inputs. It looks wrong on a multi-line field.
        shape = OutlinedTextFieldDefaults.roundedShape,
        colors = OutlinedTextFieldDefaults.tonalColors(),
        // You must pick based on whether the field actually has a label. The old single
        // contentPadding() guessed.
        contentPadding = OutlinedTextFieldDefaults.contentPaddingWithLabel(),
        isError = isError,
        supportingText = when {
            error != null -> { { Text(error) } }
            supporting != null -> { { Text(supporting) } }
            else -> null
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics {
                // Belt and braces: isError sets the error semantics, but naming the field in
                // the message means the announcement is useful out of context.
                if (isError) error(error!!)
            },
    )
}
```

**Multi-line fields:** drop `shape = roundedShape` (a pill on a 5-line box looks like a bug) and use
`lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 6)`.

**Passwords:** `SecureTextField` / `OutlinedSecureTextField` (new in 1.4.0) are the
`TextFieldState`-based password fields. Use them instead of a `visualTransformation`.

## 2.3 Time picker — use the real dialog

**This contradicts developer.android.com**, whose time-picker page renders from `android/snippets`
and defines its *own* `TimePickerDialog` out of `AlertDialog`. That snippet is outdated. androidx
ships the real thing.

`[ANDROIDX — material3 samples, `TimePickerSamples.kt:152-219`, `TimePickerSwitchableSample`,
verbatim structure]`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        // NEVER assume 12/24-hour format.
        is24Hour = DateFormat.is24HourFormat(context),
    )
    var displayMode by rememberSaveable { mutableStateOf(TimePickerDisplayMode.Picker) }
    val configuration = LocalConfiguration.current

    TimePickerDialog(
        title = { TimePickerDialogDefaults.Title(displayMode = displayMode) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                // The hand-rolled AlertDialog version CANNOT do this: in TimeInput mode a
                // user can type an invalid time and the dialog must refuse it.
                enabled = state.isInputValid,
                onClick = { onConfirm(state.hour, state.minute) },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        // Named `modeToggleButton`, not `toggle` — Med's hand-rolled version uses the old name.
        modeToggleButton = {
            if (configuration.screenHeightDp.dp > TimePickerDialogDefaults.MinHeightForTimePicker) {
                TimePickerDialogDefaults.DisplayModeToggle(
                    displayMode = displayMode,
                    onDisplayModeChange = {
                        displayMode = if (displayMode == TimePickerDisplayMode.Picker) {
                            TimePickerDisplayMode.Input
                        } else {
                            TimePickerDisplayMode.Picker
                        }
                    },
                )
            }
        },
    ) {
        // The dial does not fit in landscape on most phones. Fall back to TimeInput.
        if (displayMode == TimePickerDisplayMode.Picker &&
            configuration.screenHeightDp.dp > TimePickerDialogDefaults.MinHeightForTimePicker
        ) {
            TimePicker(state = state)
        } else {
            TimeInput(state = state)
        }
    }
}
```

**alpha26 behaviour changes** — `TimePickerState` now **saves the active selection mode across state
restoration** (Iad905), so rotating mid-edit no longer resets the picker to the hour field. If you
hand-persisted this, remove your workaround; it now fights the built-in `Saver`. The same change
added an `initialSelection` parameter (exact type `[UNVERIFIED]`). alpha26 also fixed TalkBack
announcing raw numbers instead of formatted times in the scrollable `TimePicker` (Ice981).

For a date, the equivalent is `DatePickerDialog` + `rememberDatePickerState`; the same "read the
system setting, offer a text-input fallback" rules apply.

## 2.4 Submit — a `SplitButtonLayout`

Use a split button when there is **one obvious default action plus related variants**. "Save" plus
"Save and add another" / "Save as draft" is exactly that. If there is no clear default, use a plain
button plus a menu — the leading button must be worth pressing on its own.

`[COMPOSED — structure from `[CORPUS vivi-music: .../ui/component/SortHeader.kt]`, the only working
split button in the corpus]`

```kotlin
@Composable
fun SubmitSplitButton(
    enabled: Boolean,
    onSave: () -> Unit,
    onSaveAndAdd: () -> Unit,
    onSaveDraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // SplitButtonLayout is the current, undeprecated name at alpha26 — there is no
    // SplitButton composable. Verified in compose/material3/material3/api/current.txt
    // at androidx HEAD 360e8cba, 2026-08-14.
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.widthIn(min = 140.dp),
            ) {
                Icon(
                    Icons.Rounded.Save,
                    contentDescription = null,
                    modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Save")
            }
        },
        trailingButton = {
            // Note the asymmetry: leading takes onClick, trailing takes checked/onCheckedChange.
            // The trailing button IS the dropdown toggle and its checked state is real state.
            SplitButtonDefaults.TrailingButton(
                checked = menuExpanded,
                onCheckedChange = { menuExpanded = it },
                enabled = enabled,
                modifier = Modifier.semantics {
                    stateDescription = if (menuExpanded) "Expanded" else "Collapsed"
                    contentDescription = "More save options"
                },
            ) {
                val rotation by animateFloatAsState(
                    targetValue = if (menuExpanded) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "chevron",
                )
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(SplitButtonDefaults.TrailingIconSize)
                        .graphicsLayer { rotationZ = rotation },
                )
            }
        },
        modifier = modifier,
    )

    // The menu is a SIBLING of SplitButtonLayout, not a child. It anchors to its position in the
    // composition, so keep it immediately after.
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        modifier = Modifier.widthIn(min = 200.dp),
    ) {
        DropdownMenuItem(
            text = { Text("Save and add another") },
            onClick = { menuExpanded = false; onSaveAndAdd() },
        )
        DropdownMenuItem(
            text = { Text("Save as draft") },
            onClick = { menuExpanded = false; onSaveDraft() },
        )
    }
}
```

**Do not add a separate `rememberSaveable` for the chevron rotation** — animate off the trailing
button's `checked` state. Note also that `DropdownMenuItem`'s `trailingIcon` parameter became
`trailingContent` in alpha25 on the shape/checked/selected overloads.

## 2.5 The assembled form — IME insets and scroll-to-error

`[COMPOSED]`

```kotlin
@Composable
fun ReminderFormScreen(
    onSubmit: (ReminderFormState) -> Unit,
    onBack: () -> Unit,
) {
    val form = rememberReminderFormState()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    // One FocusRequester per field, keyed so scroll-to-error can address them.
    val requesters = remember { FieldKey.entries.associateWith { FocusRequester() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New reminder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // safeDrawing INCLUDES the IME, so this is one modifier instead of
                        // imePadding() + navigationBarsPadding() (which double-counts when
                        // the keyboard is up). Requires android:windowSoftInputMode="adjustResize"
                        // and enableEdgeToEdge() in the Activity.
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SubmitSplitButton(
                        enabled = true,          // never disable submit on a form: see below
                        onSave = {
                            val firstBad = form.attemptSubmit()
                            if (firstBad == null) {
                                focusManager.clearFocus()
                                onSubmit(form)
                            } else {
                                // Focusing a field inside a verticalScroll container scrolls
                                // it into view automatically. That is the cheapest correct
                                // scroll-to-error, and it also moves screen-reader focus.
                                requesters[firstBad]?.requestFocus()
                            }
                        },
                        onSaveAndAdd = { /* … */ },
                        onSaveDraft = { /* … */ },
                    )
                }
            }
        },
        // The bottom bar owns its insets, so the Scaffold must not add them again.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FormField(
                state = form.name,
                label = "Name",
                error = form.errorFor(FieldKey.Name),
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                focusRequester = requesters.getValue(FieldKey.Name),
            )
            FormField(
                state = form.email,
                label = "Email",
                error = form.errorFor(FieldKey.Email),
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                focusRequester = requesters.getValue(FieldKey.Email),
                supporting = "We only use this for reminders.",
            )
            FormField(
                state = form.dose,
                label = "Dose (mg)",
                error = form.errorFor(FieldKey.Dose),
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                focusRequester = requesters.getValue(FieldKey.Dose),
            )

            // A field-shaped button, not a text field. Never make a picker look typeable.
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonDefaults.MediumContainerHeight)
                    .focusRequester(requesters.getValue(FieldKey.Time))
                    .semantics {
                        stateDescription = "%02d:%02d".format(form.timeHour, form.timeMinute)
                    },
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(
                    ButtonDefaults.MediumContainerHeight, hasStartIcon = true, hasEndIcon = false,
                ),
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.MediumIconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
                Text("Reminder time")
                Spacer(Modifier.weight(1f))
                Text("%02d:%02d".format(form.timeHour, form.timeMinute))
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        ReminderTimePicker(
            initialHour = form.timeHour,
            initialMinute = form.timeMinute,
            onConfirm = { h, m ->
                form.timeHour = h; form.timeMinute = m; showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}
```

> `OutlinedButton(shapes = …)` mirrors `Button`'s Expressive morphing overload. The `shapes =`
> (plural) parameter is corpus-verified on `Button`, `ToggleButton`, `IconButton` and
> `FilledTonalIconButton`; on `OutlinedButton` it is `[canonical-form]`. If it does not resolve,
> pass `shape = MaterialTheme.shapes.large` and accept a static container, or hand-roll the press
> morph with `interactionSource.collectIsPressedAsState()` + `animateIntAsState` on
> `RoundedCornerShape(percent)` (`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-shapes/references/morph-recipes.md` §4.1).

**Why submit is never disabled.** A greyed-out submit button with no explanation is the worst form
failure mode: the user cannot discover what is wrong, and TalkBack announces "disabled" with no
reason. Keep it enabled, validate on press, then move focus to the first invalid field. The error
text and the focus move together explain the problem.

**Insets, three rules:**

1. `android:windowSoftInputMode="adjustResize"` in the manifest and `enableEdgeToEdge()` in the
   Activity — without both, none of the Compose inset modifiers behave.
2. `WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)` on the bottom bar covers navigation bar
   *and* IME in one modifier. Stacking `imePadding()` on top of `navigationBarsPadding()`
   double-counts when the keyboard opens.
3. `contentWindowInsets = WindowInsets(0)` on the `Scaffold` once a bar handles its own insets, or
   the padding is applied twice.

**Alternative scroll-to-error.** `Modifier.bringIntoViewRequester(requester)` +
`requester.bringIntoView()` gives finer control (you can bring the error *text* into view, not just
the field) but is still `@ExperimentalFoundationApi` on many pins. `FocusRequester.requestFocus()`
inside a scrollable is stable, moves screen-reader focus as a side effect, and is enough for almost
every form.

## 2.6 Accessibility block — form

- **Every field needs a visible `label`.** A `placeholder` is not a label — it disappears on focus
  and is announced as a hint, not a name. This is the most common form accessibility defect.
- **`isError = true` sets error semantics automatically**, but the *message* only reaches TalkBack if
  it is in `supportingText` or set explicitly with `semantics { error(message) }`. Do both: the
  supporting text is the visual channel, `error()` is the semantic one.
- **Errors appear on submit, not per keystroke.** An error announcement fired mid-typing interrupts
  the user's own input and, on some TalkBack versions, moves the reading cursor.
- **Focus order follows layout order**, driven by `ImeAction.Next` between fields and
  `ImeAction.Done` on the last. Verify with a hardware keyboard (Tab), not just by tapping — a
  `Row` of two fields reads left-to-right but Tab order can differ if you reordered with modifiers.
- **The time picker is a button, not a field.** Give it a `stateDescription` carrying the current
  value so the reader announces "Reminder time, 09:00, button", and never style it as a text field.
- **Touch targets:** `OutlinedTextField`'s default height is well over 48dp. The `SplitButtonLayout`'s
  trailing half is narrower than its leading half — verify it is ≥48dp wide; `SplitButtonDefaults`'
  per-size content paddings handle this if you do not override them.
- **What TalkBack should announce** after a failed submit: focus lands on the first invalid field and
  reads "Email, edit box, invalid entry, Enter a valid email". If it reads only "Email, edit box",
  your error message is not wired into semantics.
- **The dialog** is a focus trap. Verify predictive back dismisses it and that focus returns to the
  time button, not to the top of the form.

---

# 3. Chat screen

## 3.0 What this demonstrates

Chat is the archetypal streaming surface. The expressive payload is small and specific:
**asymmetric per-corner bubble shapes** that encode direction and grouping, `animateItem` on
insertion, and a `LoadingIndicator` for the pending turn. Everything else is restraint — the
content is user-authored, so the chrome must not compete with it.

**Deliberately calm:** no polygons, no shape morphs, no display type, no coloured gradients behind
messages. The one place expression is allowed to be loud is the pending/thinking state, because
nothing is competing with it yet.

> **Do not copy jetpacker's chat bubbles for colour.** `ChatbotScreen.kt` and `HotelSupportChat.kt`
> both declare their own hardcoded slate hex constants (`0xFF0F172A`, `0xFFF1F5F9`) instead of theme
> roles, and duplicate them across two files. Those screens are visually **off-theme**. Their
> *structure* is worth learning; their colours are not.

## 3.1 State shape and the streaming contract

`[COMPOSED — accumulate pattern verbatim in shape from
`[GOOGLE — android/ai-samples, jetpacker: ItineraryViewModel.kt:197-227]`]`

```kotlin
enum class Author { User, Assistant }

@Immutable
data class Message(
    val id: String,
    val author: Author,
    val text: String,
    val isStreaming: Boolean = false,
    val error: String? = null,
)

@Immutable
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    /** True from send until the FIRST chunk lands. Not until the stream ends. */
    val awaitingFirstToken: Boolean = false,
)

class ChatViewModel(private val client: ChatClient) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    fun send(text: String) {
        if (text.isBlank()) return

        // Stable id captured BEFORE launching. jetpacker's museum assistant uses
        // `messages.size + 2`, which collides the moment a message is ever removed —
        // its hotel-support sibling correctly uses UUIDs. Use UUIDs.
        val replyId = UUID.randomUUID().toString()

        _state.update { s ->
            s.copy(
                messages = s.messages +
                    Message(UUID.randomUUID().toString(), Author.User, text) +
                    Message(replyId, Author.Assistant, "", isStreaming = true),
                awaitingFirstToken = true,
            )
        }

        viewModelScope.launch {
            var accumulated = ""
            try {
                client.stream(text).collect { chunk ->
                    accumulated += chunk
                    _state.update { s ->
                        s.copy(
                            // isLoading flips false on the FIRST chunk, not at stream end,
                            // so the thinking placeholder is replaced the instant a token
                            // lands and the rest streams in visibly.
                            awaitingFirstToken = false,
                            messages = s.messages.map {
                                if (it.id == replyId) it.copy(text = accumulated) else it
                            },
                        )
                    }
                }
            } catch (e: Exception) {
                // Structured-concurrency hygiene. Present in all three jetpacker AI
                // ViewModels and worth teaching on its own.
                if (e is CancellationException) throw e
                _state.update { s ->
                    s.copy(messages = s.messages.map {
                        if (it.id == replyId) it.copy(error = e.message ?: "Something went wrong")
                        else it
                    })
                }
            } finally {
                _state.update { s ->
                    s.copy(
                        awaitingFirstToken = false,
                        messages = s.messages.map {
                            if (it.id == replyId) it.copy(isStreaming = false) else it
                        },
                    )
                }
            }
        }
    }
}
```

**The four rules that make streaming UI feel right**

1. Append **two** messages on send — the user's and an empty assistant placeholder. The loading state
   *is* a message; you do not need a separate list-level spinner.
2. Accumulate in a local `var` outside `collect`, then `+= chunk` and push the whole string. Compose
   re-renders the `Text` per token; no custom typewriter is needed.
3. `awaitingFirstToken = false` **on the first chunk**, not at stream end.
4. `finally { … }` as the safety net, and `if (e is CancellationException) throw e` in every catch.

## 3.2 Bubbles with asymmetric per-corner shapes

Corner asymmetry does two jobs at once: it encodes **direction** (which side the message came from)
and **grouping** (consecutive messages from one author stack with small inner corners, like a
segmented list turned on its side).

`[CORPUS LastChat: .../ui/components/chat/GroupedMessageBubble.kt — condensed from 173 lines]`

```kotlin
enum class BubblePosition { Single, First, Middle, Last }

fun bubblePosition(index: Int, groupSize: Int): BubblePosition = when {
    groupSize == 1 -> BubblePosition.Single
    index == 0 -> BubblePosition.First
    index == groupSize - 1 -> BubblePosition.Last
    else -> BubblePosition.Middle
}

@Composable
fun bubbleShape(
    position: BubblePosition,
    author: Author,
    large: Dp = 20.dp,
    small: Dp = 6.dp,
): RoundedCornerShape {
    // Assistant is left-aligned, so its small corners are on the START side.
    // User is right-aligned, so its small corners are on the END side.
    val leftAligned = author != Author.User
    return when (position) {
        BubblePosition.Single -> RoundedCornerShape(large)

        BubblePosition.First -> if (leftAligned) {
            RoundedCornerShape(large, large, large, small)   // start, end, end, start (bottom)
        } else {
            RoundedCornerShape(large, large, small, large)
        }

        BubblePosition.Middle -> if (leftAligned) {
            RoundedCornerShape(small, large, large, small)
        } else {
            RoundedCornerShape(large, small, small, large)
        }

        BubblePosition.Last -> if (leftAligned) {
            RoundedCornerShape(small, large, large, large)
        } else {
            RoundedCornerShape(large, small, large, large)
        }
    }
}
```

> `RoundedCornerShape(a, b, c, d)` is positional `topStart, topEnd, bottomEnd, bottomStart`. LastChat
> writes them as named arguments; the positional form above is condensed for readability — **use
> named arguments in real code**, this is exactly the API where a transposition is invisible.

```kotlin
@Composable
fun MessageBubble(
    message: Message,
    position: BubblePosition,
    modifier: Modifier = Modifier,
) {
    val isUser = message.author == Author.User
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = bubbleShape(position, message.author),
            color = when {
                message.error != null -> MaterialTheme.colorScheme.errorContainer
                isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = when {
                message.error != null -> MaterialTheme.colorScheme.onErrorContainer
                isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            // weight(1f, fill = false) is the crucial trick: the bubble shrinks to its
            // content but caps at 85% of the row. Without `fill = false` every bubble
            // would be full width.
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 4.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = message.error ?: message.text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
```

**Colour comes from theme role pairs** — `primaryContainer`/`onPrimaryContainer`,
`surfaceContainerHigh`/`onSurface`, `errorContainer`/`onErrorContainer`. Those pairs are guaranteed
contrast; hardcoded hex is not.

## 3.3 The thinking placeholder

Two options, and the choice is not cosmetic.

**`LoadingIndicator`** — the Expressive default. A seven-shape morph, sized to sit inside an
assistant bubble. Use it when the wait is genuinely short and indeterminate.

`[COMPOSED]`

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThinkingBubble(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = bubbleShape(BubblePosition.Single, Author.Assistant),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                // ONE polite announcement for the whole state, not one per dot.
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Assistant is thinking"
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingIndicator(modifier = Modifier.size(28.dp))
            }
        }
    }
}
```

**Three bouncing dots** — the messaging-app convention, and more legible than a 28dp morph because it
is horizontal and reads as "typing" rather than "working".

`[CORPUS LastChat: .../ui/components/chat/TypingIndicator.kt — complete file, condensed]`

```kotlin
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp,
    bounceHeight: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val animatables = remember { List(3) { Animatable(0f) } }

    animatables.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 150L)                 // stagger
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        animatables.forEach { a ->
            Box(
                Modifier
                    .offset(y = -(bounceHeight * a.value))
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.6f + 0.4f * a.value)),
            )
        }
    }
}
```

**Which to use:** `LoadingIndicator` when the assistant might take several seconds and you want the
Expressive signature; `TypingIndicator` when the product idiom is conversation and you want the
familiar messaging cue. **Never both, and never one on top of the other.** The corpus-wide rule
applies: only one indicator type per class of activity in an app.

Both of these are `infiniteRepeatable`/`Animatable` loops that keep the frame pump running. Compose
them only while pending (`if (state.awaitingFirstToken)`), and gate them on the reduce-animations
setting.

## 3.4 The input bar, pinned above the IME

`[COMPOSED — inset handling from `[GOOGLE — jetpacker: ChatbotScreen.kt]`, field styling corrected to
theme roles]`

```kotlin
@Composable
fun ChatInputBar(
    state: TextFieldState,
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                state = state,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
                    .semantics { contentDescription = "Message" },
                decorator = { inner ->
                    if (state.text.isEmpty()) {
                        Text(
                            "Message",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )

            // Conditional composition rather than `enabled = false`: an always-present but
            // permanently disabled send button is a dead stop for a screen reader.
            AnimatedVisibility(
                visible = state.text.isNotBlank() && enabled,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                FilledIconButton(
                    onClick = onSend,
                    shapes = IconButtonDefaults.shapes(),   // one-line expressive press morph
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send message")
                }
            }
        }
    }
}
```

> `BasicTextField`'s `decorator` parameter is the current name for what was `decorationBox`.
> `[canonical-form]` — verify against your foundation pin. If you would rather not hand-roll, a
> `TextField` with all four container colours and both indicator colours set to `Color.Transparent`,
> wrapped in `Modifier.clip(...).background(...)`, is the standard borderless-chat-field recipe
> `[GOOGLE — jetpacker]`.

## 3.5 The assembled chat screen

`[COMPOSED]`

```kotlin
@Composable
fun ChatScreen(state: ChatUiState, onSend: (String) -> Unit) {
    val listState = rememberLazyListState()
    val inputState = rememberTextFieldState()
    val scope = rememberCoroutineScope()

    // Auto-scroll ONLY when the user is already at the bottom. Yanking the list while
    // someone is reading scrollback is the classic chat bug — and jetpacker has no
    // auto-scroll at all, which is the opposite failure.
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last == null || last.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(state.messages.lastOrNull()?.text, state.messages.size) {
        if (atBottom && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Assistant") }) },
        bottomBar = {
            ChatInputBar(
                state = inputState,
                enabled = !state.awaitingFirstToken,
                onSend = {
                    onSend(inputState.text.toString())
                    inputState.clearText()
                },
                // safeDrawing includes the IME. One modifier, no double-counting.
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                ),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(
                items = state.messages,
                // MANDATORY for animateItem. Without a stable key Compose cannot tell an
                // insertion from a content change and nothing animates.
                key = { _, m -> m.id },
            ) { index, message ->
                // Grouping: consecutive messages from the same author share a stack.
                val prevAuthor = state.messages.getOrNull(index - 1)?.author
                val nextAuthor = state.messages.getOrNull(index + 1)?.author
                val position = when {
                    prevAuthor != message.author && nextAuthor != message.author -> BubblePosition.Single
                    prevAuthor != message.author -> BubblePosition.First
                    nextAuthor != message.author -> BubblePosition.Last
                    else -> BubblePosition.Middle
                }

                if (message.isStreaming && message.text.isEmpty()) {
                    ThinkingBubble(
                        modifier = Modifier.animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        )
                    )
                } else {
                    MessageBubble(
                        message = message,
                        position = position,
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription = buildString {
                                    append(if (message.author == Author.User) "You said: " else "Assistant said: ")
                                    append(message.error ?: message.text)
                                }
                                // Announce the completed reply once, when streaming ends.
                                if (!message.isStreaming && message.author == Author.Assistant) {
                                    liveRegion = LiveRegionMode.Polite
                                }
                            },
                    )
                }
            }
        }
    }
}
```

**`fadeInSpec`/`fadeOutSpec` are effects; `placementSpec` is spatial.** LastChat's shipped values
(`spring(dampingRatio = 0.6f, …)` for the fades) are underdamped, so the alpha rings instead of
settling — `MotionScheme`'s contract is that effects motion has no overshoot. Use the motion-scheme
specs above.

## 3.6 Accessibility block — chat

**The hard problem: a streaming message must not be announced per token.** A `Polite` live region on
a `Text` whose content changes 30 times a second is unusable. Three rules:

1. Put the live region on the **completed** message only — `if (!message.isStreaming)` above. The
   reply is announced once, in full, when the stream ends.
2. Announce the *start* separately and once, on the thinking bubble ("Assistant is thinking").
3. If users need progressive feedback, announce at sentence boundaries in the ViewModel and expose
   that as a separate `announcement: String?` field — do not derive it from the rendered `Text`.

Other rules:

- **Merge each bubble** and give it an authored `contentDescription` that names the speaker:
  "You said: …" / "Assistant said: …". Without the speaker prefix a screen-reader user cannot tell
  whose message they are on — bubble alignment and colour carry that information visually only.
- **The input field needs a real label.** A `decorator` placeholder is a hint, not a name; the
  `Modifier.semantics { contentDescription = "Message" }` above supplies it. If you use a
  `SearchBar`-style placeholder, add `Modifier.clearAndSetSemantics {}` on the placeholder `Text` so
  it is not double-announced over the field's own label.
- **The send button is conditionally composed**, so its appearance is a layout change, not a state
  change. That is correct — but verify focus does not jump when it appears mid-typing.
- **Traversal order** is list → input bar → send. The `Scaffold`'s `bottomBar` is traversed after the
  content by default, which is what you want. Do not add `traversalIndex` unless you have measured a
  problem.
- **Touch targets:** the send `FilledIconButton` is 48dp by default. Bubbles are not interactive
  here; if you add long-press actions, expose them as `CustomAccessibilityAction`s rather than
  relying on a long-press gesture, which TalkBack intercepts.
- **Errors are content, not a separate state.** Writing the failure into the same bubble (§3.1) means
  it flows through the same announcement channel as the reply. That is a genuinely good generative-UI
  pattern and it is also the accessible one.

---

# 4. Search screen

## 4.0 What this demonstrates

The **current** search-bar API — a `SearchBarState` holder plus a hoisted `inputField` slot — and the
four states a search screen actually has: idle-with-recents, results, no-results, and error.

> Search bars changed more between 1.4.0 and alpha26 than anything else in material3. Three
> generations are in the wild:
>
> | Form | Status |
> | --- | --- |
> | `SearchBar(query, onQueryChange, active, onActiveChange, …)` | pre-1.4. Doubly stale. **vivi-music still ships this** on an alpha23 pin — recognise it, migrate it, do not copy it. |
> | `SearchBar(expanded, onExpandedChange, …)` | deprecated in alpha24 |
> | `SearchBar(state, inputField, …)` + `SearchBarState` | **current** — stable since alpha24 |
>
> developer.android.com's search-bar page is also stuck on the pre-state form. "It's on the docs
> site" is not evidence an API is current.

## 4.1 The canonical shape

The load-bearing idiom: **`inputField` is hoisted into a `@Composable` val and passed to both
`SearchBar` and `ExpandedFullScreenSearchBar`.** That single val replaces all the manual
`var expanded by rememberSaveable` bookkeeping in the old form.

`[ANDROIDX — material3 samples, `SearchBarSamples.kt:83-113`, `SimpleSearchBarSample`, verbatim]`

```kotlin
@Composable
fun SimpleSearchBarSample() {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
                placeholder = {
                    Text(modifier = Modifier.clearAndSetSemantics {}, text = "Search")
                },
                leadingIcon = { SampleLeadingIcon(searchBarState, scope) },
                trailingIcon = { SampleTrailingIcon() },
            )
        }

    SearchBar(state = searchBarState, inputField = inputField)
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        SampleSearchResults(
            onResultClick = { result ->
                textFieldState.setTextAndPlaceCursorAtEnd(result)
                scope.launch { searchBarState.animateToCollapsed() }
            }
        )
    }
}
```

Four things to internalise:

- `SearchBarDefaults.InputField` takes **`textFieldState` + `searchBarState`**, not
  `query`/`onQueryChange`. If you see `query = `, it is the pre-1.4 API.
- Collapsing is `searchBarState.animateToCollapsed()` **in a coroutine scope**, not a boolean flip.
- `ExpandedFullScreenSearchBar` is a **sibling** of `SearchBar` in the composition, not a child.
- The placeholder carries `Modifier.clearAndSetSemantics {}` so it is not double-announced over the
  input field's own label. That one line is in Google's own sample for a reason.

## 4.2 The complete screen with all four states

`[COMPOSED — on the sample's skeleton]`

```kotlin
@Immutable
sealed interface SearchUiState {
    data class Idle(val recents: List<String>) : SearchUiState
    data object Loading : SearchUiState
    data class Results(val items: List<SearchResult>) : SearchUiState
    data class Empty(val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onRecentClick: (String) -> Unit,
    onClearRecents: () -> Unit,
    onRetry: () -> Unit,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .debounce(250)
            .collect(onQueryChange)
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { query ->
                onSubmit(query)
                scope.launch { searchBarState.animateToCollapsed() }
            },
            placeholder = {
                Text("Search library", modifier = Modifier.clearAndSetSemantics {})
            },
            leadingIcon = {
                // Icon in the collapsed bar, back arrow in the expanded one.
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search")
                    }
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear query")
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SearchBar(state = searchBarState, inputField = inputField)
            }
        },
    ) { padding ->
        // Collapsed content: whatever the screen shows when nobody is searching.
        BrowseContent(Modifier.padding(padding))
    }

    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        when (state) {
            is SearchUiState.Idle -> RecentSearches(
                recents = state.recents,
                onClick = { onRecentClick(it); textFieldState.setTextAndPlaceCursorAtEnd(it) },
                onClearAll = onClearRecents,
            )

            SearchUiState.Loading -> Box(
                Modifier.fillMaxWidth().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Searching"
                    },
                )
            }

            is SearchUiState.Results -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "${state.items.size} results"
                },
            ) {
                itemsIndexed(state.items, key = { _, r -> r.id }) { index, result ->
                    SegmentedListItem(
                        selected = false,
                        onClick = {
                            onResultClick(result)
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                        leadingContent = { Icon(result.icon, contentDescription = null) },
                        supportingContent = { Text(result.subtitle) },
                        shapes = AppListDefaults.segmentedShapes(index, state.items.size),
                        colors = AppListDefaults.listItemColors,
                    ) { Text(result.title) }
                }
            }

            is SearchUiState.Empty -> SearchEmptyState(
                headline = "No results for “${state.query}”",
                supporting = "Check the spelling or try a different term.",
            )

            is SearchUiState.Error -> SearchEmptyState(
                headline = "Couldn't search",
                supporting = state.message,
                actionLabel = "Try again",
                onAction = onRetry,
            )
        }
    }
}
```

> `searchBarState.currentValue == SearchBarValue.Expanded` is the shape used to branch the leading
> icon. `[UNVERIFIED]` — the state property/enum names were not read from source; the androidx sample
> factors this into a `SampleLeadingIcon(searchBarState, scope)` helper whose body was not captured.
> Check autocomplete at your pin before writing it.

Recent searches and the empty state:

```kotlin
@Composable
fun RecentSearches(recents: List<String>, onClick: (String) -> Unit, onClearAll: () -> Unit) {
    if (recents.isEmpty()) {
        SearchEmptyState(
            headline = "Search your library",
            supporting = "Find tracks, albums and playlists.",
        )
        return
    }
    Column(Modifier.semantics { isTraversalGroup = true }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Recent",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onClearAll) { Text("Clear") }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            contentPadding = PaddingValues(16.dp),
        ) {
            itemsIndexed(recents, key = { _, q -> q }) { index, query ->
                SegmentedListItem(
                    selected = false,
                    onClick = { onClick(query) },
                    leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
                    trailingContent = {
                        Icon(Icons.Rounded.NorthWest, contentDescription = null)
                    },
                    shapes = AppListDefaults.segmentedShapes(index, recents.size),
                    colors = AppListDefaults.listItemColors,
                    modifier = Modifier.animateItem(
                        fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ),
                ) { Text(query) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchEmptyState(
    headline: String,
    supporting: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp).semantics { isTraversalGroup = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(
            Modifier
                .size(96.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    // 96dp is above Cookie7Sided's ~40dp floor. Do not use a 12-lobed cookie
                    // here — below ~56dp it degrades into a slightly wobbly circle.
                    MaterialShapes.Cookie7Sided.toShape(),
                )
                .clearAndSetSemantics {}          // decoration: never announced
        )
        Spacer(Modifier.height(20.dp))
        Text(headline, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(
                    ButtonDefaults.MediumContainerHeight, hasStartIcon = false, hasEndIcon = false,
                ),
            ) { Text(actionLabel) }
        }
    }
}
```

## 4.3 Choosing a search variant

| Situation | Use |
| --- | --- |
| Search is the screen's purpose | `SearchBar` + `ExpandedFullScreenSearchBar` (§4.2) |
| Search filters a list that stays visible | `ExpandedDockedSearchBar`, or `…WithGap` for a detached results panel (`rememberSearchBarWithGapState()`) |
| Search is a persistent affordance in the top app bar | `AppBarWithSearch` + `ExpandedFullScreenContainedSearchBar` — still `@ExperimentalMaterial3Api` at alpha26, but androidx ships it as a canonical `@Sampled` function |
| Search is one action among many | an `IconButton` that navigates to a search destination |

For the `AppBarWithSearch` variant the non-obvious structural rule is that **the expanded search bar
goes inside the `topBar` slot, as a sibling of `AppBarWithSearch`** — the `topBar` lambda emits two
composables. And build **one** `SearchBarDefaults.appBarWithSearchColors(…)` from
`SearchBarDefaults.containedColors(state = …)`, then hand `.searchBarColors.inputFieldColors` to the
input field and `.searchBarColors` to the expanded bar. Full verbatim sample in
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/app-bars.md` §9.3b.

**alpha26 break:** `SearchBarScrollBehavior`'s `scrollOffset` / `scrollOffsetLimit` / `contentOffset`
**moved onto a new `SearchBarScrollState`** (Ib24e4). Any read of `behavior.scrollOffset` becomes
`behavior.scrollState.scrollOffset`. This is a hard compile break, not a deprecation.

## 4.4 Accessibility block — search

- **`ExpandedFullScreenSearchBar` renders in a `Dialog`.** That changes back handling, predictive
  back and TalkBack traversal. Verify three things explicitly: the back gesture collapses rather than
  leaving the screen; predictive back previews the collapse; and **focus lands in the input field**
  when it expands.
- **The placeholder must not be announced over the field's label.** Google's own sample puts
  `Modifier.clearAndSetSemantics {}` on the placeholder `Text` — keep it.
- **Announce result counts, not results.** A `Polite` live region on the results container with
  `contentDescription = "12 results"` tells the user the search finished. A live region on each row
  would read all twelve.
- **Announce the loading state once**, on the container that appears and disappears — never as a
  repeating region.
- **Empty and error states must be reachable and announced.** Both use real headline text (not just
  an illustration), the illustration is `clearAndSetSemantics {}`, and the whole state is an
  `isTraversalGroup` so it reads as one block.
- **Recent-search rows need a distinguishable action.** "Clear" (destructive, clears all) sits next
  to N rows that each *run* a search. Give the header `Modifier.semantics { heading() }` so heading
  navigation lands there, and consider a per-row `CustomAccessibilityAction("Remove from recents")`
  rather than a swipe.
- **Touch targets:** the clear (`X`) trailing icon inside the input field is an `IconButton` and is
  48dp; verify it does not overlap the input field's own tap area in a way that makes the field hard
  to focus at the right edge.
- **What TalkBack should announce** on expanding search: "Search library, edit box" (focus lands
  here) → "Close search, button" → "Recent, heading" → "ambient, button". After typing and a result
  set arrives: "12 results".

---

# 5. What this file exists to prevent

1. **A settings list built from `Card`s.** Three-plus cards in a column is visual noise; rows that are
   variations on one thing belong in one container. Do not nest cards inside a segmented list or
   segmented items inside a card — pick one containment level per region (§1).
2. **`ToggleButtonDefaults.shapes(...)`**, which is `DeprecationLevel.HIDDEN` and is a hard compile
   failure on alpha25+ (§1.4).
3. **A connected toggle group with no `role`**, which announces as N unrelated checkboxes (§1.4).
4. **Validation on every keystroke**, and a disabled submit button with no explanation (§2.1, §2.5).
5. **A hand-rolled `TimePickerDialog` from `AlertDialog`**, which loses `state.isInputValid` gating
   and the short-screen `TimeInput` fallback (§2.3).
6. **`imePadding()` stacked on `navigationBarsPadding()`**, which double-counts insets when the
   keyboard opens (§2.5, §3.5).
7. **A live region on a streaming message**, announcing per token (§3.6).
8. **`SearchBar(query = …, active = …)`** — two API generations stale, and still shipping in a
   current app (§4.0).
