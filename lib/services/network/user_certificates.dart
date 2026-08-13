import 'dart:convert';
import 'dart:io';
import 'package:flutter_user_certificates_android/flutter_user_certificates_android.dart';

class UserCertificates {
  // Cached so that SecurityContext.defaultContext is only mutated once.
  // Calling setTrustedCertificatesBytes() twice with the same cert throws a
  // TlsException, which would abort every socket reconnect attempt.
  static SecurityContext? _cache;
  static bool _initialized = false;

  Future<SecurityContext?> getContext() async {
    if (_initialized) return _cache;
    _initialized = true;

    // Non-Android platforms use the system cert store as-is.
    if (!Platform.isAndroid) {
      return null;
    }

    final certs = await FlutterUserCertificatesAndroid().getUserCertificates();

    // No user-installed certs — nothing to add.
    if (certs == null || certs.isEmpty) {
      return null;
    }

    // Mutate defaultContext once and cache it.
    final ctx = SecurityContext.defaultContext;
    for (final c in certs.entries) {
      ctx.setTrustedCertificatesBytes(utf8.encode(c.value.toPEM()));
    }

    _cache = ctx;
    return _cache;
  }
}
