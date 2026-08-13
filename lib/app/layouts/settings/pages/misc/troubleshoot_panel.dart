import 'package:bluebubbles/app/layouts/chat_selector_view/chat_selector_view.dart';
import 'package:bluebubbles/app/layouts/settings/pages/misc/logging_panel.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/log_level_selector.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/backend/settings_helpers.dart';
import 'package:bluebubbles/main.dart';
import 'package:bluebubbles/services/backend/sync/chat_sync_manager.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/ui/extension_service.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/utils/share.dart';
import 'package:disable_battery_optimization/disable_battery_optimization.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';
import 'package:url_launcher/url_launcher.dart';

class TroubleshootPanel extends StatefulWidget {
  const TroubleshootPanel({super.key});

  @override
  State<StatefulWidget> createState() => _TroubleshootPanelState();
}

class _TroubleshootPanelState extends State<TroubleshootPanel> with ThemeHelpers {
  final RxnBool resyncingHandles = RxnBool();
  final RxnBool resyncingChats = RxnBool();
  final RxInt logFileCount = 0.obs;
  final RxInt logFileSize = 0.obs;
  final RxBool optimizationsDisabled = false.obs;
  final TextEditingController participantController = TextEditingController();

  bool isExportingLogs = false;
  final RxnBool reregisteringIds = RxnBool();

  @override
  void initState() {
    super.initState();
    _refreshLogStats();

    // Check if battery optimizations are disabled
    if (Platform.isAndroid) {
      DisableBatteryOptimization.isAllBatteryOptimizationDisabled.then((value) {
        optimizationsDisabled.value = value ?? false;
      });
    }
  }

  void _refreshLogStats() {
    int count = 0;
    int sizeKb = 0;

    final Directory logDir = Directory(Logger.logDir);
    if (logDir.existsSync()) {
      final List<FileSystemEntity> files = logDir.listSync();
      final List<FileSystemEntity> logFiles = files.where((file) => file.path.endsWith(".log")).toList();
      count = logFiles.length;

      for (final file in logFiles) {
        sizeKb += file.statSync().size ~/ 1024;
      }
    }

    logFileCount.value = count;
    logFileSize.value = sizeKb;
  }

  @override
  Widget build(BuildContext context) {
    bool isWebOrDesktop = kIsWeb || kIsDesktop;
    return SettingsScaffold(
        title: "Developer Tools",
        initialHeader: (isWebOrDesktop) ? "Contacts" : "Logging",
        iosSubtitle: iosSubtitle,
        materialSubtitle: materialSubtitle,
        tileColor: tileColor,
        headerColor: headerColor,
        bodySlivers: [
          SliverList(
            delegate: SliverChildListDelegate(
              <Widget>[
                if (isWebOrDesktop)
                  SettingsSection(
                    backgroundColor: tileColor,
                    children: [
                      SettingsTile(
                        onTap: () async {
                          final RxList<String> log = <String>[].obs;
                          showDialog(
                              context: context,
                              builder: (context) => AlertDialog(
                                    backgroundColor: context.theme.colorScheme.surface,
                                    contentPadding: const EdgeInsets.symmetric(horizontal: 20),
                                    titlePadding: const EdgeInsets.only(top: 15),
                                    title: Text("Fetching contacts...", style: context.theme.textTheme.titleLarge),
                                    content: Padding(
                                      padding: const EdgeInsets.all(8.0),
                                      child: SizedBox(
                                        width: NavigationSvc.width(context) * 4 / 5,
                                        height: context.height * 1 / 3,
                                        child: Container(
                                          decoration: BoxDecoration(
                                            borderRadius: BorderRadius.circular(25),
                                            color: context.theme.colorScheme.surface,
                                          ),
                                          padding: const EdgeInsets.all(10),
                                          child: Obx(() => ListView.builder(
                                                physics: const AlwaysScrollableScrollPhysics(
                                                    parent: BouncingScrollPhysics()),
                                                itemBuilder: (context, index) {
                                                  return Text(
                                                    log[index],
                                                    style: TextStyle(
                                                      color: context.theme.colorScheme.onSurface,
                                                      fontSize: 10,
                                                    ),
                                                  );
                                                },
                                                itemCount: log.length,
                                              )),
                                        ),
                                      ),
                                    ),
                                  ));
                          await ContactsSvcV2.fetchNetworkContacts(logger: (newLog) {
                            log.add(newLog);
                          });
                        },
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.group,
                          materialIcon: Icons.contacts,
                        ),
                        title: "Fetch Contacts With Verbose Logging",
                        subtitle:
                            "This will fetch contacts from the server with extra info to help devs debug contacts issues",
                      ),
                    ],
                  ),
                if (isWebOrDesktop)
                  SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Logging"),
                SettingsSection(backgroundColor: tileColor, children: [
                  const LogLevelSelector(),
                  SettingsTile(
                    title: "View Latest Log",
                    subtitle: "View the latest log file. Useful for debugging issues, in app.",
                    leading: const SettingsLeadingIcon(
                      iosIcon: CupertinoIcons.doc_append,
                      materialIcon: Icons.document_scanner_rounded,
                      containerColor: Colors.blueAccent,
                    ),
                    onTap: () {
                      NavigationSvc.pushSettings(
                        context,
                        const LoggingPanel(),
                      );
                    },
                    trailing: const NextButton(),
                  ),
                  if (Platform.isAndroid) const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (Platform.isAndroid)
                    Obx(
                      () => SettingsTile(
                          leading: const SettingsLeadingIcon(
                            iosIcon: CupertinoIcons.share_up,
                            materialIcon: Icons.share,
                            containerColor: Colors.green,
                          ),
                          title: "Download / Share Logs",
                          subtitle: "${logFileCount.value} log file(s) | ${logFileSize.value} KB",
                          onTap: () async {
                            _refreshLogStats();
                            if (logFileCount.value == 0) {
                              showSnackbar("No Logs", "There are no logs to download!");
                              return;
                            }

                            if (isExportingLogs) return;
                            isExportingLogs = true;

                            try {
                              showSnackbar("Please Wait", "Compressing ${logFileCount.value} log file(s)...");
                              String filePath = await Logger.compressLogs();
                              final String fileName = File(filePath).uri.pathSegments.last;

                              try {
                                final String savedPath = await FilesystemSvc.saveToDownloads(
                                  File(filePath),
                                  mimeType: 'application/zip',
                                );
                                showSnackbar("Logs Saved", "Saved $fileName to your Downloads folder.");
                                if (kIsDesktop) await launchUrl(Uri.file(savedPath));
                              } catch (_) {
                                // saveToDownloads failed on Android — fall back to share sheet.
                                Share.files([filePath], mimeType: 'application/zip');
                              }
                            } catch (ex, stacktrace) {
                              Logger.error("Failed to export logs!", error: ex, trace: stacktrace);
                              showSnackbar("Failed to export logs!", "Error: ${ex.toString()}");
                            } finally {
                              isExportingLogs = false;
                              _refreshLogStats();
                            }
                          }),
                    ),
                  if (kIsDesktop) const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  if (kIsDesktop)
                    SettingsTile(
                        leading: const SettingsLeadingIcon(
                          iosIcon: CupertinoIcons.doc,
                          materialIcon: Icons.file_open,
                        ),
                        title: "Open Logs",
                        subtitle: Logger.logDir,
                        onTap: () async {
                          final File logFile = File(Logger.logDir);
                          if (logFile.existsSync()) {
                            logFile.createSync(recursive: true);
                          }
                          await launchUrl(Uri.file(logFile.path));
                        }),
                  const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                  SettingsTile(
                      leading: const SettingsLeadingIcon(
                        iosIcon: CupertinoIcons.trash,
                        materialIcon: Icons.delete,
                        containerColor: Colors.redAccent,
                      ),
                      title: "Clear Logs",
                      subtitle: "Deletes all stored log files.",
                      onTap: () async {
                        Logger.clearLogs();
                        showSnackbar("Logs Cleared", "All logs have been deleted.");
                        _refreshLogStats();
                      }),
                  if (kIsDesktop) const SettingsDivider(),
                  if (kIsDesktop)
                    SettingsTile(
                      leading: const SettingsLeadingIcon(
                        iosIcon: CupertinoIcons.folder,
                        materialIcon: Icons.folder,
                      ),
                      title: "Open App Data Location",
                      subtitle: FilesystemSvc.appDocDir.path,
                      onTap: () async => await launchUrl(Uri.file(FilesystemSvc.appDocDir.path)),
                    ),
                ]),
                if (Platform.isAndroid)
                  SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Optimizations"),
                if (Platform.isAndroid)
                  SettingsSection(backgroundColor: tileColor, children: [
                    SettingsTile(
                        onTap: () async {
                          if (optimizationsDisabled.value) {
                            showSnackbar(
                                "Already Disabled", "Battery optimizations are already disabled for BlueBubbles");
                            return;
                          }

                          final optsDisabled = await disableBatteryOptimizations();
                          if (!optsDisabled) {
                            showSnackbar("Error", "Battery optimizations were not disabled. Please try again.");
                          }
                        },
                        leading: Obx(() => SettingsLeadingIcon(
                              iosIcon: CupertinoIcons.battery_25,
                              materialIcon: Icons.battery_5_bar,
                              containerColor: optimizationsDisabled.value ? Colors.green : Colors.redAccent,
                            )),
                        title: "Disable Battery Optimizations",
                        subtitle:
                            "Allow app to run in the background via the OS. This may not do anything on some devices.",
                        trailing: Obx(() => !optimizationsDisabled.value
                            ? const NextButton()
                            : Icon(Icons.check, color: context.theme.colorScheme.outline))),
                  ]),
                SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Troubleshooting"),
                SettingsSection(backgroundColor: tileColor, children: [
                  SettingsTile(
                      onTap: () async {
                        NavigationSvc.pushSettings(
                          context,
                          ChatSelectorView(
                            onSelect: (Chat chat) async {
                              final bool? confirmed = await showBBDialog<bool>(
                                context: context,
                                title: "Delete Chat?",
                                body:
                                    "This will permanently delete the chat, all of its messages, and all of its participants (handles). This cannot be undone.",
                                actions: [
                                  BBDialogAction(
                                    text: "Cancel",
                                    onPressed: () => Navigator.of(context, rootNavigator: true).pop(false),
                                  ),
                                  BBDialogAction(
                                    text: "Delete",
                                    isDestructive: true,
                                    color: Colors.redAccent,
                                    onPressed: () => Navigator.of(context, rootNavigator: true).pop(true),
                                  ),
                                ],
                              );

                              if (confirmed != true) return;

                              try {
                                await ChatsSvc.deleteChat(chat, deleteHandles: true);
                                showSnackbar(
                                  "Chat Deleted",
                                  "Successfully deleted chat and all associated data.",
                                );
                              } catch (ex, stacktrace) {
                                Logger.error("Failed to delete chat!", error: ex, trace: stacktrace);
                                showSnackbar("Failed to Delete Chat", "Error: ${ex.toString()}");
                              }
                            },
                          ),
                        );
                      },
                      leading: const SettingsLeadingIcon(
                        iosIcon: CupertinoIcons.chat_bubble_2,
                        materialIcon: Icons.delete_forever,
                        containerColor: Colors.redAccent,
                      ),
                      title: "Delete a Chat",
                      subtitle:
                          "Permanently deletes a selected chat, all its messages, and all its participants. Use this to simulate a brand-new chat arrival."),
                  const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                ]),
                SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "OpenBubbles"),
                SettingsSection(backgroundColor: tileColor, children: [
                  SettingsTile(
                    leading: const SettingsLeadingIcon(
                      iosIcon: CupertinoIcons.share,
                      materialIcon: Icons.share,
                    ),
                    title: "Export OB logs",
                    subtitle:
                        "Last 24-48 hours saved. Contains sensitive information (such as messages and identifiers); do not share publicly.",
                    onTap: () async {
                      try {
                        final Directory file = Directory(
                          Platform.isAndroid
                              ? "${FilesystemSvc.appDocDir.path}/../files/logs"
                              : "${FilesystemSvc.appDocDir.path}/logs",
                        );
                        final List<FileSystemEntity> entities = await file.list().toList();
                        final int current = entities.indexWhere((element) => element.path.endsWith("CURRENT.log"));
                        if (current == -1) {
                          showSnackbar("No Logs", "There are no logs to export!");
                          return;
                        }

                        final FileSystemEntity item = entities.removeAt(current);
                        final Uint8List end = await File(item.path).readAsBytes();
                        final BytesBuilder b = BytesBuilder();
                        if (entities.isNotEmpty) {
                          final Uint8List next = await File(entities.first.path).readAsBytes();
                          b.add(next);
                        }
                        b.add(end);
                        final Uint8List total = b.toBytes();

                        final String date = DateTime.now().toIso8601String().split('T').first;
                        final File logFile = File("${FilesystemSvc.appDocDir.path}/openbubbles-logs-$date.log");
                        if (logFile.existsSync()) logFile.deleteSync();

                        await logFile.writeAsBytes(total);

                        final String newPath = await FilesystemSvc.saveToDownloads(logFile);
                        logFile.deleteSync();

                        showSnackbar(
                          "Logs Exported",
                          "Logs have been exported to your downloads folder. Tap here to share it.",
                          durationMs: 5000,
                          onTap: (snackbar) async {
                            Share.files([newPath]);
                          },
                        );
                      } catch (ex, stacktrace) {
                        Logger.error("Failed to export OB logs!", error: ex, trace: stacktrace);
                        showSnackbar("Failed to export logs!", "Error: ${ex.toString()}");
                      }
                    },
                  ),
                ]),
                if (!kIsWeb && BackendSvc.getRemoteService() != null)
                  SettingsHeader(
                      iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Database Re-syncing"),
                if (!kIsWeb && BackendSvc.getRemoteService() != null)
                  SettingsSection(backgroundColor: tileColor, children: [
                    SettingsTile(
                        title: "Sync Handles & Contacts",
                        subtitle:
                            "Run this troubleshooter if you are experiencing issues with missing or incorrect contact names and photos",
                        onTap: () async {
                          resyncingHandles.value = true;
                          try {
                            final handleSyncer = HandleSyncManager();
                            await handleSyncer.start();
                            EventDispatcherSvc.emit("refresh-all", null);

                            showSnackbar("Success",
                                "Successfully re-synced handles! You may need to close and re-open the app for changes to take effect.");
                          } catch (ex, stacktrace) {
                            Logger.error("Failed to reset contacts!", error: ex, trace: stacktrace);

                            showSnackbar("Failed to re-sync handles!", "Error: ${ex.toString()}");
                          } finally {
                            resyncingHandles.value = false;
                          }
                        },
                        trailing: Obx(() => resyncingHandles.value == null
                            ? const SizedBox.shrink()
                            : resyncingHandles.value == true
                                ? Container(
                                    constraints: const BoxConstraints(
                                      maxHeight: 20,
                                      maxWidth: 20,
                                    ),
                                    child: CircularProgressIndicator(
                                      strokeWidth: 3,
                                      valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                                    ))
                                : Icon(Icons.check, color: context.theme.colorScheme.outline))),
                    const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                    SettingsTile(
                        title: "Sync Chat Info",
                        subtitle:
                            "This will re-sync all chat data & icons from the server to ensure that you have the most up-to-date information.\n\nNote: This will overwrite any group chat icons that are not locked!",
                        onTap: () async {
                          resyncingChats.value = true;
                          try {
                            showSnackbar("Please Wait...", "This may take a few minutes.");

                            final chatSyncer = ChatSyncManager();
                            await chatSyncer.start();
                            EventDispatcherSvc.emit("refresh-all", null);

                            showSnackbar("Success",
                                "Successfully synced your chat info! You may need to close and re-open the app for changes to take effect.");
                          } catch (ex, stacktrace) {
                            Logger.error("Failed to sync chat info!", error: ex, trace: stacktrace);
                            showSnackbar("Failed to sync chat info!", "Error: ${ex.toString()}");
                          } finally {
                            resyncingChats.value = false;
                          }
                        },
                        trailing: Obx(() => resyncingChats.value == null
                            ? const SizedBox.shrink()
                            : resyncingChats.value == true
                                ? Container(
                                    constraints: const BoxConstraints(
                                      maxHeight: 20,
                                      maxWidth: 20,
                                    ),
                                    child: CircularProgressIndicator(
                                      strokeWidth: 3,
                                      valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                                    ))
                                : Icon(Icons.check, color: context.theme.colorScheme.outline)))
                  ]),
                if (usingRustPush)
                  SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "iMessage"),
                if (usingRustPush)
                  SettingsSection(backgroundColor: tileColor, children: [
                    SettingsTile(
                        title: "Clear identity cache",
                        subtitle: "Run this troubleshooter if you're having trouble sending messages.",
                        onTap: () async {
                          await api.invalidateIdCache(client: PushSvc.state!.client);
                          showSnackbar("Success", "Identity cache cleared! Try re-sending any messages.");
                        }),
                    const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                    SettingsTile(
                      title: "Clear peer caches",
                      subtitle: "Run this troubleshooter if you are told to do so.",
                      onTap: () async {
                        if (reregisteringIds.value ?? false) return;
                        try {
                          reregisteringIds.value = true;
                          await PushSvc.invalidatePeerCaches();
                          showSnackbar("Success", "Cleared peer caches");
                        } catch (e) {
                          showSnackbar("Failure", e.toString());
                          rethrow;
                        } finally {
                          reregisteringIds.value = false;
                        }
                      },
                      trailing: Obx(() => reregisteringIds.value == null
                          ? const SizedBox.shrink()
                          : reregisteringIds.value == true
                              ? Container(
                                  constraints: const BoxConstraints(
                                    maxHeight: 20,
                                    maxWidth: 20,
                                  ),
                                  child: CircularProgressIndicator(
                                    strokeWidth: 3,
                                    valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                                  ))
                              : Icon(Icons.check, color: context.theme.colorScheme.outline)),
                    ),
                    const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                    SettingsTile(
                      title: "Reregister",
                      subtitle: "Run this troubleshooter if you are told to do so.",
                      onTap: () async {
                        if (reregisteringIds.value ?? false) return;
                        try {
                          reregisteringIds.value = true;
                          await api.doReregister(state: PushSvc.state!.client);
                          showSnackbar("Success", "Registered");
                        } catch (e) {
                          showSnackbar("Failure", e.toString());
                          rethrow;
                        } finally {
                          reregisteringIds.value = false;
                        }
                      },
                      trailing: Obx(() => reregisteringIds.value == null
                          ? const SizedBox.shrink()
                          : reregisteringIds.value == true
                              ? Container(
                                  constraints: const BoxConstraints(
                                    maxHeight: 20,
                                    maxWidth: 20,
                                  ),
                                  child: CircularProgressIndicator(
                                    strokeWidth: 3,
                                    valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                                  ))
                              : Icon(Icons.check, color: context.theme.colorScheme.outline)),
                    ),
                    const SettingsDivider(padding: EdgeInsets.only(left: 16.0)),
                    SettingsTile(
                      title: "Clear FaceTime Handles",
                      subtitle:
                          "Run this troubleshooter if you cannot use FaceTime. This will delete all links asscoiated with your account.",
                      onTap: () async {
                        if (reregisteringIds.value ?? false) return;
                        try {
                          reregisteringIds.value = true;
                          await api.clearLinks(facetime: PushSvc.state!.ftClient);
                          showSnackbar("Success", "Cleared Links!");
                        } catch (e) {
                          showSnackbar("Failure", e.toString());
                          rethrow;
                        } finally {
                          reregisteringIds.value = false;
                        }
                      },
                      trailing: Obx(() => reregisteringIds.value == null
                          ? const SizedBox.shrink()
                          : reregisteringIds.value == true
                              ? Container(
                                  constraints: const BoxConstraints(
                                    maxHeight: 20,
                                    maxWidth: 20,
                                  ),
                                  child: CircularProgressIndicator(
                                    strokeWidth: 3,
                                    valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                                  ))
                              : Icon(Icons.check, color: context.theme.colorScheme.outline)),
                    )
                  ]),
                if (!kIsDesktop)
                  SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Extensions"),
                if (!kIsDesktop)
                  SettingsSection(
                    backgroundColor: tileColor,
                    children: [
                      Obx(() => SettingsSwitch(
                            onChanged: (bool val) {
                              if (val) {
                                showDialog(
                                  context: context,
                                  builder: (BuildContext context) {
                                    return AlertDialog(
                                        backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                        title:
                                            Text("Enable development mode?", style: context.theme.textTheme.titleLarge),
                                        content: Text(
                                          'This mode is intended for developer use only. Extensions added through this mode have not been reviewed or approved by neither OpenBubbles or Google. You are responsible for ensuring the safety of your data and any extensions you add.',
                                          style: context.theme.textTheme.bodyLarge,
                                        ),
                                        actions: <Widget>[
                                          TextButton(
                                            child: Text("Cancel",
                                                style: context.theme.textTheme.bodyLarge!
                                                    .copyWith(color: context.theme.colorScheme.primary)),
                                            onPressed: () {
                                              Navigator.of(context).pop();
                                            },
                                          ),
                                          TextButton(
                                            child: Text("Enable",
                                                style: context.theme.textTheme.bodyLarge!
                                                    .copyWith(color: context.theme.colorScheme.primary)),
                                            onPressed: () async {
                                              Navigator.of(context).pop();
                                              SettingsSvc.settings.developerEnabled.value = true;
                                              await SettingsSvc.settings.saveOneAsync('developerEnabled');
                                            },
                                          ),
                                        ]);
                                  },
                                );
                                return;
                              }
                              SettingsSvc.settings.developerEnabled.value = val;
                              SettingsSvc.settings.saveOneAsync('developerEnabled');
                              showSnackbar("Success", "Restart device or force quit OpenBubbles to unload extensions");
                            },
                            initialVal: SettingsSvc.settings.developerEnabled.value,
                            title: "Enable Developer Mode",
                            backgroundColor: tileColor,
                          )),
                    ],
                  ),
                if (kIsDesktop) const SizedBox(height: 100),
              ],
            ),
          ),
          if (!kIsDesktop)
            Obx(() => SliverList(
                  delegate: SliverChildBuilderDelegate((context, index) {
                    final Widget addMember = ListTile(
                      mouseCursor: MouseCursor.defer,
                      title: Text("Add Service Name",
                          style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
                      leading: Container(
                        width: 40 * SettingsSvc.settings.avatarScale.value,
                        height: 40 * SettingsSvc.settings.avatarScale.value,
                        decoration: BoxDecoration(
                            color: !iOS ? null : context.theme.colorScheme.surfaceContainerHighest,
                            shape: BoxShape.circle,
                            border: iOS ? null : Border.all(color: context.theme.colorScheme.primary, width: 3)),
                        child: Icon(Icons.add, color: context.theme.colorScheme.primary, size: 20),
                      ),
                      onTap: () {
                        showDialog(
                            context: context,
                            builder: (_) {
                              return AlertDialog(
                                actions: [
                                  TextButton(
                                    child: Text("Cancel",
                                        style: context.theme.textTheme.bodyLarge!
                                            .copyWith(color: context.theme.colorScheme.primary)),
                                    onPressed: () => Get.back(),
                                  ),
                                  TextButton(
                                    child: Text("OK",
                                        style: context.theme.textTheme.bodyLarge!
                                            .copyWith(color: context.theme.colorScheme.primary)),
                                    onPressed: () async {
                                      SettingsSvc.settings.developerMode.add(participantController.text);
                                      await SettingsSvc.settings.saveOneAsync('developerMode');
                                      await ExtensionSvc.refreshCache();
                                      Navigator.of(Get.context!, rootNavigator: true).pop();
                                    },
                                  ),
                                ],
                                content: TextField(
                                  controller: participantController,
                                  decoration: const InputDecoration(
                                    labelText: "Service Name",
                                    border: OutlineInputBorder(),
                                  ),
                                  autofillHints: [AutofillHints.telephoneNumber, AutofillHints.email],
                                ),
                                title: Text("Add", style: context.theme.textTheme.titleLarge),
                                backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                              );
                            });
                      },
                    );

                    final Widget refreshCache = ListTile(
                      mouseCursor: MouseCursor.defer,
                      title: Text("Reload extensions",
                          style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
                      leading: Container(
                        width: 40 * SettingsSvc.settings.avatarScale.value,
                        height: 40 * SettingsSvc.settings.avatarScale.value,
                        decoration: BoxDecoration(
                            color: !iOS ? null : context.theme.colorScheme.surfaceContainerHighest,
                            shape: BoxShape.circle,
                            border: iOS ? null : Border.all(color: context.theme.colorScheme.primary, width: 3)),
                        child: Icon(Icons.refresh, color: context.theme.colorScheme.primary, size: 20),
                      ),
                      onTap: () async {
                        await ExtensionSvc.refreshCache();
                        showSnackbar("Success", "Extensions reloaded!");
                      },
                    );

                    final Widget clear = ListTile(
                      mouseCursor: MouseCursor.defer,
                      title: Text("Clear services",
                          style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.error)),
                      leading: Container(
                        width: 40 * SettingsSvc.settings.avatarScale.value,
                        height: 40 * SettingsSvc.settings.avatarScale.value,
                        decoration: BoxDecoration(
                            color: !iOS ? null : context.theme.colorScheme.surfaceContainerHighest,
                            shape: BoxShape.circle,
                            border: iOS ? null : Border.all(color: context.theme.colorScheme.primary, width: 3)),
                        child: Icon(Icons.clear_all, color: context.theme.colorScheme.error, size: 20),
                      ),
                      onTap: () async {
                        SettingsSvc.settings.developerMode.clear();
                        await SettingsSvc.settings.saveOneAsync('developerMode');
                        showSnackbar("Success", "Restart device or force quit OpenBubbles to unload extensions");
                      },
                    );

                    if (index == SettingsSvc.settings.developerMode.length) {
                      return addMember;
                    }
                    if (index == SettingsSvc.settings.developerMode.length + 1) {
                      return refreshCache;
                    }
                    if (index == SettingsSvc.settings.developerMode.length + 2) {
                      return clear;
                    }

                    return ListTile(
                      mouseCursor: MouseCursor.defer,
                      title: Text(SettingsSvc.settings.developerMode[index]),
                    );
                  },
                      childCount: SettingsSvc.settings.developerEnabled.value
                          ? SettingsSvc.settings.developerMode.length + 3
                          : 0),
                )),
        ]);
  }
}
