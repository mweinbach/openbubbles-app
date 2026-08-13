import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/app/layouts/settings/pages/profile/posterkit.dart';
import 'package:bluebubbles/app/layouts/settings/pages/profile/profile_scaffold.dart';
import 'package:bluebubbles/app/layouts/settings/pages/theming/avatar/avatar_crop.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/main.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:collection/collection.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_rust_bridge/flutter_rust_bridge.dart';
import 'package:get/get.dart';
import 'package:google_sign_in_all_platforms/google_sign_in_all_platforms.dart';
import 'package:in_app_purchase_android/billing_client_wrappers.dart';
import 'package:skeletonizer/skeletonizer.dart';
import 'package:supercharged/supercharged.dart';
import 'package:telephony_plus/telephony_plus.dart';
import 'package:universal_io/io.dart';
import 'package:url_launcher/url_launcher.dart';

class ProfilePanel extends StatefulWidget {
  const ProfilePanel({super.key});

  @override
  State<ProfilePanel> createState() => _ProfilePanelState();
}

class _ProfilePanelState extends State<ProfilePanel> with WidgetsBindingObserver, ThemeHelpers {
  static const List<int> _syncHistoryOptions = [604800000, 2592000000, 15552000000, 31536000000, 0];
  static const Map<int, String> _syncHistoryLabels = {
    604800000: "7 days",
    2592000000: "1 month",
    15552000000: "6 months",
    31536000000: "1 year",
    0: "No limit",
  };
  final RxDouble opacity = 1.0.obs;
  final RxMap<String, dynamic> accountInfo = RxMap({});
  final RxMap<String, dynamic> accountContact = RxMap({});
  final RxnBool reregisteringIds = RxnBool();
  final RxList<api.PrivateDeviceInfo> forwardingTargets = RxList([]);
  final Rxn<api.QuotaInfo> quotaInfo = Rxn(null);
  final Rxn<GoogleSignInCredentials> googleCreds = Rxn(null);

  StreamSubscription<PurchasesResultWrapper>? subscription;
  String? ticket;
  bool cloudKitRecordDirty = false;
  bool profileDirty = false;

  @override
  void initState() {
    super.initState();
    getDetails();
    subscription = PushSvc.client!.purchasesUpdatedStream.listen((PurchasesResultWrapper details) {
      handlePurchases(details);
    });
    if (PushSvc.state!.icloudServices != null) {
      api.getQuotaInfo(info: PushSvc.state!.icloudServices!.tokenProvider).then((quota) => quotaInfo.value = quota);
    }
    if (kIsDesktop) {
      PushSvc.googleSignIn.silentSignIn().then((state) {
        googleCreds.value = state;
      });
    }
  }

  void getDetails() async {
    try {
      final result = await BackendSvc.getAccountInfo();
      accountInfo.addAll(result);
      opacity.value = 1.0;
      final result2 = await BackendSvc.getAccountContact();
      accountContact.addAll(result2);
    } catch (e, s) {
      Logger.warn("Failed to fetch account profile info", error: e, trace: s, tag: 'ProfilePanel');
    }
    final myHandles = await api.getMyPhoneHandles(state: PushSvc.state!.client);
    if (myHandles.isNotEmpty) {
      final pendingTargets = SettingsSvc.settings.isSmsRouter.value
          ? await api.getSmsTargets(state: PushSvc.state!.client, handle: myHandles.first, refresh: true)
          : <api.PrivateDeviceInfo>[];
      await SettingsSvc.settings.saveOneAsync('smsForwardingTargets'); // prefs key for the smsRoutingTargets field
      forwardingTargets.value = pendingTargets;
    }
    setState(() {});
  }

  @override
  void dispose() {
    subscription?.cancel();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!profileDirty) return;
      api.ShareProfileMessage? profile;
      if (cloudKitRecordDirty && SettingsSvc.settings.nameAndPhotoSharing.value) {
        api.ShareProfileMessage? existing;
        if (SettingsSvc.settings.shareProfileMessage.value != null) {
          existing = await api.decodeProfileMessage(s: SettingsSvc.settings.shareProfileMessage.value!);
        }
        Uint8List? image;
        if (SettingsSvc.settings.userAvatarPath.value != null) {
          image = await File(SettingsSvc.settings.userAvatarPath.value!).readAsBytes();
        }
        showDialog(
          context: Get.context!,
          builder: (context) => AlertDialog(
            backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
            title: Text("Updating profile...", style: context.theme.textTheme.titleLarge),
            content: SizedBox(
              height: 70,
              child: Center(
                child: CircularProgressIndicator(
                  backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                  valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                ),
              ),
            ),
          ),
        );

        api.SimplifiedIncomingCallPoster? poster;
        if (SettingsSvc.settings.userPosterPath.value != null && !kIsDesktop) {
          final data = await File("${SettingsSvc.settings.userPosterPath.value!}.jpg").readAsBytes();
          poster = await api.fromPosterSave(poster: data);
        }

        if (SettingsSvc.settings.userPosterPath.value != null) {
          await restorePoster(poster?.poster, SettingsSvc.settings.userPosterPath.value!);
        }

        try {
          final message = await api.setProfile(
            profiles: PushSvc.state!.icloudServices!.profilesClient,
            record: api.IMessageNicknameRecord(
              name: api.IMessageNameRecord(
                name: SettingsSvc.settings.userName.value,
                first: SettingsSvc.settings.firstName.value!,
                last: SettingsSvc.settings.lastName.value!,
              ),
              image: image,
              poster: poster != null ? await api.fromPoster(poster: poster) : null,
            ),
            existing: existing,
          );
          Navigator.of(Get.context!, rootNavigator: true).pop();
          SettingsSvc.settings.sharedContacts.clear();
          SettingsSvc.settings.dismissedContacts.clear();
          SettingsSvc.settings.shareProfileMessage.value = await api.encodeProfileMessage(p: message);
          await SettingsSvc.settings.saveAsync();
          profile = message;
        } catch (e, s) {
          Navigator.of(Get.context!, rootNavigator: true).pop();
          showSnackbar("Error", "Failed to update profile! $e");
          Logger.error("Failed to update profile", error: e, trace: s);
          rethrow;
        }
      }

      PushSvc.updateShareState();
      final handle = (await api.getHandles(state: PushSvc.state!.client)).first;
      final msg = await api.newMsg(
        conversation: api.ConversationData(participants: [handle]),
        sender: handle,
        message: api.Message.updateProfile(
          api.UpdateProfileMessage(
            shareContacts: SettingsSvc.settings.shareContactAutomatically.value,
            profile: profile,
          ),
        ),
      );
      await (BackendSvc as RustPushBackend).sendMsg(msg);
    });
    super.dispose();
  }

  Future<T> wrapPromise<T>(Future<T> inner, String text) async {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
        title: Text(text, style: context.theme.textTheme.titleLarge),
        content: SizedBox(
          height: 70,
          child: Center(
            child: CircularProgressIndicator(
              backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
              valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
            ),
          ),
        ),
      ),
    );
    try {
      final result = await inner;
      Get.back();
      return result;
    } catch (e) {
      Get.back();
      showSnackbar("Failure! Please try again", e.toString());
      rethrow;
    }
  }

  Future<void> handleSubscriptionToken(String subscription) async {
    final activated = await HttpSvc.dio
        .post("https://hw.openbubbles.app/ticket/${ticket!}/activate", data: {"purchase_token": subscription});
    final useTicket = activated.data["ticket"];
    if (useTicket != ticket) {
      throw Exception("Ticket changed???");
    }
    try {
      reregisteringIds.value = true;
      await api.doReregister(state: PushSvc.state!.client);
      getDetails();
      showSnackbar("Success", "Registered");
    } finally {
      reregisteringIds.value = false;
    }
  }

  Future<bool> handlePurchases(PurchasesResultWrapper details) async {
    for (final detail in details.purchasesList) {
      if (detail.purchaseState != PurchaseStateWrapper.purchased) continue;
      SettingsSvc.settings.hostedToken.value = detail.purchaseToken;
      await SettingsSvc.settings.saveOneAsync('hostedToken');
      await wrapPromise(handleSubscriptionToken(detail.purchaseToken), "Validating subscription...");
      Logger.info("Purchased token ${detail.purchaseToken}");
      return true;
    }
    return false;
  }

  Future<void> updateName() async {
    final firstName = TextEditingController(text: SettingsSvc.settings.firstName.value);
    final lastName = TextEditingController(text: SettingsSvc.settings.lastName.value);
    done() async {
      if (firstName.text.isEmpty) {
        showSnackbar("Error", "Enter a name!");
        return;
      }
      Navigator.of(context, rootNavigator: true).pop();
      SettingsSvc.settings.firstName.value = firstName.text;
      SettingsSvc.settings.lastName.value = lastName.text;
      SettingsSvc.settings.userName.value = "${firstName.text} ${lastName.text}".trim();
      cloudKitRecordDirty = true;
      profileDirty = true;
      await SettingsSvc.settings.saveAsync();
      setState(() {});
    }

    await showDialog(
        context: context,
        builder: (_) {
          return AlertDialog(
            actions: [
              TextButton(
                child: Text("Cancel",
                    style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
                onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
              ),
              TextButton(
                child: Text("OK",
                    style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
                onPressed: () async {
                  done.call();
                },
              ),
            ],
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: firstName,
                  textInputAction: TextInputAction.next,
                  autofocus: true,
                  decoration: const InputDecoration(
                    labelText: "First Name",
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: lastName,
                  onSubmitted: (_) => done.call(),
                  decoration: const InputDecoration(
                    labelText: "Last Name",
                    border: OutlineInputBorder(),
                  ),
                ),
              ],
            ),
            title: Text("Change Name", style: context.theme.textTheme.titleLarge),
            backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
          );
        });
  }

  void updatePhoto() async {
    Navigator.of(context).push(
      ThemeSwitcher.buildPageRoute(
        builder: (context) => AvatarCrop(
          cropped: () {
            cloudKitRecordDirty = true;
            profileDirty = true;
          },
        ),
      ),
    );
  }

  Future<void> removePhoto() async {
    File file = File(SettingsSvc.settings.userAvatarPath.value!);
    file.delete();
    SettingsSvc.settings.userAvatarPath.value = null;
    await SettingsSvc.settings.saveOneAsync("userAvatarPath");
    cloudKitRecordDirty = true;
    profileDirty = true;
  }

  @override
  Widget build(BuildContext context) {
    return ProfileScaffold(
      handle: null,
      posterEdited: () {
        cloudKitRecordDirty = true;
        profileDirty = true;
      },
      bodySlivers: [
        SliverToBoxAdapter(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SettingsHeader(
                iosSubtitle: iosSubtitle,
                materialSubtitle: materialSubtitle,
                text: "Your Name and Photo",
              ),
              SettingsSection(
                backgroundColor: tileColor,
                children: [
                  Padding(
                    padding: const EdgeInsets.only(bottom: 5.0),
                    child: Material(
                      color: Colors.transparent,
                      child: ListTile(
                        mouseCursor: MouseCursor.defer,
                        leading: const ContactAvatarWidget(
                          handle: null,
                          borderThickness: 0.1,
                          editable: false,
                          fontSize: 22,
                          size: 50,
                        ),
                        onTap: () async {
                          updateName();
                        },
                        title: RichText(
                          text: TextSpan(
                            style: context.theme.textTheme.bodyLarge,
                            children: MessageHelper.buildEmojiText(
                              SettingsSvc.settings.redactedMode.value && SettingsSvc.settings.hideContactInfo.value
                                  ? "User Name"
                                  : SettingsSvc.settings.userName.value,
                              context.theme.textTheme.bodyLarge!,
                            ),
                          ),
                        ),
                        subtitle: Text(
                          SettingsSvc.settings.redactedMode.value && SettingsSvc.settings.hideContactInfo.value
                              ? "User iCloud"
                              : SettingsSvc.settings.iCloudAccount.isEmpty
                                  ? "Unknown iCloud account"
                                  : SettingsSvc.settings.iCloudAccount.value,
                          style: context.theme.textTheme.bodyMedium!.apply(color: context.theme.colorScheme.outline),
                        ),
                        trailing: Icon(Icons.edit_outlined, color: context.theme.colorScheme.onSurface),
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(bottom: 5.0),
                    child: Material(
                      color: Colors.transparent,
                      child: ListTile(
                        mouseCursor: MouseCursor.defer,
                        onTap: () async {
                          updatePhoto();
                        },
                        title: Text("Update your photo", style: context.theme.textTheme.bodyLarge!),
                        trailing: Icon(Icons.edit_outlined, color: context.theme.colorScheme.onSurface),
                      ),
                    ),
                  ),
                  Obx(() => SettingsSvc.settings.userAvatarPath.value != null
                      ? Padding(
                          padding: const EdgeInsets.only(bottom: 5.0),
                          child: Material(
                            color: Colors.transparent,
                            child: ListTile(
                              mouseCursor: MouseCursor.defer,
                              onTap: () async {
                                removePhoto();
                              },
                              title: Text(
                                "Remove your photo",
                                style:
                                    context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.error),
                              ),
                              trailing: Icon(Icons.close, color: context.theme.colorScheme.error),
                            ),
                          ),
                        )
                      : const SizedBox.shrink()),
                ],
              ),
              SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Sharing"),
              SettingsSection(
                backgroundColor: tileColor,
                children: [
                  Obx(
                    () => SettingsSwitch(
                      onChanged: (bool val) async {
                        if (val && PushSvc.state!.icloudServices?.profilesClient == null) {
                          showSnackbar("Relog required!",
                              "Relog required to use profile sharing! Relog in Settings -> Reconfigure");
                          return;
                        }
                        if (SettingsSvc.settings.firstName.value == null ||
                            SettingsSvc.settings.lastName.value == null) {
                          await updateName();
                        }
                        if (SettingsSvc.settings.firstName.value == null ||
                            SettingsSvc.settings.lastName.value == null) {
                          return;
                        }
                        SettingsSvc.settings.nameAndPhotoSharing.value = val;
                        profileDirty = true;
                        await SettingsSvc.settings.saveOneAsync('nameAndPhotoSharing');
                      },
                      initialVal: SettingsSvc.settings.nameAndPhotoSharing.value,
                      title: "Name and Photo Sharing",
                      backgroundColor: tileColor,
                    ),
                  ),
                  Obx(
                    () => SettingsSvc.settings.nameAndPhotoSharing.value
                        ? SettingsOptions<String>(
                            title: "Share Automatically",
                            initial:
                                SettingsSvc.settings.shareContactAutomatically.value ? "Contacts Only" : "Always Ask",
                            clampWidth: false,
                            options: const ["Contacts Only", "Always Ask"],
                            secondaryColor: headerColor,
                            useCupertino: false,
                            capitalize: false,
                            textProcessing: (s) => s,
                            onChanged: (value) async {
                              SettingsSvc.settings.shareContactAutomatically.value = value == "Contacts Only";
                              profileDirty = true;
                              await SettingsSvc.settings.saveOneAsync('shareContactAutomatically');
                            },
                          )
                        : const SizedBox.shrink(),
                  ),
                ],
              ),
              SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Backup/Restore"),
              Obx(
                () => SettingsSection(
                  backgroundColor: tileColor,
                  children: [
                    SettingsSwitch(
                      onChanged: (bool val) async {
                        if (PushSvc.state!.icloudServices?.keychain == null && val) {
                          showSnackbar(
                              "Relog required!", "Relog required to use Backup! Relog in Settings -> Reconfigure");
                          return;
                        }
                        if (val) {
                          if (!await PushSvc.joinClique()) return;
                          PushSvc.eraseCloudKitSync();
                        }
                        SettingsSvc.settings.cloudSyncingEnabled.value = val;
                        await SettingsSvc.settings.saveOneAsync('cloudSyncingEnabled');
                        if (!val) {
                          await PushSvc.resetCloudKitSync();
                        } else {
                          PushSvc.doCloudKitSync();
                        }
                      },
                      initialVal: SettingsSvc.settings.cloudSyncingEnabled.value,
                      title: "Messages in iCloud (BETA)",
                      backgroundColor: tileColor,
                    ),
                    if (SettingsSvc.settings.cloudSyncingEnabled.value)
                      SettingsSwitch(
                        onChanged: (bool val) async {
                          SettingsSvc.settings.attachmentSyncEnabled.value = val;
                        },
                        initialVal: SettingsSvc.settings.attachmentSyncEnabled.value,
                        title: "Upload attachments",
                        subtitle: "Disable to reduce iCloud storage usage",
                        backgroundColor: tileColor,
                      ),
                    if (SettingsSvc.settings.cloudSyncingEnabled.value)
                      SettingsOptions<int>(
                        title: "Sync history",
                        initial: _syncHistoryOptions.contains(SettingsSvc.settings.syncHistoryTime.value)
                            ? SettingsSvc.settings.syncHistoryTime.value
                            : 0,
                        clampWidth: false,
                        options: _syncHistoryOptions,
                        secondaryColor: headerColor,
                        useCupertino: false,
                        capitalize: false,
                        textProcessing: (value) => _syncHistoryLabels[value] ?? "No limit",
                        onChanged: (value) async {
                          if (value == null) return;
                          SettingsSvc.settings.syncHistoryTime.value = value;
                          await SettingsSvc.settings.saveOneAsync('syncHistoryTime');
                          await PushSvc.resetCloudKitSync();
                          PushSvc.doCloudKitSync();
                        },
                      ),
                    if (quotaInfo.value != null && SettingsSvc.settings.cloudSyncingEnabled.value)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8.0, left: 15, top: 8.0, right: 15),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                                "Used ${PushSvc.formatBytes(quotaInfo.value!.messagesBytes)}. ${PushSvc.formatBytes(quotaInfo.value!.availableBytes)} available in iCloud."),
                            Text(
                              "Upgrade to iCloud+ on any Apple device or Windows PC for more storage space.",
                              style: context.theme.textTheme.bodySmall!.copyWith(
                                color: context.theme.colorScheme.onSurface.withOpacity(0.75),
                                height: 1.5,
                              ),
                            ),
                          ],
                        ),
                      ),
                    if (SettingsSvc.settings.cloudSyncingEnabled.value && PushSvc.isSyncing.value == null)
                      SettingsTile(
                        title: "Sync Now",
                        onTap: () async {
                          await PushSvc.doCloudKitSync();
                        },
                        trailing: const NextButton(),
                      ),
                    if (SettingsSvc.settings.cloudSyncingEnabled.value)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8.0, left: 15, top: 8.0, right: 15),
                        child: Text(
                          PushSvc.isSyncing.value ??
                              ((PrefsSvc.i.getInt("lastSynced") ?? 0) == 0
                                  ? "Not Synced"
                                  : "Synced ${buildChatListDateMaterial(DateTime.fromMillisecondsSinceEpoch(PrefsSvc.i.getInt("lastSynced")!))}"),
                        ),
                      ),
                  ],
                ),
              ),
              if (kIsDesktop)
                SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Contacts Syncing"),
              if (kIsDesktop)
                Obx(
                  () => SettingsSection(
                    backgroundColor: tileColor,
                    children: [
                      SettingsOptions<String>(
                        title: "Sync contacts with",
                        initial: SettingsSvc.settings.contactSyncProvider.value,
                        clampWidth: false,
                        options: const ["iCloud", "Google", "CardDav"],
                        secondaryColor: headerColor,
                        useCupertino: false,
                        textProcessing: (str) => str,
                        capitalize: false,
                        onChanged: (value) async {
                          SettingsSvc.settings.ctags.clear();
                          SettingsSvc.settings.tokens.clear();
                          SettingsSvc.settings.contactSyncProvider.value = value ?? "iCloud";
                          await SettingsSvc.settings.saveAsync();
                          unawaited(ContactsSvcV2.syncContactsToHandles(wait: false));
                        },
                      ),
                      if (SettingsSvc.settings.contactSyncProvider.value == "Google" && googleCreds.value == null)
                        SettingsTile(
                          title: "Sign In",
                          onTap: () async {
                            final credentials = await PushSvc.googleSignIn.signIn();
                            if (credentials != null) {
                              googleCreds.value = credentials;
                              unawaited(ContactsSvcV2.syncContactsToHandles(wait: false));
                            }
                          },
                          trailing: const NextButton(),
                        ),
                      if (SettingsSvc.settings.contactSyncProvider.value == "Google" && googleCreds.value != null)
                        SettingsTile(
                          title: "Sign Out",
                          onTap: () async {
                            await PushSvc.googleSignIn.signOut();
                            googleCreds.value = null;
                          },
                          trailing: const NextButton(),
                        ),
                      if (SettingsSvc.settings.contactSyncProvider.value == "CardDav")
                        SettingsTile(
                          title: "Set CardDav Server Details",
                          onTap: () async {
                            PushSvc.updateCardDav();
                          },
                          trailing: const NextButton(),
                        ),
                    ],
                  ),
                ),
              SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Apple Account Info"),
              Skeletonizer(
                  enabled: accountInfo.isEmpty,
                  child: SettingsSection(
                    backgroundColor: tileColor,
                    children: [
                      Obx(() {
                        bool redact = SettingsSvc.settings.redactedMode.value;
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 8.0, left: 15, top: 8.0, right: 15),
                          child: AnimatedOpacity(
                            duration: const Duration(milliseconds: 300),
                            opacity: opacity.value,
                            child: SelectableText.rich(
                              TextSpan(children: [
                                TextSpan(
                                    text: redact
                                        ? "Account Name - Apple ID"
                                        : "${accountInfo['account_name']} - ${accountInfo['apple_id']}"),
                                const TextSpan(text: "\n"),
                                const TextSpan(text: "iMessage Status: ", style: TextStyle(height: 3.0)),
                                TextSpan(
                                    text: accountInfo['login_status_message'],
                                    style: TextStyle(
                                        color: getIndicatorColor(
                                            (accountInfo['login_status_message']?.startsWith("Connected") ?? false)
                                                ? SocketState.connected
                                                : SocketState.disconnected))),
                                const TextSpan(text: "\n"),
                                const TextSpan(text: "SMS Forwarding Status: "),
                                TextSpan(
                                    text: accountInfo['sms_forwarding_enabled'] == true ? "ENABLED" : "DISABLED",
                                    style: TextStyle(
                                        color: getIndicatorColor(accountInfo['sms_forwarding_enabled'] == true
                                            ? SocketState.connected
                                            : SocketState.disconnected))),
                                const TextSpan(text: "  |  "),
                                TextSpan(
                                    text: accountInfo['sms_forwarding_capable'] == true ? "CAPABLE" : "INCAPABLE",
                                    style: TextStyle(
                                        color: getIndicatorColor(accountInfo['sms_forwarding_capable'] == true
                                            ? SocketState.connected
                                            : SocketState.disconnected))),
                                const TextSpan(text: "\n"),
                                const TextSpan(
                                    text: "VETTED ALIASES\n",
                                    style: TextStyle(fontWeight: FontWeight.w700, height: 3.0)),
                                ...((accountInfo['vetted_aliases'] as List<dynamic>? ?? []))
                                    .map((e) => [
                                          TextSpan(
                                              text: "⬤  ",
                                              style: TextStyle(
                                                  color: getIndicatorColor(e['Status'] == 3
                                                      ? SocketState.connected
                                                      : SocketState.disconnected))),
                                          TextSpan(
                                              text: redact
                                                  ? (GetUtils.isEmail(e['Alias'])
                                                      ? "Redacted Email\n"
                                                      : "Redacted Phone\n")
                                                  : "${e['Alias']}\n")
                                        ])
                                    .toList()
                                    .flattened,
                                const TextSpan(text: "\n"),
                                const TextSpan(
                                    text: "Tap to update values...", style: TextStyle(fontStyle: FontStyle.italic)),
                              ]),
                              onTap: () {
                                opacity.value = 0.0;
                                getDetails();
                              },
                            ),
                          ),
                        );
                      }),
                      if (accountInfo['login_status_message']?.startsWith("Deregistered") ?? false)
                        Container(
                          color: tileColor,
                          child: SettingsDivider(
                              color: context.theme.colorScheme.surfaceVariant, padding: EdgeInsets.zero),
                        ),
                      if ((accountInfo['login_status_message']?.startsWith("Deregistered") ?? false) ||
                          (accountInfo['login_status_message']?.contains("Subscription not active!") ?? false))
                        SettingsTile(
                          title: accountInfo['login_status_message']!.contains("Device not reserved!")
                              ? "Reserve a new device"
                              : accountInfo['login_status_message']!.contains("Subscription not active!")
                                  ? "Renew subscription"
                                  : "Retry now",
                          onTap: () async {
                            if (accountInfo['login_status_message']!.contains("Subscription not active!") ||
                                accountInfo['login_status_message']!.contains("Device not reserved!")) {
                              await wrapPromise(() async {
                                ticket = await api.validateRelay(configRef: PushSvc.state!.osConfig);
                                if (ticket == null) {
                                  final isNotReserved =
                                      accountInfo['login_status_message']!.contains("Device not reserved!");
                                  final status = await HttpSvc.dio.get("https://hw.openbubbles.app/status");
                                  final hasCapacity = status.data["available"];
                                  var description =
                                      "When an OpenBubbles subscription becomes invalid, we reserve your device for a few days as a courtesy should you choose to restart your subscription. Unfortunately, however, we have already released your device to another user.";
                                  if (hasCapacity) {
                                    description +=
                                        " We have more devices available, however, you will have to re-activate. Backing up your messages now is recommended in case you aren't able to get back in.";
                                  } else {
                                    description +=
                                        " Double unfortunately, we are currently out of devices. Please check back later.";
                                  }
                                  Timer(
                                    const Duration(milliseconds: 100),
                                    () => showDialog(
                                      context: context,
                                      builder: (context) => AlertDialog(
                                        title: Text("We're so sorry!", style: context.theme.textTheme.titleLarge),
                                        backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                        content: Text(description, style: context.theme.textTheme.bodyLarge),
                                        actions: [
                                          TextButton(
                                            onPressed: () => Navigator.of(context).pop(),
                                            child: Text("Close",
                                                style: context.theme.textTheme.bodyLarge!
                                                    .copyWith(color: context.theme.colorScheme.primary)),
                                          ),
                                          if (hasCapacity)
                                            TextButton(
                                              onPressed: () {
                                                Navigator.of(context).pop();
                                                PushSvc.markFailedToLogin(hw: true, ui: true);
                                              },
                                              child: Text(
                                                isNotReserved ? "Get a new device" : "Restart subscription",
                                                style: context.theme.textTheme.bodyLarge!
                                                    .copyWith(color: context.theme.colorScheme.primary),
                                              ),
                                            ),
                                          if (!hasCapacity && isNotReserved)
                                            TextButton(
                                              onPressed: () {
                                                Navigator.of(context).pop();
                                                PushSvc.offerHostedRefund(true);
                                              },
                                              child: Text("Get a refund",
                                                  style: context.theme.textTheme.bodyLarge!
                                                      .copyWith(color: context.theme.colorScheme.primary)),
                                            ),
                                        ],
                                      ),
                                    ),
                                  );
                                  return;
                                }
                                await PushSvc.client!.runWithClientNonRetryable<void>((client) async {
                                  final purchases = await client.queryPurchases(ProductType.subs);
                                  if (await handlePurchases(purchases)) return;
                                  final details = await client.queryProductDetails(
                                    productList: [
                                      const ProductWrapper(productId: 'monthly_hosted', productType: ProductType.subs)
                                    ],
                                  );
                                  if (details.productDetailsList.isEmpty) return;
                                  client.launchBillingFlow(
                                    product: 'monthly_hosted',
                                    offerToken:
                                        details.productDetailsList.first.subscriptionOfferDetails?.first.offerIdToken,
                                  );
                                });
                              }(), "Validating subscription...");
                              return;
                            }
                            try {
                              reregisteringIds.value = true;
                              await api.doReregister(state: PushSvc.state!.client);
                              getDetails();
                              showSnackbar("Success", "Registered");
                            } finally {
                              reregisteringIds.value = false;
                            }
                          },
                          trailing: Obx(
                            () => reregisteringIds.value == null
                                ? const NextButton()
                                : reregisteringIds.value == true
                                    ? Container(
                                        constraints: const BoxConstraints(maxHeight: 20, maxWidth: 20),
                                        child: CircularProgressIndicator(
                                          strokeWidth: 3,
                                          valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                                        ),
                                      )
                                    : Icon(Icons.check, color: context.theme.colorScheme.outline),
                          ),
                        ),
                      if (!(accountInfo['can_pnr'] ?? true) && !kIsDesktop)
                        SettingsTile(
                          title: "Add your phone number",
                          onTap: () async {
                            PushSvc.wantAddNumber();
                          },
                          trailing: const NextButton(),
                          leading: Icon(Icons.add, color: context.theme.colorScheme.outline),
                        ),
                      if (accountInfo['login_status_message']
                              ?.contains("Sorry, your hosted device is currently offline!") ??
                          false)
                        SettingsTile(
                          title: "Get a different Hosted Device",
                          onTap: () async {
                            showDialog(
                              context: context,
                              builder: (context) => AlertDialog(
                                title:
                                    Text("Get a different hosted device?", style: context.theme.textTheme.titleLarge),
                                backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                content: Text("You will have to log in again with your Apple Account.",
                                    style: context.theme.textTheme.bodyLarge),
                                actions: [
                                  TextButton(
                                    onPressed: () => Navigator.of(context).pop(),
                                    child: Text("Cancel",
                                        style: context.theme.textTheme.bodyLarge!
                                            .copyWith(color: context.theme.colorScheme.primary)),
                                  ),
                                  TextButton(
                                    onPressed: () {
                                      wrapPromise(() async {
                                        Navigator.of(context).pop();
                                        final relay = await api.validateRelay(configRef: PushSvc.state!.osConfig);
                                        if (relay == null) {
                                          throw Exception("Failed to validate!");
                                        }
                                        final status = await HttpSvc.dio.post(
                                          "https://hw.openbubbles.app/swap-token",
                                          options: Options(headers: {"Authorization": "Bearer $relay"}),
                                        );
                                        if (status.statusCode != 200) {
                                          if (status.data.toString().contains("No device available!")) {
                                            Timer(const Duration(milliseconds: 100),
                                                () => PushSvc.offerHostedRefund(false));
                                          }
                                          throw Exception("Failed to swap ${status.data}");
                                        }
                                        final newTicket = status.data["new_ticket"];
                                        final config = await api.configFromRelay(
                                            code: newTicket, host: "https://hw.openbubbles.app");
                                        await api.setIdentity(
                                            statePath: PushSvc.statePath,
                                            config: config,
                                            identity: api.newNgmIdentity());
                                        api.resetAnisette(path: PushSvc.statePath);
                                        await PushSvc.markFailedToLogin(hw: true, logout: true);
                                        final list = SettingsSvc.settings.cachedCodes.entries.toList();
                                        for (final items in list) {
                                          if (!items.key.startsWith("sms-auth-")) continue;
                                          SettingsSvc.settings.cachedCodes.remove(items.key);
                                        }
                                        await SettingsSvc.settings.saveOneAsync('cachedCodes');
                                      }(), "Changing device...");
                                    },
                                    child: Text("Change",
                                        style: context.theme.textTheme.bodyLarge!
                                            .copyWith(color: context.theme.colorScheme.primary)),
                                  ),
                                ],
                              ),
                            );
                          },
                          trailing: const NextButton(),
                        ),
                      if ((accountInfo['vetted_aliases'] as List<dynamic>? ?? [])
                          .any((a) => (a['Alias'] as String).isEmail))
                        SettingsTile(
                          title: "Get a verification code",
                          onTap: () async {
                            final code = await api.get2FaCode(anisette: PushSvc.state!.anisette);
                            await showDialog(
                              context: context,
                              builder: (_) => AlertDialog(
                                actions: [
                                  TextButton(
                                    onPressed: () async {
                                      Get.back();
                                    },
                                    child: Text("OK",
                                        style: context.theme.textTheme.bodyLarge!
                                            .copyWith(color: context.theme.colorScheme.primary)),
                                  ),
                                ],
                                content: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Text("Use this code to log into your Apple Account on another device.",
                                        style: context.textTheme.bodyLarge),
                                    const SizedBox(height: 30),
                                    Text(
                                      code.toString().padLeft(6, '0'),
                                      style: context.textTheme.displaySmall
                                          ?.copyWith(color: context.textTheme.bodyLarge?.color, letterSpacing: 20),
                                    ),
                                    const SizedBox(height: 30),
                                    Text(
                                        "Do not share it with anyone. Apple will never call or text you for this code.",
                                        style: context.textTheme.bodyLarge),
                                  ],
                                ),
                                title: Text("Verification code", style: context.theme.textTheme.titleLarge),
                                backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                              ),
                            );
                          },
                          trailing: const NextButton(),
                        ),
                      if (!(accountInfo['login_status_message']?.contains("Subscription not active!") ?? false) &&
                          SettingsSvc.settings.deviceIsHosted.value)
                        SettingsTile(
                          title: "Manage subscription",
                          onTap: () async {
                            launchUrl(
                              Uri.parse(
                                  "https://play.google.com/store/account/subscriptions?sku=monthly_hosted&package=com.openbubbles.messaging"),
                              mode: LaunchMode.externalNonBrowserApplication,
                            );
                          },
                          trailing: const NextButton(),
                        ),
                      if (accountInfo['active_alias'] != null)
                        SettingsOptions<String>(
                          title: "Start Chats Using",
                          initial: accountInfo['active_alias'],
                          clampWidth: false,
                          options:
                              accountInfo['vetted_aliases'].map((e) => e['Alias'].toString()).toList().cast<String>(),
                          secondaryColor: headerColor,
                          useCupertino: false,
                          textProcessing: (str) => SettingsSvc.settings.redactedMode.value
                              ? (GetUtils.isEmail(str) ? "Redacted Email" : "Redacted Phone")
                              : str,
                          capitalize: false,
                          onChanged: (value) async {
                            if (value == null) return;
                            accountInfo['active_alias'] = value;
                            setState(() {});
                            await BackendSvc.setDefaultHandle(value);
                          },
                        ),
                      if (usingRustPush && Platform.isAndroid && (accountInfo["can_forward"] ?? false))
                        Obx(
                          () => SettingsSwitch(
                            onChanged: (bool val) async {
                              if (val) {
                                final granted = await TelephonyPlus().requestPermissions();
                                if (!granted) {
                                  showSnackbar("SMS denied", "Please enable SMS permission in settings");
                                  return;
                                }
                              }
                              final myHandles = await api.getMyPhoneHandles(state: PushSvc.state!.client);
                              SettingsSvc.settings.isSmsRouter.value = val;
                              await SettingsSvc.settings.saveOneAsync('isSmsRouter');
                              final pendingTargets = val
                                  ? await api.getSmsTargets(
                                      state: PushSvc.state!.client, handle: myHandles.first, refresh: true)
                                  : <api.PrivateDeviceInfo>[];
                              if (!val) {
                                await (BackendSvc as RustPushBackend)
                                    .broadcastSmsForwardingState(false, SettingsSvc.settings.smsRoutingTargets);
                              }
                              SettingsSvc.settings.smsRoutingTargets
                                  .retainWhere((element) => pendingTargets.any((e) => e.uuid == element));
                              await SettingsSvc.settings.saveOneAsync('smsForwardingTargets'); // prefs key for the smsRoutingTargets field
                              setState(() {
                                forwardingTargets.value = pendingTargets;
                              });
                            },
                            initialVal: SettingsSvc.settings.isSmsRouter.value,
                            title: "Text message forwarding (BETA)",
                            subtitle: "See your Android SMS messages on your other Apple devices",
                            backgroundColor: tileColor,
                            isThreeLine: true,
                          ),
                        ),
                      if (!SettingsSvc.settings.redactedMode.value)
                        ...(usingRustPush && Platform.isAndroid && SettingsSvc.settings.isSmsRouter.value
                            ? forwardingTargets
                                .where((target) => target.uuid != null && target.deviceName != null)
                                .map(
                                  (target) => SettingsSwitch(
                                    onChanged: (bool val) async {
                                      if (!target.isHsaTrusted) {
                                        showSnackbar(
                                            "Can't enable SMS forwarding!", "Re-log in with 2fa on the other device");
                                        return;
                                      }
                                      if (SettingsSvc.settings.smsRoutingTargets.contains(target.uuid)) {
                                        SettingsSvc.settings.smsRoutingTargets.remove(target.uuid);
                                        setState(() {});
                                        await (BackendSvc as RustPushBackend)
                                            .broadcastSmsForwardingState(false, [target.uuid!]);
                                      } else {
                                        SettingsSvc.settings.smsRoutingTargets.add(target.uuid!);
                                        setState(() {});
                                        await (BackendSvc as RustPushBackend)
                                            .broadcastSmsForwardingState(true, [target.uuid!]);
                                      }
                                      await SettingsSvc.settings.saveOneAsync('smsForwardingTargets'); // prefs key for the smsRoutingTargets field
                                    },
                                    initialVal: SettingsSvc.settings.smsRoutingTargets.contains(target.uuid),
                                    title: target.deviceName!,
                                    backgroundColor: tileColor,
                                  ),
                                )
                                .toList()
                            : []),
                    ],
                  )),
              if (!isNullOrEmpty(accountContact['name']))
                SettingsHeader(
                    iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "iMessage Contact Card"),
              if (!isNullOrEmpty(accountContact['name']))
                SettingsSection(
                  backgroundColor: tileColor,
                  children: [
                    SettingsTile(
                      leading: isNullOrEmpty(accountContact['avatar'])
                          ? const CircleAvatar()
                          : CircleAvatar(
                              backgroundImage: MemoryImage(
                                base64Decode(accountContact['avatar']),
                              ),
                            ),
                      title: accountContact['name'],
                      subtitle: "Your sharable iMessage contact card",
                    ),
                    const SettingsSubtitle(subtitle: "Visit iMessage settings on your Mac to update.")
                  ],
                ),
            ],
          ),
        ),
        const SliverPadding(
          padding: EdgeInsets.only(top: 50),
        ),
      ],
    );
  }
}
