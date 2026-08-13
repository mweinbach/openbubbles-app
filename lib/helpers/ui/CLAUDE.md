# helpers/ui/ — UI Utility Functions

Pure helper functions and small utility classes for the UI layer. No service dependencies; no state.

## File Routing

| File | What's inside |
|------|---------------|
| `ui_helpers.dart` | General widget utilities; custom back button with gesture support |
| `theme_helpers.dart` | `HexColor` (hex string → `Color`), `BubbleColors` theme extension, desktop window effects (Mica/acrylic) |
| `message_widget_helpers.dart` | `buildMessageSpans()` — rich text rendering with emoji scaling, mention detection, and styled spans for message bubbles |
| `attributed_body_helpers.dart` | Extracts audio transcripts from `AttributedBody` rich text; parses `Run` objects by part number |
| `reaction_helpers.dart` | `ReactionTypes` — string constants for iMessage tapbacks (`love`, `like`, `dislike`, `laugh`, `emphasize`, `question`) and their verb forms |
| `facetime_helpers.dart` | `showFaceTimeOverlay()` / `hideFaceTimeOverlay()` — incoming FaceTime call UI overlay |
| `oauth_helpers.dart` | Google OAuth flow; platform-branched: `GoogleSignIn` on Android, `DesktopWebviewAuth` on Desktop |
| `async_task.dart` | Lightweight async task wrapper for fire-and-forget UI work |

## Key Usage Notes

**Message text rendering** — always use `buildMessageSpans()` from `message_widget_helpers.dart` to render message text inside bubbles. It handles emoji font sizing, mention highlighting, and URL styling consistently.

**Bubble colors** — `BubbleColors` is a `ThemeExtension`; access via `Theme.of(context).extension<BubbleColors>()`. Don't hardcode bubble colors.

**Hex colors** — `HexColor("#RRGGBB")` or `HexColor("#AARRGGBB")` converts hex strings to Flutter `Color` objects.

**Reaction type constants** — use `ReactionTypes.LOVE`, `ReactionTypes.LIKE`, etc. instead of raw strings. The verb map (`reactionToVerb`) maps type → "loved", "liked", etc. for display.

**Desktop window effects** — window blur/acrylic effects are in `theme_helpers.dart`; only invoke on supported desktop platforms.
