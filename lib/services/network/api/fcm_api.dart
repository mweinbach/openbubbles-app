import 'package:bluebubbles/services/network/api/base_api.dart';
import 'package:dio/dio.dart';

class FcmApi {
  final BaseApi _svc;

  FcmApi(this._svc);

  /// Add a new FCM Device to the server. Must provide [name] and [identifier]
  Future<Response> addDevice(String name, String identifier, {CancelToken? cancelToken}) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.post(
        "${_svc.apiRoot}/fcm/device",
        data: {"name": name, "identifier": identifier},
        queryParameters: _svc.buildQueryParams(),
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }

  /// Get the current FCM data from the server
  Future<Response> getServiceAccount({CancelToken? cancelToken}) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "${_svc.apiRoot}/fcm/client",
        queryParameters: _svc.buildQueryParams(),
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }
}
