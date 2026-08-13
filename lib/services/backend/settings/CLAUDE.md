# services/backend/settings/ — Settings Persistence

## Files

### `settings_service.dart` — `SettingsService` / `SettingsSvc`

GetIt singleton. The authoritative source for all app settings at runtime.

- Holds the `Settings` object (defined in `lib/database/global/settings.dart`) as `SettingsSvc.settings`
- All settings fields are `Rx*` observables — widgets can `Obx()` directly on any field
- Reads/writes settings via `PrefsInterface` → `PrefsActions` → ObjectBox in the GlobalIsolate

**Reading a setting anywhere:**
```dart
SettingsSvc.settings.enablePrivateAPI.value
```

**Saving a setting change:**
```dart
SettingsSvc.settings.myField.value = newValue;
await SettingsSvc.saveSettings();   // or use saveSettings() helper from settings_helpers.dart
```

Also owns `AppUpdateInfo` and `ServerUpdateInfo` models for update check state.

---

### `shared_preferences_service.dart` — `SharedPreferencesService` / `PrefsSvc`

Thin wrapper around Flutter's `SharedPreferences` for simple key-value storage that must be available before ObjectBox initializes (e.g. during background isolate startup).

Used for: callback handle storage (background isolate registration), install timestamp, and any other primitive values that need to survive a cold start before the database is ready.

```dart
PrefsSvc.desktop.setWindowDimensions(width: 1280, height: 720);
final themeName = PrefsSvc.theme.getSelectedDarkTheme();
```

Use category helpers instead of `PrefsSvc.i` direct access. Keep raw `PrefsSvc.i` usage restricted to helper implementation internals.

For app settings (everything in the `Settings` class), use `SettingsSvc` instead. `PrefsSvc` is only for low-level bootstrap values.
