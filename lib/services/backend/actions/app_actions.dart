import 'package:bluebubbles/services/backend/settings/settings_service.dart';

class AppActions {
  static Future<Map<String, dynamic>> checkForUpdate() async {
    return await SettingsSvc.getAppUpdateDict();
  }
}
