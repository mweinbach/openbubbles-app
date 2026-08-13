import 'package:bluebubbles/services/network/api/base_api.dart';
import 'package:dio/dio.dart';

class HandleApi {
  final BaseApi _svc;

  HandleApi(this._svc);

  /// Get the number of handles in the server iMessage DB
  Future<Response> handleCount({CancelToken? cancelToken}) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "${_svc.apiRoot}/handle/count",
        queryParameters: _svc.buildQueryParams(),
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }

  /// Query the handles DB. Use [withQuery] to specify what you would like in
  /// the response or how to query the DB.
  ///
  /// [withQuery] options: `"chats"` / `"chat"`, `"chats.participants"` / `"chat.participants"`
  /// (set as one string, comma separated, no spaces)
  Future<Response> handles({
    List<String> withQuery = const [],
    String? address,
    int offset = 0,
    int limit = 100,
    CancelToken? cancelToken,
  }) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.post(
        "${_svc.apiRoot}/handle/query",
        queryParameters: _svc.buildQueryParams(),
        data: {"with": withQuery, "address": address, "offset": offset, "limit": limit},
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }

  /// Get a single handle by [guid]
  Future<Response> handle(String guid, {CancelToken? cancelToken}) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "${_svc.apiRoot}/handle/$guid",
        queryParameters: _svc.buildQueryParams(),
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }

  /// Get a single handle's focus state by [address]
  Future<Response> handleFocusState(String address, {CancelToken? cancelToken}) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "${_svc.apiRoot}/handle/$address/focus",
        queryParameters: _svc.buildQueryParams(),
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }

  /// Get a single handle's iMessage state by [address]
  Future<Response> handleiMessageState(String address, {CancelToken? cancelToken}) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "${_svc.apiRoot}/handle/availability/imessage",
        queryParameters: _svc.buildQueryParams({"address": address}),
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }

  /// Get a single handle's FaceTime state by [address]
  Future<Response> handleFaceTimeState(String address, {CancelToken? cancelToken}) async {
    return _svc.runApiGuarded(() async {
      final response = await _svc.dio.get(
        "${_svc.apiRoot}/handle/availability/facetime",
        queryParameters: _svc.buildQueryParams({"address": address}),
        cancelToken: cancelToken,
      );
      return _svc.returnSuccessOrError(response);
    });
  }
}
