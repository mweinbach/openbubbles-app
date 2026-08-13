import 'package:barcode_widget/barcode_widget.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/utils/share.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart' hide Response;
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:url_launcher/url_launcher.dart';

class DevicePanelController extends StatefulController {
  final RxBool allowSharing = false.obs;
}

class DevicePanel extends CustomStateful<DevicePanelController> {
  DevicePanel() : super(parentController: Get.put(DevicePanelController()));

  @override
  State<StatefulWidget> createState() => _DevicePanelState();
}

class _DevicePanelState extends CustomState<DevicePanel, void, DevicePanelController> {
  api.DeviceInfo? deviceInfo;
  String deviceName = "";
  RxBool isInClique = false.obs;

  Future<T> wrapPromise<T>(Future<T> inner, String text) async {
    showDialog(
        context: context,
        builder: (BuildContext context) {
          return AlertDialog(
            backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
            title: Text(
              text,
              style: context.theme.textTheme.titleLarge,
            ),
            content: Container(
              height: 70,
              child: Center(
                child: CircularProgressIndicator(
                  backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                  valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                ),
              ),
            ),
          );
        });
    T result;
    try {
      result = await inner;
    } catch (e, s) {
      Get.back();
      showSnackbar("Failure! Please try again", e.toString());
      rethrow;
    }
    Get.back();
    return result;
  }

  void choosePassword(bool pin) async {
    var codeController = TextEditingController();
    await showDialog(
        context: context,
        builder: (_) {
          return AlertDialog(
            actions: [
              TextButton(
                child: Text("Use ${pin ? "Password" : "Passcode"}",
                    style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
                onPressed: () {
                  Get.back();
                  choosePassword(!pin);
                },
              ),
              TextButton(
                child: Text("OK",
                    style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
                onPressed: () async {
                  Get.back();
                  await wrapPromise(
                      api.changeEscrowPassword(
                          keychain: PushSvc.state!.icloudServices!.keychain!, devicePassword: codeController.text),
                      "Changing password...");
                  SettingsSvc.settings.keychainDefaultPassword.value = null;
                  await SettingsSvc.settings.saveOneAsync('keychainDefaultPassword');
                },
              ),
            ],
            title: Text("Enter a ${pin ? "passcode" : "password"} to protect your iCloud data.",
                style: context.theme.textTheme.titleLarge),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(
                  height: 20,
                ),
                pin
                    ? StatefulBuilder(
                        builder: (context, state) => Stack(
                              children: [
                                Row(
                                  children: List.generate(6, (index) {
                                    var text = index < codeController.text.length ? codeController.text[index] : "";
                                    return Expanded(
                                        child: Container(
                                            decoration: index == codeController.text.length
                                                ? BoxDecoration(
                                                    border:
                                                        Border.all(color: context.theme.colorScheme.primary, width: 2),
                                                    borderRadius: const BorderRadius.all(Radius.circular(10)),
                                                  )
                                                : BoxDecoration(
                                                    border: Border.all(
                                                      color: context.theme.colorScheme.outline,
                                                    ),
                                                    borderRadius: const BorderRadius.all(Radius.circular(10)),
                                                  ),
                                            margin: const EdgeInsets.all(3),
                                            height: 50,
                                            child: Center(
                                              child: Text(text, style: context.theme.textTheme.titleLarge),
                                            )));
                                  }),
                                ),
                                Opacity(
                                    opacity: 0,
                                    child: TextField(
                                      cursorColor: context.theme.colorScheme.primary,
                                      autocorrect: false,
                                      autofocus: true,
                                      controller: codeController,
                                      textInputAction: TextInputAction.next,
                                      keyboardType: TextInputType.number,
                                      onChanged: (v) {
                                        state(() {});
                                      },
                                    )),
                              ],
                            ))
                    : TextField(
                        controller: codeController,
                        decoration: const InputDecoration(
                          labelText: "Password",
                          border: OutlineInputBorder(),
                        ),
                        autofocus: true,
                      )
              ],
            ),
            backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
          );
        });
  }

  @override
  void initState() {
    super.initState();
    api.getDeviceInfo(config: PushSvc.state!.osConfig).then((value) {
      setState(() {
        deviceInfo = value;
        deviceName = RustPushBBUtils.modelToUser(deviceInfo!.name);
      });
    });
    if (PushSvc.state!.icloudServices != null)
      api.isInClique(keychain: PushSvc.state!.icloudServices!.keychain!).then((clique) => isInClique.value = clique);
  }

  @override
  Widget build(BuildContext context) {
    Widget nextIcon = Obx(() => SettingsSvc.settings.skin.value != Skins.Material
        ? Icon(
            SettingsSvc.settings.skin.value != Skins.Material ? CupertinoIcons.chevron_right : Icons.arrow_forward,
            color: context.theme.colorScheme.outline,
            size: iOS ? 18 : 24,
          )
        : const SizedBox.shrink());

    return Obx(
      () => SettingsScaffold(
        title:
            "${SettingsSvc.settings.deviceIsHosted.value ? "Hosted" : SettingsSvc.settings.macIsMine.value ? 'My' : 'Shared'} Device",
        initialHeader: null,
        iosSubtitle: iosSubtitle,
        materialSubtitle: materialSubtitle,
        tileColor: tileColor,
        headerColor: headerColor,
        bodySlivers: [
          SliverList(
            delegate: SliverChildListDelegate(
              <Widget>[
                SettingsSection(
                  backgroundColor: tileColor,
                  children: [
                    Center(
                        child: Column(
                      children: [
                        Padding(
                          padding: const EdgeInsets.all(25),
                          child: Icon(
                            RustPushBBUtils.getIcon(deviceName),
                            size: 200,
                            color: context.theme.colorScheme.onSurface,
                          ),
                        ),
                        Text(deviceName, style: context.theme.textTheme.titleLarge),
                        const SizedBox(height: 10),
                        Text(SettingsSvc.settings.redactedMode.value ? "Serial Number" : deviceInfo?.serial ?? ""),
                        const SizedBox(height: 10),
                        Text(deviceInfo?.osVersion ?? ""),
                        const SizedBox(height: 25),
                      ],
                    )),
                    if (SettingsSvc.settings.deviceIsHosted.value)
                      SettingsTile(
                          title: "Manage subscription",
                          onTap: () async {
                            launchUrl(
                                Uri.parse(
                                    "https://play.google.com/store/account/subscriptions?sku=monthly_hosted&package=com.openbubbles.messaging"),
                                mode: LaunchMode.externalNonBrowserApplication);
                          },
                          trailing: const NextButton()),
                    if (isInClique.value)
                      SettingsTile(
                          title: "Manage iCloud Keychain Password",
                          subtitle: "Protects your Apple ID, saved passwords, and other data stored in iCloud",
                          onTap: () async {
                            await showDialog(
                                context: context,
                                builder: (_) {
                                  return AlertDialog(
                                    actions: [
                                      TextButton(
                                        child: Text("Change Password",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary)),
                                        onPressed: () async {
                                          Get.back();
                                          choosePassword(true);
                                        },
                                      ),
                                      TextButton(
                                        child: Text("Ok",
                                            style: context.theme.textTheme.bodyLarge!
                                                .copyWith(color: context.theme.colorScheme.primary)),
                                        onPressed: () {
                                          Get.back();
                                        },
                                      ),
                                    ],
                                    title: Text("Keychain Password", style: context.theme.textTheme.titleLarge),
                                    content: Text(
                                        SettingsSvc.settings.keychainDefaultPassword.value != null
                                            ? "This device's default keychain passcode is ${SettingsSvc.settings.keychainDefaultPassword.value}."
                                            : "You have set a custom keychain password.",
                                        style: context.theme.textTheme.bodyLarge),
                                    backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                                  );
                                });
                          },
                          trailing: const NextButton()),
                  ],
                ),
                if (SettingsSvc.settings.macIsMine.value &&
                    deviceInfo?.encodedData != null &&
                    !SettingsSvc.settings.redactedMode.value)
                  SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Share Mac"),
                if (SettingsSvc.settings.macIsMine.value &&
                    deviceInfo?.encodedData != null &&
                    !SettingsSvc.settings.redactedMode.value)
                  SettingsSection(
                    backgroundColor: tileColor,
                    children: [
                      Obx(() => SettingsSwitch(
                            onChanged: (bool val) {
                              controller.allowSharing.value = !val;
                            },
                            initialVal: !controller.allowSharing.value,
                            title: "Prevent sharing",
                            backgroundColor: tileColor,
                            subtitle:
                                "Apple may block devices due to spam or exceeding 20 users. Only share with people you trust.",
                            isThreeLine: true,
                          )),
                      if (deviceInfo != null)
                        Padding(
                            padding: const EdgeInsets.fromLTRB(15, 0, 15, 0),
                            child: AspectRatio(
                              aspectRatio: 1,
                              child: BarcodeWidget.fromBytes(
                                barcode: Barcode.qrCode(
                                  errorCorrectLevel: BarcodeQRCorrectionLevel.medium,
                                ),
                                data: PushSvc.getQrInfo(controller.allowSharing.value, deviceInfo!.encodedData!),
                                backgroundColor: const Color(0),
                                color: context.theme.colorScheme.onSurface,
                              ),
                            )),
                      const SizedBox(height: 10),
                      if (!kIsDesktop)
                        SettingsTile(
                          backgroundColor: tileColor,
                          title: "Share Activation Code",
                          onTap: () async {
                            var code = await PushSvc.uploadCode(controller.allowSharing.value, deviceInfo!);
                            if (code.length > 50) {
                              Share.text(code);
                            } else {
                              Share.text("$rpApiRoot/$code");
                            }
                          },
                          subtitle: controller.allowSharing.value ? null : "Code can only be used once",
                          trailing:
                              Icon(SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.share : Icons.share),
                        ),
                      SettingsTile(
                        backgroundColor: tileColor,
                        title: "Copy Activation Code",
                        onTap: () async {
                          Clipboard.setData(ClipboardData(
                              text: await PushSvc.uploadCode(controller.allowSharing.value, deviceInfo!)));
                        },
                        trailing: Icon(SettingsSvc.settings.skin.value == Skins.iOS
                            ? CupertinoIcons.doc_on_clipboard
                            : Icons.copy),
                        subtitle: controller.allowSharing.value ? null : "Code can only be used once",
                      ),
                    ],
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void saveSettings() {
    SettingsSvc.settings.saveAsync();
  }
}
