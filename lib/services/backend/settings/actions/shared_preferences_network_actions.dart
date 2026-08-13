import 'package:bluebubbles/services/backend/settings/shared_preferences_service.dart';

class SharedPreferencesNetworkActions {
  static const String _customHeadersKey = 'customHeaders';

  final SharedPreferencesService service;

  SharedPreferencesNetworkActions(this.service);

  String? getCustomHeadersJson() => service.i.getString(_customHeadersKey);

  Future<void> setCustomHeadersJson(String value) async {
    await service.i.setString(_customHeadersKey, value);
  }
}
