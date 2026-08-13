import 'package:bluebubbles/services/network/api/base_api.dart';
import 'package:dio/dio.dart';

class FirebaseApi {
  final BaseApi _svc;

  FirebaseApi(this._svc);

  Future<Response> getFirebaseProjects(String accessToken) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "https://firebase.googleapis.com/v1beta1/projects",
        queryParameters: {"access_token": accessToken},
      );
      return _svc.returnSuccessOrError(response);
    }, checkOrigin: false);
  }

  Future<Response> getGoogleInfo(String accessToken) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "https://www.googleapis.com/oauth2/v1/userinfo",
        queryParameters: {"access_token": accessToken},
      );
      return _svc.returnSuccessOrError(response);
    }, checkOrigin: false);
  }

  Future<Response> getServerUrlRTDB(String rtdb, String accessToken) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "https://$rtdb.firebaseio.com/config.json",
        queryParameters: {"token": accessToken},
      );
      return _svc.returnSuccessOrError(response);
    }, checkOrigin: false);
  }

  Future<Response> getServerUrlCF(String project, String accessToken) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents/server/config",
        queryParameters: {"access_token": accessToken},
      );
      return _svc.returnSuccessOrError(response);
    }, checkOrigin: false);
  }

  Future<Response> setRestartDateCF(String project) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.patch(
        "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents/server/commands?updateMask.fieldPaths=nextRestart",
        data: {
          "fields": {
            "nextRestart": {"integerValue": DateTime.now().toUtc().millisecondsSinceEpoch},
          },
        },
      );
      return _svc.returnSuccessOrError(response);
    }, checkOrigin: false);
  }
}
