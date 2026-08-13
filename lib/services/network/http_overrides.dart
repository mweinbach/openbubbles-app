import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/network/user_certificates.dart';
import 'package:universal_io/io.dart';

/// Shared certificate validation logic for both HTTP and WebSocket connections
/// Returns true if the certificate should be accepted (override bad cert), false otherwise
bool shouldAcceptCertificate(X509Certificate cert, String host, int port) {
  String serverUrl = sanitizeServerAddress() ?? "";

  // Extract hostname from the server URL
  Uri? uri = Uri.tryParse(serverUrl);
  if (uri == null) {
    return false;
  }

  String serverHost = uri.host;
  bool isValidCert = false;

  // Handle wildcard certificates
  if (host.startsWith("*")) {
    // Extract the domain from wildcard (e.g., "*.example.com" -> "example.com")
    String domain = host.substring(2); // Remove "*."
    // Check if server host ends with the domain and has exactly one more subdomain
    // or matches the domain exactly
    isValidCert = serverHost == domain ||
        (serverHost.endsWith('.$domain') &&
            serverHost.substring(0, serverHost.length - domain.length - 1).split('.').length == 1);
  } else {
    // For non-wildcard certificates, the hosts must match exactly
    isValidCert = serverHost == host;
  }

  return isValidCert;
}

class CustomHttpContext extends HttpOverrides {
  SecurityContext? _cachedContext;

  CustomHttpContext() {
    _initContext();
  }

  Future<void> _initContext() async {
    _cachedContext = await UserCertificates().getContext();
  }

  @override
  HttpClient createHttpClient(SecurityContext? context) {
    // Use the cached context with system + user certs
    final client = super.createHttpClient(_cachedContext ?? context);

    // Add custom certificate validation for self-signed certs and hostname mismatches
    client.badCertificateCallback = shouldAcceptCertificate;

    return client;
  }
}
