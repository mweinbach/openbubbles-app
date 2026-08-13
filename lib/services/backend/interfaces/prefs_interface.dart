import 'package:bluebubbles/env.dart';
import 'package:bluebubbles/services/backend/actions/prefs_actions.dart';
import 'package:get_it/get_it.dart';
import 'package:bluebubbles/services/isolates/global_isolate.dart';
import 'package:bluebubbles/services/backend/settings/settings_service.dart';

class PrefsInterface {
  static Future<void> saveReplyToMessageState(String chatGuid, String? messageGuid, int? messagePart) async {
    final data = {
      'chatGuid': chatGuid,
      'messageGuid': messageGuid,
      'messagePart': messagePart,
    };

    if (isIsolate) {
      return await PrefsActions.saveReplyToMessageState(data);
    } else {
      return await GetIt.I<GlobalIsolate>().send<void>(IsolateRequestType.saveReplyToMessageState, input: data);
    }
  }

  static Future<Map<String, dynamic>?> loadReplyToMessageState(String chatGuid) async {
    final data = {
      'chatGuid': chatGuid,
    };

    if (isIsolate) {
      return PrefsActions.loadReplyToMessageState(data);
    } else {
      return await GetIt.I<GlobalIsolate>()
          .send<Map<String, dynamic>?>(IsolateRequestType.loadReplyToMessageState, input: data);
    }
  }

  static Future<void> syncAllSettings({Map<String, dynamic>? settings}) async {
    final data = {
      'settings': settings ?? SettingsSvc.settings.toMap(),
    };

    if (isIsolate) {
      return await PrefsActions.syncAllSettings(data);
    } else {
      return await GetIt.I<GlobalIsolate>().send<void>(IsolateRequestType.syncAllSettings, input: data);
    }
  }

  static Future<void> syncSettings(Map<String, dynamic> settings) async {
    if (isIsolate) {
      return await PrefsActions.syncSettings(settings);
    } else {
      return await GetIt.I<GlobalIsolate>().send<void>(IsolateRequestType.syncSettings, input: settings);
    }
  }
}
