import 'dart:convert';

import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/helpers/types/constants.dart';
import 'package:bluebubbles/main.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:get/get.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:bluebubbles/helpers/types/constants.dart' as constants;
import 'package:get_it/get_it.dart';

class App {
  int appId;
  String store;
  String madridName;
  String madridBundleId;
  AvailableApp? available;

  App({
    required this.appId,
    required this.store,
    required this.madridName,
    required this.madridBundleId,
    this.available,
  });

  factory App.fromMap(Map<String, dynamic> json) => App(
        appId: json["appId"],
        store: json["store"],
        madridName: json["madridName"],
        madridBundleId: json["madridBundleId"],
        available: json["available"] == null ? null : AvailableApp.fromMap(json["available"]),
      );

  Map<String, dynamic> toMap() => {
        "appId": appId,
        "store": store,
        "madridName": madridName,
        "madridBundleId": madridBundleId,
        "available": available?.toMap(),
      };
}

class AvailableApp {
  String name;
  String icon;
  int color;

  AvailableApp({
    required this.name,
    required this.icon,
    required this.color,
  });

  factory AvailableApp.fromMap(Map<String, dynamic> json) => AvailableApp(
        name: json["name"],
        icon: json["icon"],
        color: json["color"],
      );

  Map<String, dynamic> toMap() => {
        "name": name,
        "icon": icon,
        "color": color,
      };
}

// ignore: non_constant_identifier_names
ExtensionService ExtensionSvc = GetIt.I<ExtensionService>();

class ExtensionService extends GetxService {
  List<App> cachedStatus = [];

  Map<String, List<String?>> amkToLatest = {};
  List<String> suppressingSessions = [];

  List<String?> getLatest(String amk) {
    if (amkToLatest.containsKey(amk)) {
      return amkToLatest[amk]!;
    }

    final messages = Database.messages.getAll().where((message) => message.amkSessionId == amk).toList()
      ..sort((a, b) => (b.dateCreated ?? DateTime.fromMillisecondsSinceEpoch(0))
          .compareTo(a.dateCreated ?? DateTime.fromMillisecondsSinceEpoch(0)));

    final results = messages.take(3).map((i) => i.stagingGuid ?? i.guid).toList();
    amkToLatest[amk] = results;
    return results;
  }

  bool isAppAvailable(int app) {
    return ExtensionSvc.cachedStatus.firstWhereOrNull((i) => i.appId == app)?.available != null;
  }

  bool isAppSupported(int app) {
    return cachedStatus.any((a) => a.appId == app);
  }

  String getExtensionBundle(int app) {
    return cachedStatus.firstWhere((a) => a.appId == app).madridBundleId;
  }

  void engageApp(Message data) async {
    var app = data.payloadData!.appData!.first.appId!;
    var myId = cachedStatus.firstWhereOrNull((a) => a.appId == app);
    if (myId == null) return;

    if (myId.available == null) {
      // redirect to store
      launchUrl(Uri.parse(myId.store));
    }

    var payload = data.payloadData!.appData![0];
    var myMap = payload.toNative(null);
    myMap["messageGuid"] = data.guid;
    myMap["userCount"] = data.chat.target!.participants.length + 1;
    await MethodChannelSvc.invokeMethod("extension-template-tap", myMap);
  }

  Future<void> refreshCache() async {
    Logger.debug("Refreshing extension state");
    if (SettingsSvc.settings.developerEnabled.value) {
      for (var item in SettingsSvc.settings.developerMode) {
        await addDevExtension(item);
      }
    }
    var result = await MethodChannelSvc.invokeMethod("extension-status");
    if (result == null) return;
    List<dynamic> parsed = json.decode(result);
    cachedStatus = parsed.map((item) => App.fromMap(item)).toList();
    Logger.debug("Extension state refreshed");
  }

  Future<void> addDevExtension(String package) async {
    await MethodChannelSvc.invokeMethod("dev-extension-handler", {"serviceName": package});
  }

  Future<void> setSuppress(Map<String, dynamic> args) async {
    if (args["suppress"]) {
      if (!suppressingSessions.contains(args["session"])) {
        suppressingSessions.add(args["session"]);
      }
    } else {
      if (suppressingSessions.contains(args["session"])) {
        suppressingSessions.remove(args["session"]);
      }
    }
  }

  Future<void> updateMessage(Map<String, dynamic> args) async {
    var app = cachedStatus.firstWhere((a) => a.appId == args["appId"]);
    var payload = PayloadData(
      type: constants.PayloadType.app,
      appData: [iMessageAppData.fromNative(args, app)],
    );

    var old = Message.findOne(guid: args["messageGuid"])!;

    PlatformFile? file;
    if (args["imageBase64"] != null) {
      var decoded = base64Decode(args["imageBase64"]);
      file = PlatformFile(
        name: "jpeg-image.jpeg",
        size: decoded.length,
        bytes: decoded,
      );
    }

    var message = await BackendSvc.updateMessage(old.chat.target!, old, payload, file, false, null);
    await IncomingMsgHandler.handle(IncomingPayload(
      type: MessageEventType.newMessage,
      source: MessageSource.apiResponse,
      chat: old.chat.target!,
      message: message,
    ));
  }

  Future<void> informUpdate(Message message) async {
    var payload = message.payloadData!.appData![0];
    var myMap = payload.toNative(null);
    myMap["messageGuid"] = message.guid;
    await MethodChannelSvc.invokeMethod("message-update-handler", myMap);
  }

  void addMessage(Map<String, dynamic> args) {
    var app = cachedStatus.firstWhere((a) => a.appId == args["appId"]);

    var payload = PayloadData(
      type: constants.PayloadType.app,
      appData: [iMessageAppData.fromNative(args, app)],
    );

    PlatformFile? file;
    if (args["imageBase64"] != null) {
      var decoded = base64Decode(args["imageBase64"]);
      file = PlatformFile(
        name: "jpeg-image.jpeg",
        size: decoded.length,
        bytes: decoded,
      );
    }

    ChatsSvc.activeChat!.controller!.pickedApp.value = (file, payload);
    ChatsSvc.activeChat!.controller!.triggerTypingIndicator();
    Logger.debug("set");
  }
}
