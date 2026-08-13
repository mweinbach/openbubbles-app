import 'package:bluebubbles/services/backend/settings/shared_preferences_service.dart';

class SharedPreferencesThemeActions {
  static const String _selectedLightKey = 'selected-light';
  static const String _selectedDarkKey = 'selected-dark';
  static const String _previousLightKey = 'previous-light';
  static const String _previousDarkKey = 'previous-dark';

  final SharedPreferencesService service;

  SharedPreferencesThemeActions(this.service);

  String? getSelectedLightTheme() => service.i.getString(_selectedLightKey);

  String? getSelectedDarkTheme() => service.i.getString(_selectedDarkKey);

  Future<void> setSelectedThemes({String? lightTheme, String? darkTheme}) async {
    if (lightTheme != null) {
      await service.i.setString(_selectedLightKey, lightTheme);
    }
    if (darkTheme != null) {
      await service.i.setString(_selectedDarkKey, darkTheme);
    }
  }

  Future<void> setSelectedLightTheme(String themeName) => service.i.setString(_selectedLightKey, themeName);

  Future<void> setSelectedDarkTheme(String themeName) => service.i.setString(_selectedDarkKey, themeName);

  String? getPreviousLightTheme() => service.i.getString(_previousLightKey);

  String? getPreviousDarkTheme() => service.i.getString(_previousDarkKey);

  Future<void> setPreviousThemes({String? lightTheme, String? darkTheme}) async {
    if (lightTheme != null) {
      await service.i.setString(_previousLightKey, lightTheme);
    }
    if (darkTheme != null) {
      await service.i.setString(_previousDarkKey, darkTheme);
    }
  }

  Future<void> clearPreviousLightTheme() => service.i.remove(_previousLightKey);

  Future<void> clearPreviousDarkTheme() => service.i.remove(_previousDarkKey);
}
