import 'package:bluebubbles/services/backend/settings/shared_preferences_service.dart';

class SharedPreferencesDesktopActions {
  static const String _windowWidthKey = 'window-width';
  static const String _windowHeightKey = 'window-height';
  static const String _windowXKey = 'window-x';
  static const String _windowYKey = 'window-y';
  static const String _windowEffectKey = 'window-effect';
  static const String _splitRatioKey = 'splitRatio';

  final SharedPreferencesService service;

  SharedPreferencesDesktopActions(this.service);

  double? getWindowWidth() => service.i.getDouble(_windowWidthKey);

  double? getWindowHeight() => service.i.getDouble(_windowHeightKey);

  double? getWindowX() => service.i.getDouble(_windowXKey);

  double? getWindowY() => service.i.getDouble(_windowYKey);

  Future<void> setWindowDimensions({required double width, required double height}) async {
    await service.i.setDouble(_windowWidthKey, width);
    await service.i.setDouble(_windowHeightKey, height);
  }

  Future<void> setWindowOffsets({required double x, required double y}) async {
    await service.i.setDouble(_windowXKey, x);
    await service.i.setDouble(_windowYKey, y);
  }

  String? getWindowEffect() => service.i.getString(_windowEffectKey);

  Future<void> setWindowEffect(String effect) => service.i.setString(_windowEffectKey, effect);

  double? getSplitRatio() => service.i.getDouble(_splitRatioKey);

  Future<void> setSplitRatio(double ratio) => service.i.setDouble(_splitRatioKey, ratio);
}
