# windows/ — Windows Native

## C++ Runner (`runner/`)
- `main.cpp` — entry point: COM init, `DartProject` setup, 1280×720 `FlutterWindow`, Win32 message loop
- `flutter_window.cpp/h` — `FlutterViewController` host; plugin registration on `OnCreate`
- `win32_window.cpp/h` — Win32 window base class (creation, message routing)
- `utils.cpp/h` — UTF-8 / UTF-16 helpers

## Installer
- `bluebubbles_installer_script.iss` — Inno Setup installer definition
- `CodeDependencies.iss` — installer dependency declarations

## Key Flutter-Side Files for Windows
- `lib/utils/window_effects.dart` — Mica/acrylic transparency (`flutter_acrylic`)
- `lib/app/wrappers/titlebar_wrapper.dart` — custom window frame (`bitsdojo_window`)
- `lib/services/ui/navigator/navigator_service.dart` — Windows taskbar integration (`windows_taskbar`)
- `lib/services/backend/sync/full_sync_manager.dart` — taskbar progress bar during sync

## Build
Target: x64. Binary: `bluebubbles_app.exe`. CMake build system.
MSIX identity: `23344BlueBubbles.BlueBubbles`
