import 'package:bluebubbles/services/backend/settings/shared_preferences_service.dart';

class SharedPreferencesServerActions {
  static const String _macOSVersionKey = 'macos-version';
  static const String _macOSMinorVersionKey = 'macos-minor-version';
  static const String _serverVersionKey = 'server-version';
  static const String _serverVersionCodeKey = 'server-version-code';
  static const String _privateApiEnableTipKey = 'private-api-enable-tip';
  static const String _serverUpdateCheckKey = 'server-update-check';
  static const String _clientUpdateCheckKey = 'client-update-check';

  final SharedPreferencesService service;

  SharedPreferencesServerActions(this.service);

  int? getMacOSVersion() => service.i.getInt(_macOSVersionKey);

  int? getMacOSMinorVersion() => service.i.getInt(_macOSMinorVersionKey);

  String? getServerVersion() => service.i.getString(_serverVersionKey);

  int? getServerVersionCode() => service.i.getInt(_serverVersionCodeKey);

  Future<void> setServerDetails({
    int? macOSVersion,
    int? macOSMinorVersion,
    String? serverVersion,
    int? serverVersionCode,
  }) async {
    if (macOSVersion != null) {
      await service.i.setInt(_macOSVersionKey, macOSVersion);
    }
    if (macOSMinorVersion != null) {
      await service.i.setInt(_macOSMinorVersionKey, macOSMinorVersion);
    }
    if (serverVersion != null) {
      await service.i.setString(_serverVersionKey, serverVersion);
    }
    if (serverVersionCode != null) {
      await service.i.setInt(_serverVersionCodeKey, serverVersionCode);
    }
  }

  bool hasSeenPrivateApiEnableTip() => service.i.getBool(_privateApiEnableTipKey) ?? false;

  Future<void> markPrivateApiEnableTipShown() async {
    await service.i.setBool(_privateApiEnableTipKey, true);
  }

  String? getServerUpdateCheckVersion() => service.i.getString(_serverUpdateCheckKey);

  Future<void> setServerUpdateCheckVersion(String version) async {
    await service.i.setString(_serverUpdateCheckKey, version);
  }

  String? getClientUpdateCheckCode() => service.i.getString(_clientUpdateCheckKey);

  Future<void> setClientUpdateCheckCode(String code) async {
    await service.i.setString(_clientUpdateCheckKey, code);
  }
}
