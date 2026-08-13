import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:async_task/async_task_extension.dart';
import 'package:bluebubbles/app/layouts/settings/dialogs/custom_headers_dialog.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/settings_leading_icon.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/settings_switch.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/settings_tile.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/layout/settings_section.dart';
import 'package:bluebubbles/app/layouts/setup/dialogs/failed_to_scan_dialog.dart';
import 'package:bluebubbles/app/layouts/setup/pages/page_template.dart';
import 'package:bluebubbles/app/layouts/setup/pages/sync/qr_code_scanner.dart';
import 'package:bluebubbles/app/layouts/setup/setup_view.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/crypto_utils.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_rust_bridge/flutter_rust_bridge.dart';
import 'package:get/get.dart' hide Response;
import 'package:permission_handler/permission_handler.dart';
import 'package:telephony_plus/telephony_plus.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:convert/convert.dart';
import 'package:app_links/app_links.dart';

class PhoneNumber extends StatefulWidget {
  @override
  State<PhoneNumber> createState() => PhoneNumberState();

  const PhoneNumber({super.key});
}

class PhoneNumberState extends State<PhoneNumber> with ThemeHelpers {
  final TextEditingController codeController = TextEditingController();
  final controller = Get.find<SetupViewController>();
  final FocusNode focusNode = FocusNode();

  final RxBool failed = false.obs;

  final RxBool tempRegister = true.obs;

  @override
  void initState() {
    super.initState();

    subscribe();
  }

  void subscribe() async {
    try {
      await MethodChannelSvc.invokeMethod("sim-info-query", {"subscribe": true});
      failed.value = false;
    } catch (e) {
      failed.value = true;
      rethrow;
    }
  }

  RxBool failedSms = false.obs;

  Future<void> subscribeSubscription(int subscription, {bool trySmsLess = true}) async {
    if (SettingsSvc.settings.cachedCodes.containsKey("sms-auth-$subscription")) {
      controller.currentPhoneUsers[subscription] =
          await api.restoreUser(user: SettingsSvc.settings.cachedCodes["sms-auth-$subscription"]!);
      controller.updateConnectError("");
      setState(() {});
      return;
    }
    failedSms.value = false;
    controller.phoneValidating.value = true;

    if (trySmsLess) {
      try {
        String resp = await MethodChannelSvc.invokeMethod("sms-less-auth-gateway", {'subscription': subscription});
        Map<dynamic, dynamic> parsed = json.decode(resp);

        var user = await api.getEntitlements(
            config: controller.config!,
            conn: controller.connection!,
            mccmnc: parsed["mccmnc"],
            subscriber: parsed["subscriber"],
            imei: parsed["imei"],
            processChallenge: (challenge) async {
              return await MethodChannelSvc.invokeMethod(
                  "eap-aka-gateway", {'subscription': subscription, 'challenge': challenge});
            });

        controller.currentPhoneUsers[subscription] = user;
        SettingsSvc.settings.cachedCodes["sms-auth-$subscription"] = await api.saveUser(user: user);
        await SettingsSvc.settings.saveOneAsync('cachedCodes');
        controller.updateConnectError("");
        setState(() {});
        controller.phoneValidating.value = false;
        return;
      } catch (e, s) {
        if (e is AnyhowException) {
          var msg = e.message;
          Logger.info("IccData", error: e, trace: s);
          if (!msg.contains("No ICC auth permission!") && !msg.contains("Carrier does not support ICC auth!")) {
            controller.updateConnectError(msg);
            controller.phoneValidating.value = false;
            rethrow;
          }
        } else if (e is PlatformException) {
          var msg = e.code;
          Logger.info("IccData", error: e, trace: s);
          if (!msg.contains("No ICC auth permission!") && !msg.contains("Carrier does not support ICC auth!")) {
            controller.updateConnectError(msg);
            controller.phoneValidating.value = false;
            rethrow;
          }
        } else {
          controller.phoneValidating.value = false;
          rethrow;
        }
      }
    }

    try {
      var granted = await TelephonyPlus().requestPermissions();
      if (!granted) {
        showSnackbar("SMS denied", "Please enable SMS permission in settings");
        return;
      }
      // always get new token for PNR
      if (controller.currentPhoneUsers.isEmpty) {
        controller.destroyConnection();
        controller.cachedState = null;

        var data = await api.setupPush(
            config: controller.config!,
            identity: controller.identity!,
            statePath: PushSvc.statePath,
            state: controller.cachedState);
        controller.connection = data.$1;
        controller.anisette?.dispose();
        controller.anisette =
            await api.makeAnisette(path: PushSvc.statePath, config: controller.config!, conn: controller.connection!);
      }
      var token = await api.getToken(state: controller.connection!);

      String resp = await MethodChannelSvc.invokeMethod(
          "sms-auth-gateway", {'token': hex.encode(token).toUpperCase(), 'subscription': subscription});
      controller.currentPhoneUsers[subscription] = await api.authPhone(
          conn: controller.connection!,
          config: controller.config!,
          number: resp.split("|").first,
          sig: hex.decode(resp.split("|").last));
      SettingsSvc.settings.cachedCodes["sms-auth-$subscription"] =
          await api.saveUser(user: controller.currentPhoneUsers[subscription]!);
      await SettingsSvc.settings.saveOneAsync('cachedCodes');
      controller.updateConnectError("");
      setState(() {});
    } catch (e) {
      if (e is PlatformException) {
        var msg = e.code;
        controller.updateConnectError(msg);
      }
      if (e is AnyhowException) {
        controller.updateConnectError(e.message);
      }
      if (e is PanicException) {
        controller.updateConnectError(e.message);
      }
      failedSms.value = true;
      rethrow;
    } finally {
      controller.phoneValidating.value = false;
    }
  }

  @override
  void dispose() {
    super.dispose();

    MethodChannelSvc.invokeMethod("sim-info-query", {"subscribe": false});
  }

  @override
  Widget build(BuildContext context) {
    return SetupPageTemplate(
      title: "Phone Number",
      subtitle: "Use your phone number with iMessage. Requires SMS permission to receive the silent confirmation code.",
      customButton: Column(
        children: [
          ErrorText(parentController: controller),
          AnimatedSize(
            duration: const Duration(milliseconds: 200),
            child: Theme(
              data: context.theme.copyWith(
                inputDecorationTheme: InputDecorationTheme(
                  labelStyle: TextStyle(color: context.theme.colorScheme.outline),
                ),
              ),
              child: Obx(() => Column(
                    children: [
                      if (failed.value)
                        SettingsSwitch(
                          padding: false,
                          onChanged: (bool val) async {
                            tempRegister.value = val;
                          },
                          initialVal: tempRegister.value,
                          title: "Register this number",
                          subtitle: "Use this phone number with OpenBubbles and your other Apple devices",
                          backgroundColor: tileColor,
                          isThreeLine: true,
                        ),
                      if (failedSms.value)
                        TextButton(
                            onPressed: () async {
                              await showDialog(
                                context: Get.context!,
                                builder: (context) => AlertDialog(
                                  title: const Text('Alternate Activation'),
                                  content: Column(
                                    mainAxisSize: MainAxisSize.min,
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        "If SMS-based activation is not working, you can try SMS-less activation. This requires granting OpenBubbles special permissions so it can authenticate directly with your SIM.",
                                        style: Get.textTheme.bodyMedium,
                                      ),
                                      Padding(
                                        padding: const EdgeInsets.only(top: 10),
                                        child: Text(
                                          "Method 1 (Easiest)",
                                          style: Get.textTheme.labelLarge,
                                        ),
                                      ),
                                      RichText(
                                        text: TextSpan(style: Get.textTheme.bodyMedium, children: [
                                          TextSpan(
                                            text: "Download Shizuku from the Play Store",
                                            style: const TextStyle(
                                              color: Colors.blue,
                                            ),
                                            recognizer: TapGestureRecognizer()
                                              ..onTap = () {
                                                launchUrl(
                                                    Uri.parse(
                                                        "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api&hl=en_US"),
                                                    mode: LaunchMode.externalApplication);
                                              },
                                          ),
                                          const TextSpan(
                                              text:
                                                  ". Look for 'Start via Wireless Debugging', and follow the step-by-step guide. Then, click the 'Use Shizuku' button."),
                                        ]),
                                      ),
                                      Padding(
                                        padding: const EdgeInsets.only(top: 10),
                                        child: Text(
                                          "Method 2 (ADB; requires computer)",
                                          style: Get.textTheme.labelLarge,
                                        ),
                                      ),
                                      Text(
                                        "Connect your Phone to your computer. Run this command in the ADB shell:",
                                        style: Get.textTheme.bodyMedium,
                                      ),
                                      GestureDetector(
                                        child: RichText(
                                          text: TextSpan(style: context.theme.textTheme.bodySmall, children: [
                                            const TextSpan(
                                                text:
                                                    "appops set --uid com.openbubbles.messaging USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER allow (click to copy)"),
                                          ]),
                                        ),
                                        onTap: () {
                                          Clipboard.setData(const ClipboardData(
                                              text:
                                                  "appops set --uid com.openbubbles.messaging USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER allow"));
                                          if (!Platform.isAndroid ||
                                              (FilesystemSvc.androidInfo?.version.sdkInt ?? 0) < 33) {
                                            showSnackbar("Copied", "Command copied to clipboard!");
                                          }
                                        },
                                      ),
                                    ],
                                  ),
                                  actions: <Widget>[
                                    TextButton(
                                        onPressed: () => Get.back(),
                                        child: Text("Done",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary))),
                                    TextButton(
                                        onPressed: () async {
                                          try {
                                            await MethodChannelSvc.invokeMethod("shizuku-grant-permission");
                                          } catch (e) {
                                            if (e is PlatformException) {
                                              showSnackbar("Error", e.code);
                                            }
                                            rethrow;
                                          }
                                          Get.back();
                                          failedSms.value = false;
                                        },
                                        child: Text("Use Shizuku",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary))),
                                  ],
                                ),
                              );
                            },
                            child: Text("Try Alternate Activation Method",
                                style: context.theme.textTheme.bodyMedium!.apply(color: HexColor('2772C3')))),
                      if (!failed.value)
                        ...MethodChannelSvc.simInfo.map((sim) => SettingsSwitch(
                              padding: false,
                              onChanged: (bool val) async {
                                if (controller.phoneValidating.value) return;
                                int subscription = sim["subscription"];
                                if (val) {
                                  await subscribeSubscription(subscription);
                                } else {
                                  controller.currentPhoneUsers.remove(subscription);
                                  setState(() {});
                                }
                              },
                              initialVal: controller.currentPhoneUsers.containsKey(sim["subscription"]),
                              title: sim["carrier"],
                              subtitle: "Use this phone number with OpenBubbles and your other Apple devices",
                              backgroundColor: tileColor,
                              isThreeLine: true,
                            )),
                      const SizedBox(height: 20),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Container(
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(25),
                              gradient: LinearGradient(
                                begin: AlignmentDirectional.topStart,
                                colors: [HexColor('2772C3'), HexColor('5CA7F8').darkenPercent(5)],
                              ),
                            ),
                            height: 40,
                            padding: const EdgeInsets.all(2),
                            child: ElevatedButton(
                              style: ButtonStyle(
                                shape: MaterialStateProperty.all<RoundedRectangleBorder>(
                                  RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(20.0),
                                  ),
                                ),
                                backgroundColor: MaterialStateProperty.all(context.theme.colorScheme.background),
                                shadowColor: MaterialStateProperty.all(context.theme.colorScheme.background),
                                maximumSize: MaterialStateProperty.all(const Size(200, 36)),
                                minimumSize: MaterialStateProperty.all(const Size(30, 30)),
                              ),
                              onPressed: () async {
                                goBack();
                              },
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(Icons.arrow_back, color: context.theme.colorScheme.onBackground, size: 20),
                                  const SizedBox(width: 10),
                                  Text("Back",
                                      style: context.theme.textTheme.bodyLarge!
                                          .apply(fontSizeFactor: 1.1, color: context.theme.colorScheme.onBackground)),
                                ],
                              ),
                            ),
                          ),
                          Obx(() {
                            final showContinue =
                                failed.value ? tempRegister.value : controller.currentPhoneUsers.isNotEmpty;
                            return Container(
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(25),
                                gradient: LinearGradient(
                                  begin: AlignmentDirectional.topStart,
                                  colors: controller.phoneValidating.value
                                      ? [HexColor('777777'), HexColor('777777')]
                                      : [HexColor('2772C3'), HexColor('5CA7F8').darkenPercent(5)],
                                ),
                              ),
                              height: 40,
                              padding: const EdgeInsets.all(2),
                              child: ElevatedButton(
                                  style: ButtonStyle(
                                    shape: MaterialStateProperty.all<RoundedRectangleBorder>(
                                      RoundedRectangleBorder(
                                        borderRadius: BorderRadius.circular(20.0),
                                      ),
                                    ),
                                    backgroundColor: MaterialStateProperty.all(
                                        showContinue || controller.phoneValidating.value
                                            ? Colors.transparent
                                            : context.theme.colorScheme.background),
                                    shadowColor: MaterialStateProperty.all(
                                        showContinue || controller.phoneValidating.value
                                            ? Colors.transparent
                                            : context.theme.colorScheme.background),
                                    maximumSize: MaterialStateProperty.all(const Size(200, 36)),
                                    minimumSize: MaterialStateProperty.all(const Size(30, 30)),
                                  ),
                                  onPressed: controller.phoneValidating.value
                                      ? null
                                      : () async {
                                          if (failed.value && tempRegister.value) {
                                            try {
                                              await Permission.phone.request();
                                            } catch (e) {
                                              showSnackbar("Failed", "Enable phone permissions in settings");
                                              rethrow;
                                            }
                                            subscribe();
                                            var info = await MethodChannelSvc.simInfo.asFuture;
                                            if (info.length == 1) {
                                              await subscribeSubscription(info[0]["subscription"]);
                                              // stay here if it failed
                                              if (controller.currentPhoneUsers.isEmpty) {
                                                return;
                                              }
                                            } else {
                                              // stay here for dual sim
                                              return;
                                            }
                                          }
                                          controller.pageController.nextPage(
                                            duration: const Duration(milliseconds: 300),
                                            curve: Curves.easeInOut,
                                          );
                                        },
                                  child: Stack(
                                    alignment: Alignment.center,
                                    children: [
                                      Opacity(
                                        opacity: controller.phoneValidating.value ? 0 : 1,
                                        child: Row(
                                          mainAxisSize: MainAxisSize.min,
                                          children: [
                                            Text(showContinue ? "Continue" : "Skip",
                                                style: context.theme.textTheme.bodyLarge!.apply(
                                                    fontSizeFactor: 1.1,
                                                    color: showContinue
                                                        ? Colors.white
                                                        : context.theme.colorScheme.onBackground)),
                                            const SizedBox(width: 10),
                                            Icon(Icons.arrow_forward,
                                                color: showContinue
                                                    ? Colors.white
                                                    : context.theme.colorScheme.onBackground,
                                                size: 20),
                                          ],
                                        ),
                                      ),
                                      if (controller.phoneValidating.value) buildProgressIndicator(context),
                                    ],
                                  )),
                            );
                          }),
                        ],
                      ),
                    ],
                  )),
            ),
          ),
        ],
      ),
    );
  }

  void goBack() {
    FocusManager.instance.primaryFocus?.unfocus();
    controller.pageController.previousPage(
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeInOut,
    );
  }
}
