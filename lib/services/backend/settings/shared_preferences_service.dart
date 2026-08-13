import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences/util/legacy_to_async_migration_util.dart';
import 'package:bluebubbles/services/backend/settings/actions/shared_preferences_admin_actions.dart';
import 'package:bluebubbles/services/backend/settings/actions/shared_preferences_database_actions.dart';
import 'package:bluebubbles/services/backend/settings/actions/shared_preferences_desktop_actions.dart';
import 'package:bluebubbles/services/backend/settings/actions/shared_preferences_firebase_actions.dart';
import 'package:bluebubbles/services/backend/settings/actions/shared_preferences_messaging_actions.dart';
import 'package:bluebubbles/services/backend/settings/actions/shared_preferences_network_actions.dart';
import 'package:bluebubbles/services/backend/settings/actions/shared_preferences_server_actions.dart';
import 'package:bluebubbles/services/backend/settings/actions/shared_preferences_system_actions.dart';
import 'package:bluebubbles/services/backend/settings/actions/shared_preferences_theme_actions.dart';
import 'package:get_it/get_it.dart';

// ignore: non_constant_identifier_names
SharedPreferencesService get PrefsSvc => GetIt.I<SharedPreferencesService>();

class SharedPreferencesService {
  @Deprecated('Use categorized helpers on PrefsSvc instead of raw i access')
  late final SharedPreferencesWithCache i;
  late final SharedPreferencesAdminActions admin;
  late final SharedPreferencesDatabaseActions database;
  late final SharedPreferencesDesktopActions desktop;
  late final SharedPreferencesFirebaseActions firebase;
  late final SharedPreferencesThemeActions theme;
  late final SharedPreferencesMessagingActions messaging;
  late final SharedPreferencesNetworkActions network;
  late final SharedPreferencesServerActions server;
  late final SharedPreferencesSystemActions system;

  Future<void> init({bool headless = false}) async {
    const sharedPreferencesOptions = SharedPreferencesOptions();
    final SharedPreferences prefs = await SharedPreferences.getInstance();
    await migrateLegacySharedPreferencesToSharedPreferencesAsyncIfNecessary(
      legacySharedPreferencesInstance: prefs,
      sharedPreferencesAsyncOptions: sharedPreferencesOptions,
      migrationCompletedKey: 'migrationCompleted',
    );

    i = await SharedPreferencesWithCache.create(cacheOptions: const SharedPreferencesWithCacheOptions());
    admin = SharedPreferencesAdminActions(this);
    database = SharedPreferencesDatabaseActions(this);
    desktop = SharedPreferencesDesktopActions(this);
    firebase = SharedPreferencesFirebaseActions(this);
    theme = SharedPreferencesThemeActions(this);
    messaging = SharedPreferencesMessagingActions(this);
    network = SharedPreferencesNetworkActions(this);
    server = SharedPreferencesServerActions(this);
    system = SharedPreferencesSystemActions(this);
  }
}
