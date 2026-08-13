import 'package:bluebubbles/services/backend/settings/shared_preferences_service.dart';

class SharedPreferencesFirebaseActions {
  static const String _projectIDKey = 'projectID';
  static const String _storageBucketKey = 'storageBucket';
  static const String _apiKeyKey = 'apiKey';
  static const String _firebaseURLKey = 'firebaseURL';
  static const String _clientIDKey = 'clientID';
  static const String _applicationIDKey = 'applicationID';

  final SharedPreferencesService service;

  SharedPreferencesFirebaseActions(this.service);

  String? getProjectID() => service.i.getString(_projectIDKey);

  String? getStorageBucket() => service.i.getString(_storageBucketKey);

  String? getApiKey() => service.i.getString(_apiKeyKey);

  String? getFirebaseURL() => service.i.getString(_firebaseURLKey);

  String? getClientID() => service.i.getString(_clientIDKey);

  String? getApplicationID() => service.i.getString(_applicationIDKey);

  Future<void> saveConfig({
    String? projectID,
    String? storageBucket,
    String? apiKey,
    String? firebaseURL,
    String? clientID,
    String? applicationID,
  }) async {
    await _setOrRemoveString(_projectIDKey, projectID);
    await _setOrRemoveString(_storageBucketKey, storageBucket);
    await _setOrRemoveString(_apiKeyKey, apiKey);
    await _setOrRemoveString(_firebaseURLKey, firebaseURL);
    await _setOrRemoveString(_clientIDKey, clientID);
    await _setOrRemoveString(_applicationIDKey, applicationID);
  }

  Future<void> clearConfig() async {
    await service.i.remove(_projectIDKey);
    await service.i.remove(_storageBucketKey);
    await service.i.remove(_apiKeyKey);
    await service.i.remove(_firebaseURLKey);
    await service.i.remove(_clientIDKey);
    await service.i.remove(_applicationIDKey);
  }

  Future<void> _setOrRemoveString(String key, String? value) async {
    if (value == null) {
      await service.i.remove(key);
      return;
    }

    await service.i.setString(key, value);
  }
}
