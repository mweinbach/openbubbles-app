import 'dart:convert';
import 'dart:typed_data';

import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/helpers/types/helpers/date_helpers.dart';
import 'package:flutter/material.dart';
import 'package:cbor/simple.dart';

class CredentialField {
  final String label;
  final String value;

  const CredentialField(this.label, this.value);
}

abstract class CredentialItem {
  String get title;
  String get subtitle;
  IconData get icon;
  Color get color;
  List<CredentialField> get fields;
}

class BasicCredentialItem implements CredentialItem {
  @override
  final String title;
  @override
  final String subtitle;
  @override
  final IconData icon;
  @override
  final Color color;
  @override
  final List<CredentialField> fields;

  const BasicCredentialItem({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.color,
    required this.fields,
  });
}

class CredentialEntry {
  final String id;
  final String? group;
  final String? passwordMetaId;
  final PasswordGroupType groupType;
  final CredentialItem item;
  final api.PasswordManagerMeta? passwordMeta;
  final api.PasswordRawEntry? passwordRaw;
  final api.Passkey? passkey;
  final api.WifiPassword? wifiPassword;

  const CredentialEntry({
    required this.id,
    this.group,
    this.passwordMetaId,
    required this.groupType,
    required this.item,
    this.passwordMeta,
    this.passwordRaw,
    this.passkey,
    this.wifiPassword,
  });

  bool get isEditable => groupType == PasswordGroupType.web || groupType == PasswordGroupType.wifi;
}

enum PasswordGroupType {
  web,
  passkeys,
  codes,
  wifi,
}

class PasswordGroupStyle {
  final IconData icon;
  final Color color;

  const PasswordGroupStyle(this.icon, this.color);
}

PasswordGroupStyle styleForPasswordGroup(PasswordGroupType type) {
  switch (type) {
    case PasswordGroupType.web:
      return const PasswordGroupStyle(Icons.public, Colors.blueAccent);
    case PasswordGroupType.passkeys:
      return const PasswordGroupStyle(Icons.key, Colors.deepPurple);
    case PasswordGroupType.codes:
      return const PasswordGroupStyle(Icons.security, Colors.teal);
    case PasswordGroupType.wifi:
      return const PasswordGroupStyle(Icons.wifi, Colors.orangeAccent);
  }
}

CredentialItem buildPasswordCredential({
  api.PasswordManagerMeta? meta,
  api.PasswordRawEntry? password,
  api.PasswordManagerMetaData? data,
  String? group,
  required PasswordGroupType groupType,
}) {
  final style = styleForPasswordGroup(groupType);
  final server = (meta?.srvr ?? password?.srvr ?? "").trim();
  final account = (meta?.acct ?? password?.acct ?? "").trim();
  final showServer = server.contains(".");
  final metadataTitle = _decodeOptionalUtf8(data?.title);
  final title = metadataTitle ?? (showServer ? server : account);
  final subtitle = account;

  return BasicCredentialItem(
    title: title.isNotEmpty ? title : "Saved Password",
    subtitle: subtitle,
    icon: style.icon,
    color: style.color,
    fields: [
      ..._buildGroupField(group),
      ..._buildCommonFields(meta: meta, password: password),
      if (account.isNotEmpty) CredentialField("Account", account),
      if (showServer) CredentialField("Server", server),
      ..._buildPasswordFields(data: data, password: password),
    ],
  );
}

CredentialItem buildPasskeyCredential({
  required api.Passkey passkey,
  String? group,
}) {
  final style = styleForPasswordGroup(PasswordGroupType.passkeys);
  final label = passkey.labl.trim();
  final title = label;
  final subtitle = _extractPasskeyName(passkey);

  return BasicCredentialItem(
    title: title.isNotEmpty ? title : "Passkey",
    subtitle: subtitle,
    icon: style.icon,
    color: style.color,
    fields: [
      ..._buildGroupField(group),
      if (label.isNotEmpty) CredentialField("Site", label),
      if (passkey.agrp.trim().isNotEmpty) CredentialField("Created", _formatPlistDate(passkey.cdat)),
      CredentialField("Modified", _formatPlistDate(passkey.mdat)),
      CredentialField("Account", subtitle),
      // ..._bytesField("Attachment Tag", passkey.atag),
      // ..._bytesField("Key Label", passkey.klbl),
      // ..._bytesField("Data", passkey.data),
    ],
  );
}

CredentialItem buildWifiCredential({
  required api.WifiPassword wifi,
  String? group,
}) {
  final style = styleForPasswordGroup(PasswordGroupType.wifi);
  final ssid = wifi.acct.trim();
  final title = ssid;
  const subtitle = "WPA2 Personal";

  return BasicCredentialItem(
    title: title.isNotEmpty ? title : "Wi-Fi Password",
    subtitle: subtitle,
    icon: style.icon,
    color: style.color,
    fields: [
      ..._buildGroupField(group),
      if (ssid.isNotEmpty) CredentialField("SSID", ssid),
      CredentialField("Created", _formatPlistDate(wifi.cdat)),
      CredentialField("Modified", _formatPlistDate(wifi.mdat)),
      ..._wifiPasswordField(wifi.data),
    ],
  );
}

List<CredentialField> _buildCommonFields({
  api.PasswordManagerMeta? meta,
  api.PasswordRawEntry? password,
}) {
  final created = meta?.cdat ?? password?.cdat ?? 0;
  final modified = meta?.mdat ?? password?.mdat ?? 0;
  return [
    CredentialField("Created", _formatPlistDate(created)),
    CredentialField("Modified", _formatPlistDate(modified)),
  ];
}

List<CredentialField> _buildPasswordFields({
  api.PasswordManagerMetaData? data,
  api.PasswordRawEntry? password,
}) {
  final fields = <CredentialField>[];
  final latestPassword = _currentPassword(data);
  final fallbackPassword = _decodePasswordFromRaw(password);
  final passwordValue = latestPassword ?? fallbackPassword;
  if (passwordValue != null && passwordValue.isNotEmpty) {
    fields.add(CredentialField("Password", passwordValue));
  }
  if (data != null && data.altDomains.isNotEmpty) {
    fields.add(
      CredentialField(
        "Alternate Domains",
        data.altDomains.map((domain) => domain.domain).join(", "),
      ),
    );
  }
  final notes = _decodeOptionalUtf8(data?.notes);
  if (notes != null) {
    fields.add(CredentialField("Notes", notes));
  }
  return fields;
}

String? _currentPassword(api.PasswordManagerMetaData? data) {
  if (data == null || data.history.isEmpty) {
    return null;
  }
  for (final change in data.history.reversed) {
    if (change.password != null) {
      return change.password;
    }
  }
  return null;
}

String? _decodePasswordFromRaw(api.PasswordRawEntry? password) {
  if (password == null || password.data.isEmpty) {
    return null;
  }
  try {
    return utf8.decode(password.data);
  } catch (_) {
    return null;
  }
}

String _formatBytes(Uint8List bytes) {
  if (bytes.isEmpty) {
    return "";
  }
  return base64Encode(bytes);
}

String _formatPlistDate(int date) {
  if (date <= 0) return "";
  final dateTime = DateTime.fromMillisecondsSinceEpoch(date, isUtc: true).toLocal();
  return buildFullDate(dateTime);
}

List<CredentialField> _bytesField(String label, Uint8List bytes) {
  if (bytes.isEmpty) {
    return const [];
  }
  return [CredentialField(label, _formatBytes(bytes))];
}

List<CredentialField> _wifiPasswordField(Uint8List bytes) {
  if (bytes.isEmpty) {
    return const [];
  }
  final password = _formatUtf8(bytes);
  if (password.isEmpty) {
    return const [];
  }
  return [CredentialField("Password", password)];
}

String _formatUtf8(Uint8List bytes) {
  if (bytes.isEmpty) {
    return "";
  }
  try {
    return utf8.decode(bytes);
  } catch (_) {
    return "";
  }
}

List<CredentialField> _buildGroupField(String? group) {
  final value = group?.trim() ?? "";
  if (value.isEmpty || value == "(none)" || value == "(unknown group)") {
    return const [];
  }
  return [CredentialField("Group", value)];
}

String? _decodeOptionalUtf8(Uint8List? bytes) {
  if (bytes == null || bytes.isEmpty) {
    return null;
  }
  try {
    final decoded = utf8.decode(bytes).trim();
    if (decoded.isEmpty) {
      return null;
    }
    return decoded;
  } catch (_) {
    return null;
  }
}

String _extractPasskeyName(api.Passkey passkey) {
  try {
    final tag = cbor.decode(passkey.atag) as Map<dynamic, dynamic>;
    return (tag["name"]?.toString() ?? "").trim();
  } catch (_) {
    return "";
  }
}
