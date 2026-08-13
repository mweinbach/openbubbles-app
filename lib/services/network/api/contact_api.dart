import 'package:bluebubbles/services/network/api/base_api.dart';
import 'package:dio/dio.dart';

class ContactApi {
  final BaseApi _svc;

  ContactApi(this._svc);

  /// Get all iCloud contacts
  Future<Response> fetchAll({bool withAvatars = false, CancelToken? cancelToken}) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "${_svc.apiRoot}/contact",
        queryParameters: _svc.buildQueryParams(withAvatars ? {"extraProperties": "avatar"} : {}),
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }

  /// Get specific iCloud contacts with a list of [addresses], either phone
  /// numbers or emails
  Future<Response> query(List<String> addresses, {CancelToken? cancelToken}) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.post(
        "${_svc.apiRoot}/contact/query",
        queryParameters: _svc.buildQueryParams(),
        data: {"addresses": addresses},
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }

  /// Add a contact to the server
  Future<Response> create(
    List<Map<String, dynamic>> contacts, {
    void Function(int, int)? onSendProgress,
    CancelToken? cancelToken,
  }) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.post(
        "${_svc.apiRoot}/contact",
        queryParameters: _svc.buildQueryParams(),
        data: contacts,
        onSendProgress: onSendProgress,
        options: Options(
          sendTimeout: _svc.dio.options.sendTimeout! * 12,
          receiveTimeout: _svc.dio.options.receiveTimeout! * 12,
          headers: _svc.headers,
        ),
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }
}
