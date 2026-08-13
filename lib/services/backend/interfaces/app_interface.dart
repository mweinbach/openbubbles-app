import 'package:bluebubbles/env.dart';
import 'package:bluebubbles/models/models.dart' show AppUpdateInfo;
import 'package:bluebubbles/services/backend/actions/app_actions.dart';
import 'package:get_it/get_it.dart';
import 'package:bluebubbles/services/isolates/global_isolate.dart';
import 'package:github/github.dart';

class AppInterface {
  static Future<AppUpdateInfo> checkForUpdate() async {
    late Map<String, dynamic> response;
    if (isIsolate) {
      response = await AppActions.checkForUpdate();
    } else {
      response = await GetIt.I<GlobalIsolate>().send<Map<String, dynamic>>(IsolateRequestType.checkForUpdate);
    }

    return AppUpdateInfo(
      available: response['available'] as bool,
      latestRelease: response['latestRelease'] as Release,
      isDesktopRelease: response['isDesktopRelease'] as bool,
      version: (response['parsedVersion'] as Map<String, String>)['version']!,
      code: (response['parsedVersion'] as Map<String, String>)['code']!,
      buildNumber: (response['parsedVersion'] as Map<String, String>)['build']!,
    );
  }
}
