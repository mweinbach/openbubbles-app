# lib/ — Dart Source Code

## Architecture Layers
| Directory | Role |
|-----------|------|
| `app/` | UI widgets, layouts, screens → `app/CLAUDE.md` |
| `services/` | Business logic, state, network → `services/CLAUDE.md` |
| `database/` | Models, persistence → `database/CLAUDE.md` |
| `helpers/` | Cross-cutting utilities → `helpers/CLAUDE.md` |
| `models/` | Cross-cutting DTOs and view models → `models/CLAUDE.md` |
| `utils/` | Low-level pure utilities (logger, color engine, parsers) → `utils/CLAUDE.md` |
| `generated/` | Auto-generated ObjectBox code — do not edit |

## Entry Points
- `main.dart` — app init, theme, routing
- `env.dart` — environment config

## Helpers & Utils Routing
- UI utilities: `helpers/ui/`
- Type extensions: `helpers/types/extensions/extensions.dart`
- Constants: `helpers/types/constants.dart`
- Logging: `utils/logger/logger.dart`
- Color engine: `utils/color_engine/`
- Parsers: `utils/parsers/`
