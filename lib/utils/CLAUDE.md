# lib/utils/ — Low-Level Pure Utilities

No business logic. No service dependencies. No GetX.

## Logger (`logger/`)
- `logger.dart` — `Logger.debug/info/warn/error(msg, tag: 'Tag')` — **always use this, never `print()`**
- `task_logger.dart` — task-scoped logging with start/complete/fail lifecycle
- `outputs/debug_console_output.dart` — console sink
- `outputs/file_output_wrapper.dart` — rotating file sink (archived on app start)
- `outputs/log_stream_output.dart` — stream sink (consumed by in-app log viewer)

## Color Engine (`color_engine/`) → `color_engine/CLAUDE.md`
Advanced color space math for theme generation — not for direct use in widgets.

## Parsers (`parsers/event_payload/`)
- `api_payload_parser.dart` — deserializes server API event envelopes into typed objects

## Standalone Utils
- `string_utils.dart` — string manipulation (trim, case conversion, etc.)
- `file_utils.dart` — file I/O wrappers (copy, delete, exists)
- `crypto_utils.dart` — hashing and encryption
- `emoji.dart` — emoji data and character utilities
- `emoticons.dart` — text emoticon → emoji conversion table
- `share.dart` — system share sheet wrapper
- `window_effects.dart` — desktop window transparency (Mica, acrylic) via `flutter_acrylic`
