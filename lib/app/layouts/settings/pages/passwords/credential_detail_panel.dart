import 'dart:async';
import 'dart:convert';

import 'package:bluebubbles/app/layouts/settings/pages/passwords/password_models.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/password_editor_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/passwords_widgets.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:barcode_widget/barcode_widget.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/src/rust/lib.dart' as lib;
import 'package:get/get.dart';

class CredentialDetailPanel extends StatefulWidget {
  final CredentialEntry credential;
  final lib.ArcPasswordManagerDefaultAnisetteProvider provider;
  final Map<String, String> groupNamesById;
  final String? groupUserId;

  const CredentialDetailPanel({
    super.key,
    required this.credential,
    required this.provider,
    required this.groupNamesById,
    required this.groupUserId,
  });

  @override
  State<CredentialDetailPanel> createState() => _CredentialDetailPanelState();
}

class _CredentialDetailPanelState extends State<CredentialDetailPanel> with ThemeHelpers {
  bool get _canEdit => widget.credential.isEditable;
  bool _showPassword = false;
  bool get _isApplePasskey {
    if (widget.credential.groupType != PasswordGroupType.passkeys) {
      return false;
    }

    String? rawSite;
    for (final field in widget.credential.item.fields) {
      if (field.label.toLowerCase() == "site") {
        rawSite = field.value.trim();
        break;
      }
    }

    if (rawSite == null || rawSite.isEmpty) {
      return false;
    }

    final normalized = _normalizeHost(rawSite);
    return normalized == "apple.com" || normalized.endsWith(".apple.com");
  }

  @override
  Widget build(BuildContext context) {
    return SettingsScaffold(
      title: widget.credential.item.title,
      initialHeader: "Credential",
      iosSubtitle: iosSubtitle,
      materialSubtitle: materialSubtitle,
      headerColor: headerColor,
      tileColor: tileColor,
      fab: _canEdit
          ? FloatingActionButton(
              backgroundColor: context.theme.colorScheme.primary,
              child: Icon(
                Icons.edit,
                color: context.theme.colorScheme.onPrimary,
                size: 22,
              ),
              onPressed: () async {
                final result = await NavigationSvc.pushSettings(
                  context,
                  PasswordEditorPanel(
                    provider: widget.provider,
                    groupType: widget.credential.groupType,
                    id: widget.credential.id,
                    group: widget.credential.group,
                    passwordMetaId: widget.credential.passwordMetaId,
                    passwordMeta: widget.credential.passwordMeta,
                    passwordRaw: widget.credential.passwordRaw,
                    wifiPassword: widget.credential.wifiPassword,
                    availableGroups: widget.groupNamesById,
                    groupUserId: widget.groupUserId,
                  ),
                );
                if (result == true && mounted) {
                  Navigator.of(context).pop(true);
                }
              },
            )
          : null,
      bodySlivers: [
        SliverList(
          delegate: SliverChildListDelegate(
            [
              SettingsSection(
                backgroundColor: tileColor,
                children: [
                  SettingsTile(
                    backgroundColor: tileColor,
                    title: widget.credential.item.title,
                    subtitle: widget.credential.item.subtitle,
                    leading: CredentialAvatar(credential: widget.credential.item),
                  ),
                ],
              ),
              SettingsHeader(
                iosSubtitle: iosSubtitle,
                materialSubtitle: materialSubtitle,
                text: "Details",
              ),
              SettingsSection(
                backgroundColor: tileColor,
                children: _buildFieldTiles(),
              ),
            ],
          ),
        ),
      ],
    );
  }

  List<Widget> _buildFieldTiles() {
    final fields = widget.credential.item.fields;
    final tiles = <Widget>[
      for (var i = 0; i < fields.length; i++) ...[
        SettingsTile(
          backgroundColor: tileColor,
          title: fields[i].label,
          subtitle: fields[i].label == "Password" && !_showPassword ? "••••••••" : fields[i].value,
          isThreeLine: fields[i].value.length > 36,
          onTap: fields[i].label == "Password" ? () => setState(() => _showPassword = !_showPassword) : null,
        ),
        if (i != fields.length - 1) const SettingsDivider(),
      ],
    ];

    final totp = widget.credential.passwordMeta?.getPasswordData().totp;
    if (totp != null) {
      if (tiles.isNotEmpty) {
        tiles.add(const SettingsDivider());
      }
      tiles.add(TotpCodeTile(totp: totp, tileColor: tileColor));
    }
    if (widget.credential.wifiPassword != null) {
      if (tiles.isNotEmpty) {
        tiles.add(const SettingsDivider());
      }
      tiles.add(
        SettingsTile(
          backgroundColor: tileColor,
          title: "Show Wi-Fi QR Code",
          onTap: _showWifiQr,
          trailing: Icon(
            iOS ? CupertinoIcons.qrcode : Icons.qr_code,
            color: context.theme.colorScheme.onBackground,
          ),
        ),
      );
    }
    if (widget.credential.groupType == PasswordGroupType.passkeys && !_isApplePasskey) {
      if (tiles.isNotEmpty) {
        tiles.add(const SettingsDivider());
      }
      tiles.add(
        SettingsTile(
          backgroundColor: tileColor,
          title: "Delete Passkey",
          trailing: Icon(
            iOS ? CupertinoIcons.trash : Icons.delete_outline,
            color: context.theme.colorScheme.error,
          ),
          onTap: _confirmDeletePasskey,
        ),
      );
    }
    return tiles;
  }

  String _normalizeHost(String value) {
    final uri = Uri.tryParse(value);
    if (uri != null && uri.host.isNotEmpty) {
      return uri.host.toLowerCase();
    }
    return value.toLowerCase().replaceFirst(RegExp(r"^https?://"), "").split("/").first;
  }

  void _showWifiQr() {
    final wifi = widget.credential.wifiPassword;
    if (wifi == null) return;
    final ssid = wifi.acct.trim();
    final password = _decodeWifiPassword(wifi);
    if (ssid.isEmpty || password.isEmpty) {
      showSnackbar("Error", "Wi-Fi credentials are incomplete.");
      return;
    }
    final data = _buildWifiQrPayload(ssid, password);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("Wi‑Fi QR Code"),
        backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
        content: AspectRatio(
          aspectRatio: 1,
          child: BarcodeWidget(
            barcode: Barcode.qrCode(
              errorCorrectLevel: BarcodeQRCorrectionLevel.medium,
            ),
            data: data,
            backgroundColor: const Color(0),
            color: context.theme.colorScheme.onSurface,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text("Close"),
          ),
        ],
      ),
    );
  }

  String _decodeWifiPassword(api.WifiPassword entry) {
    try {
      return utf8.decode(entry.data);
    } catch (_) {
      return "";
    }
  }

  String _buildWifiQrPayload(String ssid, String password) {
    final escapedSsid =
        ssid.replaceAll(r"\", r"\\").replaceAll(";", r"\;").replaceAll(",", r"\,").replaceAll(":", r"\:");
    final escapedPassword =
        password.replaceAll(r"\", r"\\").replaceAll(";", r"\;").replaceAll(",", r"\,").replaceAll(":", r"\:");
    return "WIFI:T:WPA;S:$escapedSsid;P:$escapedPassword;;";
  }

  void _confirmDeletePasskey() {
    showDialog(
      context: context,
      builder: (context) => areYouSure(
        context,
        title: "Delete Passkey?",
        content: const Text("Are you sure you want to delete this passkey?"),
        onNo: () => Navigator.of(context).pop(),
        onYes: () async {
          Navigator.of(context).pop();
          await _deletePasskey();
        },
      ),
    );
  }

  Future<void> _deletePasskey() async {
    try {
      await Future.wait([
        api.deletePasskey(
          passwords: widget.provider,
          id: widget.credential.id,
          group: widget.credential.group,
        ),
        if (widget.credential.passwordMetaId != null)
          api.deletePasswordMeta(
            passwords: widget.provider,
            id: widget.credential.passwordMetaId!,
            group: widget.credential.group,
          ),
      ]);
      if (mounted) {
        Navigator.of(context).pop(true);
      }
    } catch (_) {
      showSnackbar("Error", "Unable to delete passkey.");
    }
  }
}

class TotpCodeTile extends StatefulWidget {
  final api.PasswordManagerTotp totp;
  final Color tileColor;

  const TotpCodeTile({
    super.key,
    required this.totp,
    required this.tileColor,
  });

  @override
  State<TotpCodeTile> createState() => _TotpCodeTileState();
}

class _TotpCodeTileState extends State<TotpCodeTile> with SingleTickerProviderStateMixin {
  late final Ticker _ticker;
  int _expiryMicros = 0;
  String _code = "";

  @override
  void initState() {
    super.initState();
    _refreshCode();
    _ticker = createTicker((_) => _tick())..start();
  }

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }

  void _refreshCode() {
    final (code, expiry) = widget.totp.generateOtp();
    _expiryMicros = expiry.toInt() * 1000000;
    _code = code.toString().padLeft(widget.totp.digits, '0');
  }

  void _tick() {
    final nowMicros = DateTime.now().toUtc().microsecondsSinceEpoch;
    if (nowMicros >= _expiryMicros) {
      _refreshCode();
    }
    if (mounted) {
      setState(() {});
    }
  }

  Future<void> _copyCode() async {
    if (_code.isEmpty) return;
    await Clipboard.setData(ClipboardData(text: _code));
    showSnackbar("Copied", "TOTP code copied to clipboard.");
  }

  @override
  Widget build(BuildContext context) {
    final nowMicros = DateTime.now().toUtc().microsecondsSinceEpoch;
    final periodMicros = widget.totp.period * 1000000;
    final remainingMicros = (_expiryMicros - nowMicros).clamp(0, periodMicros);
    final progress = widget.totp.period == 0 ? 0.0 : (1.0 - (remainingMicros / periodMicros)).clamp(0.0, 1.0);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SettingsTile(
            backgroundColor: widget.tileColor,
            title: "TOTP Code",
            subtitle: _code,
            trailing: SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                value: progress,
                strokeWidth: 4.5,
                strokeCap: StrokeCap.round,
                backgroundColor: context.theme.colorScheme.outline.withOpacity(0.3),
              ),
            ),
            onTap: _copyCode,
          ),
        ],
      ),
    );
  }
}
