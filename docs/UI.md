# Android UI

Material 3 Expressive chrome with an iMessage conversation. Not a Flutter skin, not a Cupertino
clone, not default-Material bubbles.

Load [.agents/skills/m3-expressive/SKILL.md](../.agents/skills/m3-expressive/SKILL.md) before
visual work, then only the specialist it names. Product-specific rules below win when they
conflict with generic samples.

## Design

**Budget one or two hero moments in the product, not per screen.** Conversation send-effects and
the empty-state shape are the existing heroes. New screens stay calm: containment + one accent.

Levers: size, shape, color, motion. Pull one outside a hero. Shape change is a *state* signal
(selected list row, pressed handle). Decorative morphs and looping empty-state motion are forbidden.

| Surface | Token |
|---|---|
| Single-pane canvas | `surface` |
| Two-pane canvas | `surfaceContainer` |
| List pane | `surfaceContainerLow` |
| Conversation pane | `surface` |
| Selected list row | `secondaryContainer` + `extraLarge` shape |
| Settings groups | segmented rows: `surfaceContainer` + `ListItemDefaults.segmentedShapes`, 2dp gaps, tonal icon chips |
| Find My groups | `surfaceContainer` cards or `segmentedRowShape` |
| Section labels | `titleSmall` / `onSurfaceVariant` in Settings; uppercase `labelMedium` in Find My |
| Empty-state icon | `MaterialShapes` on `primaryContainer`, **static** |

Theme: [`OpenBubblesTheme`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/ui/theme/Theme.kt)
(`MaterialExpressiveTheme`, token shape scale 4/8/12/16/20/28/32/48, dynamic color unless the user
turns it off). Motion: [`MotionPolicy.kt`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/ui/theme/MotionPolicy.kt)
— always use `defaultSpatialSpec()` / `fastEffectsSpec()` / `rememberItemAnimationSpecs()`. Those
become `snap()` when the user disabled animations. Do not hand-roll `tween()`.

**Do not hardcode hex in screens.** Use `MaterialTheme.colorScheme` except:

1. **Service identity** — [`ServiceColors.kt`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/ui/theme/ServiceColors.kt).
   iMessage blue and SMS green must not follow dynamic color. Content on those hues is **black**.
2. **Avatar seeds** — [`AvatarColors.kt`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/ui/common/AvatarColors.kt).
   Seed from handle address, else chat guid.

Bubbles ([`MessageBubble.kt`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/ui/chat/MessageBubble.kt)):
mine end-aligned (theme primary, or SMS green); theirs start-aligned `surfaceContainerHigh`;
20.dp corners, 8.dp on grouped same-author edges; width 78% of the **transcript pane**, cap 320.dp.
Status ticks only on the latest outgoing or FAILED. Group events and unsends are centered captions.
Slide a bubble toward the start edge to begin an inline reply (long-press still opens the action sheet).
A reply shows a smaller original-message bubble above it; tapping that quote focuses the thread
in the conversation instead of opening a sheet. Replies that already sit directly under their
original (or under another reply in the same run) drop the quote and share one rail: the stroke
starts on the original, turns with the same elbow as the quote marker, and continues to the last
reply instead of dying at the first.

Content width caps at 840.dp on list, transcript, and Find My.

## Architecture

```
OpenBubblesApplication          AppContext, memory trim
NativeMainActivity              edge-to-edge, Rust boot, 60s Compose release
OpenBubblesTheme
OpenBubblesApp                  Navigation3 + list-detail + onboarding gate
Screen(uiState, onEvent)        previewable, no ViewModel inside
ViewModel                       StateFlow, ports from AppGraph
AppGraph → CoreGraph            UI contracts → ObjectBox + UniFFI + SMS
```

Screens take hoisted `uiState` + lambdas. The host
([`OpenBubblesApp.kt`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/ui/OpenBubblesApp.kt))
creates the ViewModel, collects with `collectAsStateWithLifecycle`, and wires `AppGraph`.
Do not call `viewModel()` inside a previewed leaf.

UI talks to [`Repository.kt`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/data/Repository.kt)
contracts via [`AppGraph`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/data/FakeRepository.kt)
(`object AppGraph`). Composables must not import `uniffi.*` or `app.openbubbles.db.*`.
Settings / login / new-chat still touch `CoreGraph` — that is the exception, not the template.

[`NativeMainActivity`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/NativeMainActivity.kt)
disposes Compose after 60s in the background so the sticky push service does not pin the UI.
String `Routes` exist only to persist that teardown. Navigation itself is typed `NavKey`s.

## Navigation

Navigation3: `rememberNavBackStack` + `NavDisplay` + `ListDetailSceneStrategy`.
Do not add Navigation2 / Accompanist.

- Compact width, or compact height (phone landscape, <480dp): list and chat are full-screen destinations.
- Medium+ width with at least medium height: list | chat (`messagingListDetailDirective`).
- ~1200.dp: chat info is a third pane. Otherwise it uses detail-pane metadata (full-screen on phones, swaps beside the list on two-pane). Do not levitate it.
- `openChat()` **swaps** the open conversation. Back from a chat always lands on the list.
- Find My and Settings live in the chat-list **profile menu**, not extra top-bar icons or a bottom nav.
- Opening a chat from Settings/Find My pops those keys first so the detail pane is not orphaned.
- Selected-row highlight is multi-pane only.
- Chat ViewModels are scoped with `rememberViewModelStoreNavEntryDecorator()` so they die on pop.

Onboarding (`native_setup.onboarding_complete`) is a full-screen gate *before* `NavDisplay`.

## Screens

| Destination | Files | State |
|---|---|---|
| Chat list | `ui/chatlist/ChatListScreen.kt`, `ChatListViewModel.kt` | VM + `ChatListRepository`; long-press selects many chats for archive/delete; single selection's action sheet adds pin/mute and the per-chat "Send from" override |
| Search | `ui/search/SearchScreen.kt`, `SearchViewModel.kt` | VM + `SearchRepository`; chats/people/messages/links sections, match highlighting |
| Archived chats | same list screen, `ChatListKind.Archive` | Opened from Settings; unarchive or delete |
| Conversation | `ui/chat/ChatScreen.kt`, `ChatViewModel.kt`, `MessageBubble.kt`, `AttachmentBubbles.kt` | VM + send/action/attachment/typing ports |
| New chat | `ui/chatcreator/NewChatScreen.kt` | local + `CoreGraph.findOrCreateChat` |
| Chat info | `ui/chatinfo/ChatInfoScreen.kt`, `ContactSheet.kt` | hoisted `AppGraph.chatInfo*`; 1:1 shows the contact card, group participants open a contact sheet |
| Settings | `ui/settings/SettingsScreen.kt`, `SettingsRows.kt` | fat composable; use `SettingsGroup` / `SettingsToggleItem` |
| Find My | `ui/findmy/FindMyScreen.kt`, `FindMyViewModel.kt` | VM + `FindMyPort` |
| Attachment viewer | `ui/attachmentviewer/AttachmentViewerScreen.kt` | `AttachmentProvider` |
| Login / provision | `ui/login/LoginScreen.kt`, `LoginViewModel.kt`, `ProvisionScreen.kt` | VM + `RustLoginHandle` |
| Onboarding | `ui/onboarding/*` | local steps; embeds login |
| FaceTime | `facetime/FaceTimeActivity.kt` | Views + WebView, not Compose |

Shared primitives: `ui/common/` (avatars, pills, segmented shapes, shared elements, dates).
Effects: `ui/effects/SendEffects.kt`, `EffectPicker.kt`.

Screenshot fixtures: `app-native/src/screenshotTest/.../ScreenshotPreviews.kt` (fixed timestamps).

## Visual change evidence

A screenshot report is a regression comparison, not a complete product oracle. Before editing from
a screenshot or tester annotation, record the current Compose destination, window/pane state,
layout direction, message/account state, theme, renderer host, and the exact visual relationship the
change must preserve. Confirm the reference still describes current `HEAD`; old screenshots and
patches often predate intervening layout fixes.

For directional or stateful conversation UI, build the smallest applicable matrix before changing
geometry or goldens:

| Dimension | Cases to consider |
|---|---|
| Ownership/direction | mine → mine, theirs → theirs, mine → theirs, theirs → mine |
| Conversation | 1:1, group with sender/avatar chrome, SMS/MMS where applicable |
| Content | short/long text, image/media, unavailable/deleted original, reaction/status rows |
| Layout | LTR, RTL, compact phone, landscape/compact height, list-detail/expanded pane |
| Appearance | light, dark, dynamic color, large text/font scaling, display scaling |
| Interaction | default, pressed/focused/selected, TalkBack/keyboard, reduced motion |

Do not add every cross-product as a golden. Choose fixtures that make every changed branch visible,
then cover pure branching/geometry/accessibility policy with focused unit tests. At minimum, a
direction-sensitive reply change needs both opposite directions and every newly supported same-side
case; a group-specific change needs a direct-chat counterexample.

### Geometry and layout rules

- Derive connectors, overlays, and shared-element anchors from the measured bounds of the elements
  they join. Do not duplicate bubble positions with independent fixed rails or assumed row heights.
- Keep coordinate systems explicit. Convert root/window bounds into the drawing canvas once and
  test the pure transformation separately.
- Insets must account for stroke width and rounded bubble corners so caps are neither clipped nor
  hidden beneath a `Surface`.
- Same-side and opposite-side relationships are different geometry contracts. RTL mirrors the
  coordinate system; it must not silently swap message ownership.
- A visual relationship that carries meaning needs sufficient contrast. Interactive labels retain
  48.dp minimum targets even when their visible text/icon is compact.
- Do not fix a golden by increasing renderer tolerance until the measured drift is proven sparse,
  host-specific, and below a deliberate threshold. Product-level differences must continue to fail.

### Evidence ladder for UI

1. Pure tests prove direction selection, coordinates, semantics, and reduced-motion policy.
2. Focused screenshots prove selected light/dark fixtures on the current renderer.
3. Human inspection proves the intended hierarchy rather than merely pixel similarity.
4. Device/emulator evidence proves gestures, IME, TalkBack, font/display scaling, fold posture,
   predictive back, and platform rendering.

Run `:app-native:updateDebugScreenshotTest` only for fixtures intentionally changed by the task.
Inspect every updated image, then run `:app-native:validateDebugScreenshotTest`. Never sweep
unrelated goldens into a visual commit. Report screenshot and device evidence separately.

## Recipes

**New destination.** Add `Routes` + `@Serializable NavKey` + `toRoute`/`routeToKey` + `entry<Key>`
in `OpenBubblesApp.kt`. List-adjacent panes use `listPane` / `detailPane` / `extraPane()`.
Navigate with `navigateTo`; conversations must go through `openChat()`.

**New screen.** Stateless `FooScreen(uiState, onEvent, modifier)`. `Scaffold` + `widthIn(max = 840.dp)`.
Back chevron only when `showBackButton` (false in multi-pane for list-detail children). Preview under
`OpenBubblesTheme`. IO in `withContext(Dispatchers.IO)` / `produceState`, never during composition.

**New bubble / attachment kind.** Extend the *core* DTO and map in `CoreGraph` (`coreMessageToUi` /
`enrichWithEntityDetails`). Branch in `MessageBubble` or `AttachmentBubbles`. Keep grouping via
`buildConversationEntries` in `ChatScreen.kt`. Viewer: `AttachmentKey` + `sharedAttachment(guid)`.
Long-press is part-aware — pass the Apple part index.

**Settings row.** `SettingsGroup` + `SettingsInfoItem` / `SettingsActionItem` /
`SettingsToggleItem` in `SettingsRows.kt`. Compact is a titled single column
(max 600.dp). Medium+ is a 300.dp category rail plus a detail column capped at
520.dp. Every row takes a leading `icon` rendered as a 40.dp tonal chip;
`SettingsRowTone` marks healthy (`Active`) or problem/destructive (`Error`)
states. Actions show a chevron; toggles show a switch; status rows have
neither. Rows are segmented (`segmentedShapes(index, count)` + `SegmentedGap`),
so press state morphs corners — do not flatten the shapes back to rectangles.
Toggle subtitles describe the setting and must not change with its state —
state-dependent copy reflows the row height on tap. Persist through
`AppearancePrefs`, `MessagingPrefs`, `NotifPrefs`, or `BatterySaver`.
Do not invent a second preferences API.

**Tapbacks.** Indexes `❤️ 👍 👎 😂 ‼️ ❓` then custom at 6. Hidden on SMS chats.

## Anti-patterns

- Protocol, ObjectBox, or temp-guid staging in Compose. That is `CoreGraph` / `:core`.
- GetX, `Obx`, Flutter skins, `Navigator.of`, stacking chats on the back stack.
- Shared-element chat-row transitions in multi-pane (keys collide).
- Raw `MaterialTheme.motionScheme` without `MotionPolicy` (breaks reduce-motion).
- Inventing iMessage/SMS bubble colors under dynamic color.
- Desktop is not a copy-paste target: no Nav3, no `OpenBubblesTheme`, binds `ChatRepo` directly.

Desktop parity is unfinished. Share `core/` and login *contracts*, not Android composables.
