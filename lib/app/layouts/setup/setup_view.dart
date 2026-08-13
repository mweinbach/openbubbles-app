import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:android_play_install_referrer/android_play_install_referrer.dart';
import 'package:app_links/app_links.dart';
import 'package:bluebubbles/app/layouts/setup/dialogs/failed_to_connect_dialog.dart';
import 'package:bluebubbles/app/layouts/setup/pages/permissions/request_permissions.dart';
import 'package:bluebubbles/app/layouts/setup/pages/rustpush/appleid_2fa.dart';
import 'package:bluebubbles/app/layouts/setup/pages/rustpush/appleid_login.dart';
import 'package:bluebubbles/app/layouts/setup/pages/rustpush/finalize.dart';
import 'package:bluebubbles/app/layouts/setup/pages/rustpush/hw_inp.dart';
import 'package:bluebubbles/app/layouts/setup/pages/rustpush/phone_number.dart';
import 'package:bluebubbles/app/layouts/setup/pages/setup_checks/battery_optimization.dart';
import 'package:bluebubbles/app/layouts/setup/pages/setup_checks/mac_setup_check.dart';
import 'package:bluebubbles/app/layouts/setup/pages/sync/server_credentials.dart';
import 'package:bluebubbles/app/layouts/setup/pages/sync/sync_progress.dart';
import 'package:bluebubbles/app/layouts/setup/pages/sync/sync_settings.dart';
import 'package:bluebubbles/app/layouts/setup/pages/welcome/welcome_page.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/main.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/src/rust/frb_generated.dart';
import 'package:bluebubbles/src/rust/lib.dart';
import 'package:bluebubbles/utils/crypto_utils.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:collection/collection.dart';
import 'package:convert/convert.dart';
import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:disable_battery_optimization/disable_battery_optimization.dart';
import 'package:file_picker/file_picker.dart' as picker;
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_acrylic/flutter_acrylic.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_rust_bridge/flutter_rust_bridge.dart';
import 'package:get/get.dart' hide FormData, MultipartFile;
import 'package:in_app_purchase_android/billing_client_wrappers.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:url_launcher/url_launcher.dart';

class SetupViewController extends StatefulController {
  final pageController = PageController(initialPage: 0);
  int currentPage = 1;
  int numberToDownload = 25;
  bool skipEmptyChats = true;
  bool saveToDownloads = false;
  bool syncGroupChatIcons = false;
  int? syncTimeFilter = 15552000000;
  String error = "";
  bool obscurePass = true;
  RxBool isSms = false.obs;
  RxBool supportsPhoneReg = false.obs;
  final GlobalKey<HwInpState> childKey = GlobalKey<HwInpState>();
  bool goingTo2fa = true;
  bool success = false;
  bool triedBattery = false;
  api.LoginState state = const api.LoginState.needsLogin();
  api.IdsUser? currentAppleUser;
  ArcMutexAppleAccountDefaultAnisetteProvider? currentAppleAccount;
  Map<int, api.IdsUser> currentPhoneUsers = <int, api.IdsUser>{};
  api.ReceiverApsMessage? connReceiver;
  ArcIdmsAuthListener? connListener;
  ApsConnection? connection;
  api.JoinedOsConfig? config;
  api.IdsngmIdentity? identity;
  ApsState? cachedState;
  ArcAnisetteClientDefaultAnisetteProvider? anisette;
  api.CircleClientSessionDefaultAnisetteProvider? circleSession;
  RxBool phoneValidating = false.obs;
  Rxn<SubscriptionOfferDetailsWrapper> availableIAP = Rxn();
  bool hasDanglingSubscription = false;
  String? token;
  DateTime tokenExpiry = DateTime.fromMillisecondsSinceEpoch(0);
  String? currentWaitlist;
  RxString noCapErrorMsg =
      "Currently full. If you got an invite, enter your code from your email. Otherwise, sign up to get notified!".obs;
  (String, String)? twoFaCreds;
  bool fetchedReferrer = false;

  int get pageOfNoReturn => kIsWeb || kIsDesktop ? 3 : 5;

  @override
  void dispose() {
    destroyConnection();
    super.dispose();
  }

  void updatePage(int newPage) {
    currentPage = newPage;
    updateWidgets<PageNumber>(newPage);
  }

  void updateNumberToDownload(int num) {
    numberToDownload = num;
    updateWidgets<NumberOfMessagesText>(num);
  }

  bool errorIsProblem() {
    return error.isNotEmpty &&
        !error.contains("Enter the correct password") &&
        !error.contains("Your account information was entered incorrectly") &&
        !error.contains("Sorry, your hosted device is currently offline!") &&
        (!error.contains("Relay device offline") || !SettingsSvc.settings.deviceIsHosted.value);
  }

  bool hasValidToken() => !tokenExpiry.isBefore(DateTime.now());

  Future<String> ensureToken() async {
    if (!hasValidToken()) {
      final headers = <String, dynamic>{};
      if (currentWaitlist != null) headers["X-OpenBubbles-Waitlist"] = currentWaitlist;
      final response = await HttpSvc.dio.post("https://hw.openbubbles.app/ticket", options: Options(headers: headers));
      if (response.statusCode == 429) throw Exception("Too many reserved tickets!");
      if (response.statusCode != 200) throw Exception(response.data);
      token = response.data["code"];
      tokenExpiry = DateTime.fromMillisecondsSinceEpoch(response.data["expiry"] * 1000, isUtc: true);
    }
    return token!;
  }

  Future<void> restoreTicket(String ticket, Duration length) async {
    token = ticket;
    tokenExpiry = DateTime.now().add(length);
    if (availableIAP.value == null) {
      await updateIAPState();
    }
  }

  Future<void> cacheCode(String code) async {
    if (SettingsSvc.settings.cachedCodes.containsKey(code)) return;
    final hash = hex.encode(sha256.convert(code.codeUnits).bytes);
    final response = await HttpSvc.dio.get(
      "$rpApiRoot/$hash",
      options: Options(headers: {"X-OpenBubbles-Get": ""}),
    );
    if (response.statusCode == 404) return;
    final data = response.data["data"];
    final bytes = Uint8List.fromList(decryptAESCryptoJS(data, code));
    SettingsSvc.settings.cachedCodes[code] = base64Encode(bytes);
    await SettingsSvc.settings.saveOneAsync('cachedCodes');
  }

  Future<void> updateIAPState() async {
    hasDanglingSubscription = false;
    if (currentWaitlist == null && !fetchedReferrer) {
      try {
        final referrerDetails = await AndroidPlayInstallReferrer.installReferrer;
        final referrer = referrerDetails.installReferrer;
        if (referrer != null && referrer.startsWith("WL") && currentWaitlist == null) {
          currentWaitlist = referrer.replaceFirst("WL", "");
        }
        if (referrer != null && referrer.startsWith("CD")) {
          await cacheCode(referrer.replaceFirst("CD", ""));
        }
      } catch (e, s) {
        Logger.error("failed to fetch referrer", error: e, trace: s);
      }
      fetchedReferrer = true;
    }

    if (!hasValidToken()) {
      var details = (await PushSvc.getPurchaseDetails())?.purchaseToken;
      details ??= SettingsSvc.settings.hostedToken.value;
      if (details != null) {
        final restore = await HttpSvc.dio.post("https://hw.openbubbles.app/restore", data: {"purchase_token": details});
        if (restore.statusCode == 200) {
          await restoreTicket(restore.data["code"], const Duration(days: 7));
          return;
        } else if (restore.statusCode == 404) {
          hasDanglingSubscription = true;
        }
      }

      final headers = <String, dynamic>{};
      if (currentWaitlist != null) headers["X-OpenBubbles-Waitlist"] = currentWaitlist;
      final status = await HttpSvc.dio.get("https://hw.openbubbles.app/status", options: Options(headers: headers));
      final hasCapacity = status.data["available"];
      if (!hasCapacity) {
        availableIAP.value = null;
        noCapErrorMsg.value = status.data["message"];
        return;
      }
    }

    final details = await PushSvc.client!.runWithClient(
      (client) => client.queryProductDetails(
          productList: [const ProductWrapper(productId: 'monthly_hosted', productType: ProductType.subs)]),
    );
    if (details.productDetailsList.isEmpty) {
      Logger.warn("Product not found!");
      availableIAP.value = null;
      return;
    }
    availableIAP.value = details.productDetailsList.first.subscriptionOfferDetails?.first;
  }

  Future<void> ensureWatcher() async {
    if (connReceiver != null) return;
    connReceiver = api.subscribeConn(conn: connection!);
    connListener = await api.makeIdms(conn: connection!);
  }

  void destroyConnection() {
    connReceiver?.dispose();
    connReceiver = null;
    connListener?.dispose();
    connListener = null;
    connection?.dispose();
    connection = null;
  }

  Future<void> setupConnection() async {
    final data =
        await api.setupPush(config: config!, identity: identity!, statePath: PushSvc.statePath, state: cachedState);
    connection = data.$1;
    anisette = await api.makeAnisette(path: PushSvc.statePath, config: config!, conn: connection!);
  }

  Future<void> configureHostedDevice(api.JoinedOsConfig newConfig) async {
    identity = api.newNgmIdentity();
    config = newConfig;
    cachedState = null;
    destroyConnection();
    api.resetAnisette(path: PushSvc.statePath);
    anisette?.dispose();
    anisette = null;
    currentPhoneUsers = <int, api.IdsUser>{};
    final entries = SettingsSvc.settings.cachedCodes.entries.toList();
    for (final item in entries) {
      if (item.key.startsWith("sms-auth-")) {
        SettingsSvc.settings.cachedCodes.remove(item.key);
      }
    }
    await SettingsSvc.settings.saveOneAsync('cachedCodes');
    await setupConnection();
  }

  Future<T> wrapPromise<T>(Future<T> inner, String text) async {
    showDialog(
      context: Get.context!,
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
      Navigator.of(Get.context!, rootNavigator: true).pop();
      return result;
    } catch (e) {
      Navigator.of(Get.context!, rootNavigator: true).pop();
      showSnackbar("Failure! Please try again", e.toString());
      rethrow;
    }
  }

  void handleOfflineError(String newError, String? currentTicket) {
    if (newError.contains("Device not reserved!")) {
      destroyConnection();
      config = null;
      tokenExpiry = DateTime.fromMillisecondsSinceEpoch(0);
      token = null;
      pageController.jumpToPage(4);
    }

    if (newError.contains("Sorry, your hosted device is currently offline!")) {
      showDialog(
        context: Get.context!,
        builder: (context) => AlertDialog(
          title: Text("Sorry, your hosted device is currently offline!", style: context.theme.textTheme.titleLarge),
          backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
          content: Text("You can wait for it to come back, or change your device.",
              style: context.theme.textTheme.bodyLarge),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
              child: Text("Cancel",
                  style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
            ),
            TextButton(
              onPressed: () {
                Navigator.of(context, rootNavigator: true).pop();
                wrapPromise(() async {
                  final relay = currentTicket ?? await api.validateRelay(configRef: config!);
                  if (relay == null) throw Exception("Failed to validate!");
                  final status = await HttpSvc.dio.post(
                    "https://hw.openbubbles.app/swap-token",
                    options: Options(headers: {"Authorization": "Bearer $relay"}),
                  );
                  if (status.statusCode != 200) {
                    if (status.data.toString().contains("No device available!")) {
                      Timer(const Duration(milliseconds: 100), () => PushSvc.offerHostedRefund(false));
                    }
                    throw Exception("Failed to swap ${status.statusCode} ${status.data}");
                  }
                  final newTicket = status.data["new_ticket"];
                  final newConfig = await api.configFromRelay(code: newTicket, host: "https://hw.openbubbles.app");
                  await configureHostedDevice(newConfig);
                  pageController.jumpToPage(4);
                  updateConnectError('');
                }(), "Changing device...");
              },
              child: Text("Change",
                  style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
            ),
          ],
        ),
      );
    }
  }

  Future<api.LoginState> updateLoginState(api.LoginState ret) async {
    if (ret is api.LoginState_NeedsLogin) {
      ArcMutexAppleAccountDefaultAnisetteProvider account;
      (account, ret) = await api.tryAuth(
        path: PushSvc.statePath,
        conf: config!,
        conn: connection!,
        anisette: anisette!,
        creds: twoFaCreds,
      );
      currentAppleAccount?.dispose();
      currentAppleAccount = account;
      currentAppleUser = await api.tryIcloudLogin(path: PushSvc.statePath, conf: config!, account: account);
    }
    if (ret is api.LoginState_NeedsDevice2FA) {
      await ensureWatcher();
      final (provider, nextState, sid) = await api.send2FaToDevices(state: currentAppleAccount!, conn: connection!);
      await MethodChannelSvc.invokeMethod("circle-proximity-session", {'sid': sid});
      ret = nextState;
      isSms.value = false;
      circleSession = provider;
    }
    if (ret is api.LoginState_NeedsSMS2FA) {
      await MethodChannelSvc.invokeMethod("circle-proximity-session", {'sid': null});
      final options = await api.get2FaSmsOpts(state: currentAppleAccount!);
      if (options.$2 != null) {
        ret = options.$2!;
      } else if (options.$1.length == 1) {
        ret = await api.send2FaSms(locked: circleSession, account: currentAppleAccount!, phoneId: options.$1[0].id);
        circleSession = null;
      } else {
        int selectedRadio = -1;
        await showDialog(
          context: Get.context!,
          builder: (context) => AlertDialog(
            title: const Text('Choose number'),
            content: StatefulBuilder(
              builder: (context, setState) => Column(
                mainAxisSize: MainAxisSize.min,
                children: options.$1
                    .map(
                      (e) => RadioListTile<int>(
                        value: e.id,
                        groupValue: selectedRadio,
                        title: Text(e.numberWithDialCode),
                        onChanged: (val) => setState(() => selectedRadio = val!),
                      ),
                    )
                    .toList(),
              ),
            ),
            actions: [
              TextButton(
                onPressed: () {
                  selectedRadio = -1;
                  Navigator.of(context, rootNavigator: true).pop();
                },
                child: Text("Cancel",
                    style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
              ),
              TextButton(
                onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
                child: Text("OK",
                    style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
              ),
            ],
          ),
        );
        if (selectedRadio == -1) return ret;
        ret = await api.send2FaSms(locked: circleSession, account: currentAppleAccount!, phoneId: selectedRadio);
        circleSession = null;
      }
      isSms.value = true;
    }
    state = ret;
    if (ret is api.LoginState_LoggedIn) {
      await MethodChannelSvc.invokeMethod("circle-proximity-session", {'sid': null});
      SettingsSvc.settings.userName.value = await api.getUserName(state: currentAppleAccount!);
      await SettingsSvc.settings.saveOneAsync('userName');
      await doRegister();
    }
    return ret;
  }

  void updateSucceeded(Function finish, api.UpdateAccountFinish updateFinish) async {
    finish(true);
    updateConnectError('');
    try {
      currentAppleUser = await api.doLogin(
          path: PushSvc.statePath, account: currentAppleAccount!, osConfig: config!, finish: updateFinish);
      SettingsSvc.settings.userName.value = await api.getUserName(state: currentAppleAccount!);
      await SettingsSvc.settings.saveOneAsync('userName');
      await doRegister();
    } catch (e) {
      if (e is AnyhowException) updateConnectError(e.message);
      if (e is PanicException) updateConnectError(e.message);
      rethrow;
    } finally {
      finish(false);
    }
  }

  Future<void> updateAccountUi(Function finish) async {
    final (data, finalI) = await api.updateAccountHeaders(account: currentAppleAccount!, config: config!);
    final request = URLRequest(url: WebUri("https://inappwebview.dev/"));
    double height = min(400, MediaQuery.sizeOf(Get.context!).height - 200);
    showDialog(
      context: Get.context!,
      builder: (context) => StatefulBuilder(
        builder: (context, setState) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                height: height,
                child: InAppWebView(
                  initialUrlRequest: request,
                  initialData: InAppWebViewInitialData(
                      data: data, baseUrl: WebUri("https://setup.icloud.com/setup/update_account_ui")),
                  initialSettings: InAppWebViewSettings(
                    useShouldInterceptAjaxRequest: true,
                    interceptOnlyAsyncAjaxRequests: false,
                    useShouldInterceptFetchRequest: true,
                  ),
                  shouldInterceptAjaxRequest: (controller, request) async {
                    final headers = await api.getAnisetteHeaders(state: anisette!, config: config!);
                    for (final header in headers.entries) {
                      request.headers!.setRequestHeader(header.key, header.value);
                    }
                    return request;
                  },
                  shouldInterceptFetchRequest: (controller, request) async {
                    final headers = await api.getAnisetteHeaders(state: anisette!, config: config!);
                    request.headers ??= {};
                    request.headers!.addAll(headers);
                    return request;
                  },
                  onWebViewCreated: (controller) {
                    controller.addJavaScriptHandler(
                      handlerName: 'cancel',
                      callback: (_) => Navigator.of(Get.context!, rootNavigator: true).pop(),
                    );
                    controller.addJavaScriptHandler(
                        handlerName: 'updateSucceeded',
                        callback: (_) {
                          updateSucceeded(finish, finalI);
                          Navigator.of(Get.context!, rootNavigator: true).pop();
                        });
                    controller.addJavaScriptHandler(
                        handlerName: 'confirmWithCallback',
                        callback: (_) {
                          updateSucceeded(finish, finalI);
                          Navigator.of(Get.context!, rootNavigator: true).pop();
                        });
                    controller.addJavaScriptHandler(
                      handlerName: 'resizeToWindow',
                      callback: (args) => setState(() => height = (args[1] as num).toDouble()),
                    );
                    controller.injectJavascriptFileFromAsset(assetFilePath: "assets/scripts/AppleAccountSetup.js");
                  },
                ),
              ),
              TextButton(
                onPressed: () {
                  updateSucceeded(finish, finalI);
                  Navigator.of(Get.context!, rootNavigator: true).pop();
                },
                child: const Text('Accept Terms', style: TextStyle(color: Colors.white)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<api.LoginState> submitCode(String code) async {
    if (state is api.LoginState_Needs2FAVerification) {
      final (dartState, user) = await api.verify2Fa(
        path: PushSvc.statePath,
        client: circleSession!,
        anisette: anisette!,
        osConfig: config!,
        watcher: connReceiver!,
        idms: connListener!,
        account: currentAppleAccount!,
        code: code,
      );
      state = dartState;
      currentAppleUser = user;
    } else if (state is api.LoginState_NeedsSMS2FAVerification) {
      final myState = state as api.LoginState_NeedsSMS2FAVerification;
      final (dartState, user) = await api.verify2FaSms(
        path: PushSvc.statePath,
        accountMut: currentAppleAccount!,
        anisette: anisette!,
        config: config!,
        body: myState.field0,
        code: code,
      );
      state = dartState;
      currentAppleUser = user;
    }
    return await updateLoginState(state);
  }

  Future<void> doRegister() async {
    final users = <api.IdsUser>[];
    if (currentAppleUser != null) users.add(currentAppleUser!);
    if (currentPhoneUsers.isNotEmpty && supportsPhoneReg.value) {
      users.addAll(currentPhoneUsers.values);
    }
    if (users.isEmpty) throw Exception("No users to register!");

    final (newUsers, response) = await api.registerIds(
      path: PushSvc.statePath,
      aps: connection!,
      identity: identity!,
      config: config!,
      users: users.map((i) => api.duplicateUser(user: i)).toList(),
    );
    if (response != null) {
      final devInfo = await api.getDeviceInfo(config: config!);
      await showDialog(
        context: Get.context!,
        builder: (context) => AlertDialog(
          backgroundColor: Get.theme.colorScheme.surfaceContainerHighest,
          title: Text(response.title, style: Get.textTheme.titleLarge),
          content: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(response.body, style: Get.textTheme.bodyLarge),
              const SizedBox(height: 20),
              Align(
                alignment: Alignment.center,
                child: Text(
                  "The above message is from Apple.\nWarning: Do not contact Apple support for help with OpenBubbles. Do not mention OpenBubbles. For assistance, join our Discord from our website. If you can't login on this Apple Device, call Apple support. When clicking the contact button below, choose 'Get Started,' not 'Chat,' and then choose 'Apple Device.' Do not mention you are using OpenBubbles.\n${RustPushBBUtils.modelToUser(devInfo.name)}\nS/N: ${devInfo.serial}\nmacOS ${devInfo.osVersion}",
                  textAlign: TextAlign.center,
                  style: Get.textTheme.bodySmall,
                ),
              ),
            ],
          ),
          actions: [
            if (response.action != null)
              TextButton(
                onPressed: () =>
                    launchUrl(Uri.parse("https://apple.co/IMFT-mac"), mode: LaunchMode.externalApplication),
                child: Text(response.action!.button,
                    style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
              ),
            TextButton(
              onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
              child: Text("OK",
                  style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
            ),
          ],
        ),
      );
      return;
    }

    final imclient =
        await api.makeImclient(path: PushSvc.statePath, conn: connection!, users: newUsers!, identity: identity!);
    await ensureWatcher();
    final watcher = api.importWatcher(queue: connReceiver!, client: imclient);
    final clientSession = api.makeClientSession(circle: circleSession);
    final stagingPushState = api.SharedPushState(
      osConfig: config!,
      cancelPoll: watcher.$1,
      confDir: PushSvc.statePath,
      localBroadcast: watcher.$2,
      anisette: anisette!,
      conn: connection!,
      client: imclient,
      icloudServices: currentAppleAccount == null
          ? null
          : await (() async {
              final tokenProvider = api.makeTokenProvider(account: currentAppleAccount!, config: config!);
              final cloudkit = await api.makeCloudkit(
                  path: PushSvc.statePath, anisette: anisette!, config: config!, tokenProvider: tokenProvider);
              final keychain = cloudkit == null
                  ? null
                  : api.makeKeychain(
                      path: PushSvc.statePath,
                      cloudkit: cloudkit,
                      anisette: anisette!,
                      config: config!,
                      tokenProvider: tokenProvider);
              return api.SharedICloudServices(
                account: currentAppleAccount!,
                tokenProvider: tokenProvider,
                cloudkitClient: cloudkit,
                keychain: keychain,
                passwords: keychain == null
                    ? null
                    : await api.makePasswords(
                        path: PushSvc.statePath,
                        keychain: keychain,
                        cloudkit: cloudkit!,
                        client: imclient,
                        conn: connection!),
                profilesClient: await api.makeProfiles(cloudkit: cloudkit!),
                fmfd: keychain == null
                    ? null
                    : await api.makeFindmy(
                        path: PushSvc.statePath,
                        tokenProvider: tokenProvider,
                        conn: connection!,
                        cloudkit: cloudkit,
                        keychain: keychain,
                        anisette: anisette!,
                        config: config!,
                        client: imclient,
                      ),
                sharedstreams: await api.makeSharedStreams(
                    path: PushSvc.statePath,
                    conn: connection!,
                    anisette: anisette!,
                    config: config!,
                    token: tokenProvider),
                cloudMessagesClient: api.makeCloudMessagesClient(cloudkit: cloudkit, keychain: keychain!),
                statuskitClient: await api.makeStatuskit(
                    path: PushSvc.statePath,
                    provider: tokenProvider,
                    conn: connection!,
                    config: config!,
                    client: imclient),
              );
            })(),
      ftClient: await api.makeFacetime(path: PushSvc.statePath, conn: connection!, client: imclient),
      idmsClient: connListener!,
      activeCircleSessions: api.makeCircleSessions(),
      clientSession: clientSession,
    );

    if (Platform.isAndroid) {
      final (daemon, pushState) = api.sendDaemon(state: stagingPushState, watcher: watcher.$3);
      PushSvc.state = pushState;
      MethodChannelSvc.invokeMethod("provision-native", {"native": daemon});
    } else {
      final (pollState, deskState) = api.dupDaemonDesk(state: stagingPushState);
      PushSvc.state = deskState;
      PushSvc.doPoll(watcher.$3, pollState);
    }

    success = true;
    if (SettingsSvc.settings.deviceIsHosted.value) {
      PushSvc.mixpanel?.track("hosted-setup-success");
    }
    await PushSvc.configured();

    final handles = await api.getHandles(state: PushSvc.state!.client);
    final phone = handles.firstWhereOrNull((h) => h.startsWith("tel:"));
    if (phone != null) {
      SettingsSvc.settings.defaultHandle.value = phone;
      await SettingsSvc.settings.saveOneAsync('defaultHandle');
    }

    final keychain = PushSvc.state?.icloudServices?.keychain;
    if (keychain != null && circleSession != null && !SettingsSvc.settings.isDumb.value) {
      final defaultPassword = Random.secure().nextInt(1000000).toString().padLeft(6, '0');
      SettingsSvc.settings.keychainDefaultPassword.value = defaultPassword;
      await SettingsSvc.settings.saveOneAsync('keychainDefaultPassword');
      await api.circleSetupClique(
          client: PushSvc.state!.clientSession, keychain: keychain, devicePassword: defaultPassword);
    }

    await setup.finishSetup();
  }

  void updateConnectError(String newError) {
    handleOfflineError(newError, null);
    if (newError.contains("6005") && currentPhoneUsers.isNotEmpty) {
      for (final user in currentPhoneUsers.keys) {
        SettingsSvc.settings.cachedCodes.remove("sms-auth-$user");
      }
      SettingsSvc.settings.saveOneAsync('cachedCodes');
      currentPhoneUsers.clear();
      newError = "Phone Number validation failed, please re-authenticate!";
      updateWidgets<ErrorText>(newError);
      pageController.jumpToPage(5);
      return;
    }
    error = newError;
    if (SettingsSvc.settings.deviceIsHosted.value && errorIsProblem()) {
      PushSvc.mixpanel?.track("hosted-setup-error");
    }
    updateWidgets<ErrorText>(newError);
  }
}

class SetupView extends StatefulWidget {
  final (
    ApsConnection,
    ApsState,
    api.JoinedOsConfig,
    api.IdsngmIdentity,
    ArcAnisetteClientDefaultAnisetteProvider
  )? prefix;

  const SetupView({super.key, this.prefix});

  @override
  State<SetupView> createState() => _SetupViewState();
}

class _SetupViewState extends State<SetupView> {
  final controller = Get.put(SetupViewController(), permanent: true);

  @override
  void initState() {
    super.initState();

    (() async {
      if (SettingsSvc.settings.cachedCodes.containsKey("sms-auth")) {
        SettingsSvc.settings.cachedCodes["sms-auth-1"] = SettingsSvc.settings.cachedCodes["sms-auth"]!;
        SettingsSvc.settings.cachedCodes.remove("sms-auth");
        SettingsSvc.settings.saveOneAsync('cachedCodes');
      }
      await PushSvc.initFuture;

      final restored = api.readHardware(path: PushSvc.statePath);
      if (widget.prefix != null) {
        controller.connection = widget.prefix!.$1;
        controller.cachedState = widget.prefix!.$2;
        controller.config = widget.prefix!.$3;
        controller.identity = widget.prefix!.$4;
        controller.anisette = widget.prefix!.$5;
      }

      if (restored != null && PushSvc.state == null && controller.connection == null) {
        controller.identity = api.decodeIdentity(identity: restored.identity);
        controller.config = restored.osConfig;
        controller.cachedState = restored.push;
        await controller.setupConnection();
      }

      final dumb = File("${PushSvc.statePath}/dumb");
      if (restored == null && dumb.existsSync()) {
        SettingsSvc.settings.isDumb.value = true;
        SettingsSvc.settings.macIsMine.value = false;
        await SettingsSvc.settings.saveAsync();
        try {
          final list = base64Decode(dumb.readAsStringSync()).toList();
          list.removeRange(0, 5);
          final imported = await api.configFromEncoded(encoded: list);
          controller.config = imported;
          controller.identity = api.newNgmIdentity();
          await controller.setupConnection();
        } catch (e, s) {
          Logger.error("Failed to setup dumb", error: e, trace: s);
        }
      }

      final list = SettingsSvc.settings.cachedCodes.entries.toList();
      for (final items in list) {
        if (!items.key.startsWith("sms-auth-")) continue;
        controller.phoneValidating.value = true;
        try {
          final user = await api.restoreUser(user: items.value);
          await api.validateCert(conn: controller.connection!, user: user);
          controller.currentPhoneUsers[int.parse(items.key.replaceFirst("sms-auth-", ""))] = user;
        } catch (_) {
          SettingsSvc.settings.cachedCodes.remove(items.key);
          SettingsSvc.settings.saveOneAsync('cachedCodes');
        } finally {
          controller.phoneValidating.value = false;
        }
      }
    })();

    (() async {
      try {
        await controller.updateIAPState();
      } catch (e, s) {
        Logger.error("failed to fetch IAP state", error: e, trace: s);
      }
      final appLinks = AppLinks();
      final link = await appLinks.getLatestLink();

      appLinks.uriLinkStream.listen((uri) async {
        final text = uri.toString();
        const ticketHeader = "https://hw.openbubbles.app/ticket/";
        const waitlistHeader = "https://hw.openbubbles.app/waitlist/";
        if (text.startsWith(ticketHeader)) {
          await controller.restoreTicket(text.replaceFirst(ticketHeader, ""), const Duration(minutes: 15));
          return;
        }
        if (text.startsWith(waitlistHeader)) {
          controller.currentWaitlist = text.replaceFirst(waitlistHeader, "");
          await controller.updateIAPState();
          return;
        }
        final header = "$rpApiRoot/";
        if (text.startsWith(header)) {
          await controller.cacheCode(text.replaceFirst(header, ""));
          controller.childKey.currentState?.updateInitial();
        }
      });

      if (link != null) {
        final text = link.toString();
        const ticketHeader = "https://hw.openbubbles.app/ticket/";
        const waitlistHeader = "https://hw.openbubbles.app/waitlist/";
        if (text.startsWith(ticketHeader)) {
          await controller.restoreTicket(text.replaceFirst(ticketHeader, ""), const Duration(minutes: 15));
          return;
        }
        if (text.startsWith(waitlistHeader)) {
          controller.currentWaitlist = text.replaceFirst(waitlistHeader, "");
          await controller.updateIAPState();
          return;
        }
        final header = "$rpApiRoot/";
        if (text.startsWith(header)) {
          await controller.cacheCode(text.replaceFirst(header, ""));
        }
      }
    })();

    ever(SocketSvc.state, (event) {
      if (event == SocketState.error &&
          !SettingsSvc.settings.finishedSetup.value &&
          controller.pageController.hasClients &&
          controller.currentPage > controller.pageOfNoReturn) {
        showDialog(
          context: context,
          barrierDismissible: false,
          builder: (context) => FailedToConnectDialog(
            onDismiss: () {
              controller.pageController.animateToPage(
                controller.pageOfNoReturn - 1,
                duration: const Duration(milliseconds: 500),
                curve: Curves.easeInOut,
              );
              Navigator.of(context, rootNavigator: true).pop();
            },
          ),
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: Scaffold(
        backgroundColor: SettingsSvc.settings.windowEffect.value != WindowEffect.disabled
            ? Colors.transparent
            : context.theme.colorScheme.surface,
        body: SafeArea(
          child: Column(
            children: <Widget>[
              SetupHeader(),
              const SizedBox(height: 20),
              SetupPages(),
            ],
          ),
        ),
      ),
    );
  }
}

class SetupHeader extends StatelessWidget {
  final SetupViewController controller = Get.find<SetupViewController>();

  SetupHeader({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(top: kIsDesktop ? 40 : 20, left: 20, right: 20),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              Hero(tag: "setup-icon", child: Image.asset("assets/icon/icon.png", width: 30, fit: BoxFit.contain)),
              const SizedBox(width: 10),
              Text(
                "OpenBubbles",
                style: context.theme.textTheme.bodyLarge!.apply(fontWeightDelta: 2, fontSizeFactor: 1.35),
              ),
            ],
          ),
          PageNumber(parentController: controller),
        ],
      ),
    );
  }
}

class PageNumber extends CustomStateful<SetupViewController> {
  const PageNumber({super.key, required super.parentController});

  @override
  State<StatefulWidget> createState() => _PageNumberState();
}

class _PageNumberState extends CustomState<PageNumber, int, SetupViewController> {
  @override
  void updateWidget(int newVal) {
    controller.currentPage = newVal;
    super.updateWidget(newVal);
  }

  @override
  Widget build(BuildContext context) {
    return controller.currentPage == 1
        ? const SizedBox.square(dimension: 40.0)
        : Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(25),
              gradient: LinearGradient(
                begin: AlignmentDirectional.topStart,
                colors: [HexColor('2772C3'), HexColor('5CA7F8').darkenPercent(5)],
              ),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 8.0, horizontal: 13),
              child: RichText(
                text: TextSpan(
                  children: [
                    TextSpan(
                      text: "${controller.currentPage}",
                      style:
                          context.theme.textTheme.bodyLarge!.copyWith(color: Colors.white, fontWeight: FontWeight.bold),
                    ),
                    TextSpan(
                      text: " of ${kIsWeb ? "4" : kIsDesktop ? "5" : "9"}",
                      style: context.theme.textTheme.bodyLarge!
                          .copyWith(color: Colors.white38, fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
              ),
            ),
          );
  }
}

class ErrorText extends CustomStateful<SetupViewController> {
  ErrorText({super.key, required super.parentController});

  @override
  State<StatefulWidget> createState() => _ErrorTextState();
}

class _ErrorTextState extends CustomState<ErrorText, String, SetupViewController> {
  @override
  void updateWidget(String newVal) {
    controller.error = newVal;
    super.updateWidget(newVal);
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (controller.error.isNotEmpty)
          SizedBox(
            width: context.width * 2 / 3,
            child: Align(
              alignment: Alignment.center,
              child: SelectableText(
                controller.error,
                style: context.theme.textTheme.bodyLarge!
                    .apply(fontSizeDelta: 1.5, color: context.theme.colorScheme.error)
                    .copyWith(height: 2),
              ),
            ),
          ),
        if (controller.errorIsProblem())
          TextButton(
            onPressed: () async {
              final participantController = TextEditingController();
              final details = TextEditingController();
              Uint8List? attachment;
              showDialog(
                context: context,
                builder: (_) => AlertDialog(
                  title: Text("Report issue", style: context.theme.textTheme.titleLarge),
                  backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                  content: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Text(
                          "Logs and Apple device identifiers will be sent to developer for review. Logs may contain personal identifiers and 48 hours of message and chat history. Do not submit logs containing sensitive chats or messages. Your logs will be shared with Discord for storage subject to their Privacy Policy."),
                      const SizedBox(height: 16),
                      TextField(
                        controller: participantController,
                        decoration: const InputDecoration(labelText: "Your email", border: OutlineInputBorder()),
                        keyboardType: TextInputType.multiline,
                        maxLines: null,
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: details,
                        decoration: const InputDecoration(labelText: "Optional details", border: OutlineInputBorder()),
                        keyboardType: TextInputType.multiline,
                        maxLines: null,
                      ),
                    ],
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
                      child: Text("Cancel",
                          style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
                    ),
                    TextButton(
                      onPressed: () async {
                        final res = await picker.FilePicker.pickFiles(
                            withData: true, type: picker.FileType.custom, allowedExtensions: ['png', 'jpg', 'jpeg']);
                        if (res == null || res.count == 0) return;
                        attachment = await File(res.files[0].path!).readAsBytes();
                        showSnackbar("Notice", "Screenshot added");
                      },
                      child: Text("Screenshot",
                          style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
                    ),
                    TextButton(
                      onPressed: () async {
                        if (participantController.text.isEmpty) return;
                        showDialog(
                          context: context,
                          builder: (context) => AlertDialog(
                            backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                            title: Text("Uploading log...", style: context.theme.textTheme.titleLarge),
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

                        final file = Directory(
                          Platform.isAndroid
                              ? "${FilesystemSvc.appDocDir.path}/../files/logs"
                              : "${FilesystemSvc.appDocDir.path}/logs",
                        );
                        final entities = await file.list().toList();
                        final current = entities.indexWhere((element) => element.path.endsWith("CURRENT.log"));
                        final item = entities.removeAt(current);
                        final end = await File(item.path).readAsBytes();
                        final b = BytesBuilder();
                        if (entities.isNotEmpty) {
                          final next = await File(entities.first.path).readAsBytes();
                          b.add(next);
                        }
                        b.add(end);
                        final total = b.toBytes();

                        Map<String, dynamic> deviceInfo = {};
                        final currentConfig = controller.config;
                        if (currentConfig != null) {
                          final info = await api.getDeviceInfo(config: currentConfig);
                          deviceInfo = {
                            "name": info.name,
                            "serial": info.serial,
                            "os_version": info.osVersion,
                            "encoded_data": info.encodedData != null ? base64Encode(info.encodedData!) : "",
                          };
                        }

                        final url = dotenv.get('REPORT_ISSUE_WEBHOOK');
                        try {
                          final response = await HttpSvc.dio.post(
                            url,
                            data: FormData.fromMap({
                              "content":
                                  "Desc: ${details.text}\nEmail: ${participantController.text}\nError: ${controller.error}\nHosted: ${SettingsSvc.settings.deviceIsHosted.value}",
                              "username": "Onboarding",
                              "files[0]": MultipartFile.fromBytes(total, filename: "rustpush-logs.log"),
                              "files[1]": MultipartFile.fromString(jsonEncode(deviceInfo), filename: "hardware.json"),
                              if (attachment != null)
                                "files[2]": MultipartFile.fromBytes(attachment!, filename: "screenshot.png"),
                            }),
                          );
                          Navigator.of(context, rootNavigator: true).pop();
                          Navigator.of(context, rootNavigator: true).pop();
                          if (response.statusCode == 200) {
                            showSnackbar("Notice", "Logs sent! Thank you!");
                          } else {
                            showSnackbar("Error", "There was an issue sending logs");
                          }
                        } catch (e, s) {
                          Navigator.of(context, rootNavigator: true).pop();
                          Logger.error("failed", error: e, trace: s);
                          showSnackbar("Error", "There was an issue sending logs $e");
                        }
                      },
                      child: Text("OK",
                          style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
                    ),
                  ],
                ),
              );
            },
            child: Text(
              "Report Error",
              style: context.theme.textTheme.bodyMedium!
                  .apply(color: context.theme.colorScheme.error, decoration: TextDecoration.underline),
            ),
          ),
        if (controller.error.isNotEmpty) const SizedBox(height: 20),
      ],
    );
  }
}

class SetupPages extends StatelessWidget {
  final SetupViewController controller = Get.find<SetupViewController>();

  SetupPages({super.key});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Obx(
        () => PageView(
          onPageChanged: (page) {
            if (!kIsWeb &&
                !kIsDesktop &&
                page == 2 &&
                controller.currentPage == 2 &&
                !SettingsSvc.settings.isDumb.value) {
              DisableBatteryOptimization.isAllBatteryOptimizationDisabled.then((isDisabled) {
                if (isDisabled ?? false) {
                  controller.pageController
                      .nextPage(duration: const Duration(milliseconds: 300), curve: Curves.easeInOut);
                }
              });
            }
            controller.updatePage(page + 1);
          },
          physics: const NeverScrollableScrollPhysics(),
          controller: controller.pageController,
          children: <Widget>[
            if (!SettingsSvc.settings.isDumb.value) const WelcomePage(),
            if (!kIsWeb && !kIsDesktop && !SettingsSvc.settings.isDumb.value) const RequestPermissions(),
            if (!kIsWeb && !kIsDesktop && !SettingsSvc.settings.isDumb.value) const BatteryOptimizationCheck(),
            if (!usingRustPush) const MacSetupCheck(),
            if (!usingRustPush) const ServerCredentials(),
            if (!kIsWeb && !usingRustPush) SyncSettings(),
            if (!usingRustPush) const SyncProgress(),
            if (usingRustPush && !SettingsSvc.settings.isDumb.value) HwInp(key: controller.childKey),
            if (usingRustPush && controller.supportsPhoneReg.value && !kIsDesktop) const PhoneNumber(),
            if (usingRustPush) AppleIdLogin(),
            if (usingRustPush) AppleId2FA(),
            if (usingRustPush) FinalizePage(),
          ],
        ),
      ),
    );
  }
}
