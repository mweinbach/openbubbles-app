import 'package:flutter/foundation.dart';

@immutable
class HandleLookupKey {
  final String address;
  final String service;

  const HandleLookupKey(this.address, this.service);
}
