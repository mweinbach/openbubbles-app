import 'package:github/github.dart' hide Source;

class AppUpdateInfo {
  final bool available;
  final Release latestRelease;
  final bool isDesktopRelease;
  final String version;
  final String code;
  final String buildNumber;

  AppUpdateInfo({
    required this.available,
    required this.latestRelease,
    required this.isDesktopRelease,
    required this.version,
    required this.code,
    required this.buildNumber,
  });
}
