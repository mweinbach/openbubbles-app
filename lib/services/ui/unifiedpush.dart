import 'package:bluebubbles/services/services.dart';
import 'package:get/get.dart';

UnifiedPushPanelRefresh upr = Get.isRegistered<UnifiedPushPanelRefresh>()
    ? Get.find<UnifiedPushPanelRefresh>()
    : Get.put(UnifiedPushPanelRefresh());

class UnifiedPushPanelRefresh extends GetxService {
  var enabled = SettingsSvc.settings.enableUnifiedPush.value.obs;
  var endpoint = SettingsSvc.settings.endpointUnifiedPush.value.obs;

  void update(String newEndpoint) {
    endpoint.value = newEndpoint;
    enabled.value = newEndpoint != "";
    SettingsSvc.settings.endpointUnifiedPush.value = newEndpoint;
    SettingsSvc.settings.enableUnifiedPush.value = enabled.value;
    SettingsSvc.settings.saveManyAsync(['endpointUnifiedPush', 'enableUnifiedPush']);
  }
}
