import 'dart:io' as io;

import 'package:bluebubbles/services/network/http_overrides.dart';
import 'package:bluebubbles/services/network/user_certificates.dart';
import 'package:web_socket/io_web_socket.dart' show IOWebSocket;
import 'package:web_socket/web_socket.dart' as ws;

/// Custom [WebSocketConnector] for socket_io_client that trusts user-added and
/// self-signed certificates (Android) when establishing the WebSocket
/// connection.
///
/// socket_io_client 3.x removed the old `HttpClientAdapter` hook in favour of
/// a `setWebSocketConnector` callback (see [OptionBuilder.setWebSocketConnector]).
/// This mirrors the package's default native connector but injects an
/// [io.HttpClient] built from our custom [SecurityContext] plus a permissive
/// bad-certificate callback.
Future<ws.WebSocket> websocketConnector(
  Uri uri, {
  Iterable<String>? protocols,
  Map<String, String>? headers,
}) async {
  // Get context with system certs + user certs (Android only)
  final context = await UserCertificates().getContext();
  final client = io.HttpClient(context: context);

  // Add custom certificate validation for self-signed certs and hostname mismatches
  client.badCertificateCallback = shouldAcceptCertificate;

  // The socket is handed off to IOWebSocket and closed via Transport.doClose.
  // ignore: close_sinks
  final io.WebSocket socket;
  try {
    socket = await io.WebSocket.connect(
      uri.toString(),
      protocols: protocols,
      headers: headers,
      customClient: client,
    );
  } on io.WebSocketException catch (e) {
    throw ws.WebSocketException(e.message);
  }

  return IOWebSocket.fromWebSocket(socket);
}
