import 'package:bluebubbles/services/backend/settings/shared_preferences_service.dart';

class SharedPreferencesAdminActions {
  final SharedPreferencesService service;

  SharedPreferencesAdminActions(this.service);

  Future<void> setBool(String key, bool value) => service.i.setBool(key, value);

  Future<void> setString(String key, String value) => service.i.setString(key, value);

  Future<void> setInt(String key, int value) => service.i.setInt(key, value);

  Future<void> setDouble(String key, double value) => service.i.setDouble(key, value);

  Future<void> remove(String key) => service.i.remove(key);

  Set<String> getKeys() => service.i.keys;

  Object? get(String key) => service.i.get(key);

  Future<void> clearAll() => service.i.clear();
}
