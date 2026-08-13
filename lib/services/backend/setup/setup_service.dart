import 'package:bluebubbles/helpers/backend/startup_tasks.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/helpers/network/network_tasks.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:get/get.dart';

SetupService setup = Get.isRegistered<SetupService>() ? Get.find<SetupService>() : Get.put(SetupService());

class SetupService extends GetxService {
  Future<void> startSetup(int numberOfMessagesPerPage, bool skipEmptyChats, bool saveToDownloads,
      bool syncGroupChatIcons, int? syncTimeFilter) async {
    SyncSvc.numberOfMessagesPerPage = numberOfMessagesPerPage;
    SyncSvc.skipEmptyChats = skipEmptyChats;
    SyncSvc.saveToDownloads = saveToDownloads;
    SyncSvc.syncGroupChatIcons = syncGroupChatIcons;
    SyncSvc.syncTimeFilter = syncTimeFilter;

    SyncSvc.initFullSync();

    // Pre-fetch server details before the full sync so sync managers can
    // read SettingsSvc.serverDetails.value synchronously during the sync.
    await SettingsSvc.fetchServerDetails();
    await SyncSvc.startFullSync();
    await finishSetup();
  }

  Future<void> finishSetup() async {
    SettingsSvc.settings.finishedSetup.value = true;
    await SettingsSvc.settings.saveOneAsync('finishedSetup');
    await StartupTasks.onStartup();
    await NetworkTasks.onConnect();
  }
}
