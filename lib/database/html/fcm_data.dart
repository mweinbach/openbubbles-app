import 'package:bluebubbles/services/backend/settings/shared_preferences_service.dart';

class FCMData {
  int? id;
  String? projectID;
  String? storageBucket;
  String? apiKey;
  String? firebaseURL;
  String? clientID;
  String? applicationID;

  FCMData({
    this.id,
    this.projectID,
    this.storageBucket,
    this.apiKey,
    this.firebaseURL,
    this.clientID,
    this.applicationID,
  });

  factory FCMData.fromMap(Map<String, dynamic> json) {
    Map<String, dynamic> projectInfo = json["project_info"];
    Map<String, dynamic> client = json["client"][0];
    String clientID = client["oauth_client"][0]["client_id"];
    return FCMData(
      projectID: projectInfo["project_id"],
      storageBucket: projectInfo["storage_bucket"],
      apiKey: client["api_key"][0]["current_key"],
      firebaseURL: projectInfo["firebase_url"],
      clientID: clientID.contains("-") ? clientID.substring(0, clientID.indexOf("-")) : clientID,
      applicationID: client["client_info"]["mobilesdk_app_id"],
    );
  }

  FCMData save() {
    if (isNull) return this;
    Future.delayed(Duration.zero, () async {
      await PrefsSvc.firebase.saveConfig(
        projectID: projectID,
        storageBucket: storageBucket,
        apiKey: apiKey,
        firebaseURL: firebaseURL,
        clientID: clientID,
        applicationID: applicationID,
      );
    });
    return this;
  }

  static void deleteFcmData() async {
    await PrefsSvc.firebase.clearConfig();
  }

  static FCMData getFCM() {
    return FCMData(
      projectID: PrefsSvc.firebase.getProjectID(),
      storageBucket: PrefsSvc.firebase.getStorageBucket(),
      apiKey: PrefsSvc.firebase.getApiKey(),
      firebaseURL: PrefsSvc.firebase.getFirebaseURL(),
      clientID: PrefsSvc.firebase.getClientID(),
      applicationID: PrefsSvc.firebase.getApplicationID(),
    );
  }

  Map<String, dynamic> toMap() => {
        "project_id": projectID,
        "storage_bucket": storageBucket,
        "api_key": apiKey,
        "firebase_url": firebaseURL,
        "client_id": clientID,
        "application_id": applicationID,
      };

  bool get isNull =>
      projectID == null || storageBucket == null || apiKey == null || clientID == null || applicationID == null;
}
