import 'package:bluebubbles/app/layouts/settings/pages/passwords/credential_detail_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/password_editor_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/password_models.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/passwords_widgets.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/src/rust/lib.dart' as lib;
import 'package:cbor/simple.dart';
import 'dart:convert';

class PasswordsGroupPanel extends StatefulWidget {
  final String title;
  final PasswordGroupType groupType;
  final lib.ArcPasswordManagerDefaultAnisetteProvider provider;
  final Map<String, String> groupNamesById;
  final String? groupUserId;

  const PasswordsGroupPanel({
    super.key,
    required this.title,
    required this.groupType,
    required this.provider,
    required this.groupNamesById,
    required this.groupUserId,
  });

  @override
  State<PasswordsGroupPanel> createState() => _PasswordsGroupPanelState();
}

class _PasswordsGroupPanelState extends State<PasswordsGroupPanel> with ThemeHelpers {
  late Future<List<CredentialEntry>> _credentialsFuture;

  @override
  void initState() {
    super.initState();
    _credentialsFuture = _loadCredentials(widget.groupType);
  }

  Future<List<CredentialEntry>> _loadCredentials(PasswordGroupType type) async {
    switch (type) {
      case PasswordGroupType.web:
        final passwords = await api.getPasswords(passwords: widget.provider);
        final metas = await api.getPasswordsMeta(passwords: widget.provider);
        final metasBySiteUser = _indexMetasBySiteAndUser(metas);
        final items = passwords.entries.map((entry) {
          final password = entry.value.$2;
          final passwordGroup = entry.value.$1;
          final match = _takeMatchingMeta(
            metasBySiteUser,
            site: password.srvr,
            user: password.acct,
            group: passwordGroup,
          );
          final metaId = match?.$1;
          final meta = match?.$2;
          final data = meta?.getPasswordData();
          final displayGroup = _resolveGroupName(passwordGroup);
          return CredentialEntry(
            id: entry.key,
            group: passwordGroup,
            passwordMetaId: metaId,
            groupType: type,
            item: buildPasswordCredential(
              meta: meta,
              password: password,
              data: data,
              group: displayGroup,
              groupType: type,
            ),
            passwordMeta: meta,
            passwordRaw: password,
          );
        }).toList(growable: false);
        items.sort(
          (a, b) => (b.passwordMeta?.mdat ?? b.passwordRaw?.mdat ?? 0).compareTo(
            a.passwordMeta?.mdat ?? a.passwordRaw?.mdat ?? 0,
          ),
        );
        return items;
      case PasswordGroupType.passkeys:
        final entries = await api.getPasskeys(passwords: widget.provider);
        final metas = await api.getPasswordsMeta(passwords: widget.provider);
        final metasBySiteUser = _indexMetasBySiteAndUser(metas);
        final items = entries.entries.map((entry) {
          final passkey = entry.value.$2;
          final passkeyGroup = entry.value.$1;
          final match = _takeMatchingMeta(
            metasBySiteUser,
            site: passkey.labl,
            user: _extractPasskeyKeyIdBase64(passkey),
            group: passkeyGroup,
          );
          final displayGroup = _resolveGroupName(passkeyGroup);
          return CredentialEntry(
            id: entry.key,
            group: passkeyGroup,
            passwordMetaId: match?.$1,
            groupType: type,
            item: buildPasskeyCredential(
              passkey: passkey,
              group: displayGroup,
            ),
            passwordMeta: match?.$2,
            passkey: passkey,
          );
        }).toList(growable: false);
        items.sort(
          (a, b) => (b.passkey?.mdat ?? 0).compareTo(
            a.passkey?.mdat ?? 0,
          ),
        );
        return items;
      case PasswordGroupType.codes:
        final passwords = await api.getPasswords(passwords: widget.provider);
        final metas = await api.getPasswordsMeta(passwords: widget.provider);
        final metasBySiteUser = _indexMetasBySiteAndUser(metas);
        final items = <CredentialEntry>[];
        for (final entry in passwords.entries) {
          final password = entry.value.$2;
          final passwordGroup = entry.value.$1;
          final match = _takeMatchingMeta(
            metasBySiteUser,
            site: password.srvr,
            user: password.acct,
            group: passwordGroup,
          );
          final meta = match?.$2;
          final data = meta?.getPasswordData();
          if (data?.totp == null) {
            continue;
          }
          final displayGroup = _resolveGroupName(passwordGroup);
          items.add(
            CredentialEntry(
              id: entry.key,
              group: passwordGroup,
              passwordMetaId: match?.$1,
              groupType: type,
              item: buildPasswordCredential(
                meta: meta,
                password: password,
                data: data,
                group: displayGroup,
                groupType: type,
              ),
              passwordMeta: meta,
              passwordRaw: password,
            ),
          );
        }
        items.sort(
          (a, b) => (b.passwordMeta?.mdat ?? 0).compareTo(
            a.passwordMeta?.mdat ?? 0,
          ),
        );
        return items;
      case PasswordGroupType.wifi:
        final entries = await api.getWifiPasswords(passwords: widget.provider);
        final items = entries.entries
            .map(
              (entry) => CredentialEntry(
                id: entry.key,
                group: entry.value.$1,
                groupType: type,
                item: buildWifiCredential(
                  wifi: entry.value.$2,
                  group: _resolveGroupName(entry.value.$1),
                ),
                wifiPassword: entry.value.$2,
              ),
            )
            .toList(growable: false);
        items.sort(
          (a, b) => (b.wifiPassword?.mdat ?? 0).compareTo(
            a.wifiPassword?.mdat ?? 0,
          ),
        );
        return items;
    }
  }

  Map<String, List<(String, String?, api.PasswordManagerMeta)>> _indexMetasBySiteAndUser(
    Map<String, (String?, api.PasswordManagerMeta)> metas,
  ) {
    final result = <String, List<(String, String?, api.PasswordManagerMeta)>>{};
    for (final entry in metas.entries) {
      final group = entry.value.$1;
      final meta = entry.value.$2;
      final key = _siteUserKey(site: meta.srvr, user: meta.acct);
      result.putIfAbsent(key, () => <(String, String?, api.PasswordManagerMeta)>[]);
      result[key]!.add((entry.key, group, meta));
    }
    return result;
  }

  (String, api.PasswordManagerMeta)? _takeMatchingMeta(
    Map<String, List<(String, String?, api.PasswordManagerMeta)>> metasBySiteUser, {
    required String site,
    required String user,
    required String? group,
  }) {
    final key = _siteUserKey(site: site, user: user);
    final bucket = metasBySiteUser[key];
    if (bucket == null || bucket.isEmpty) {
      return null;
    }
    for (var i = 0; i < bucket.length; i++) {
      final candidate = bucket[i];
      if (_sameGroup(candidate.$2, group)) {
        bucket.removeAt(i);
        return (candidate.$1, candidate.$3);
      }
    }
    return null;
  }

  String _siteUserKey({required String site, required String user}) {
    final normalizedSite = site.trim().toLowerCase();
    final normalizedUser = user.trim().toLowerCase();
    return "$normalizedSite::$normalizedUser";
  }

  String _resolveGroupName(String? groupId) {
    if (groupId == null || groupId.trim().isEmpty) {
      return "(unknown group)";
    }
    return widget.groupNamesById[groupId] ?? "(unknown group)";
  }

  bool _sameGroup(String? a, String? b) {
    final normalizedA = a?.trim() ?? "";
    final normalizedB = b?.trim() ?? "";
    return normalizedA == normalizedB;
  }

  String _extractPasskeyKeyIdBase64(api.Passkey passkey) {
    try {
      final tag = cbor.decode(passkey.atag) as Map<dynamic, dynamic>;
      final keyId = _extractTagKeyId(tag);
      if (keyId == null || keyId.isEmpty) {
        return "";
      }
      return base64Encode(keyId);
    } catch (_) {
      return "";
    }
  }

  Uint8List? _extractTagKeyId(Map<dynamic, dynamic> tag) {
    final candidates = <dynamic>[
      tag["id"],
      tag["keyId"],
      tag["key_id"],
      tag["credentialId"],
      tag["credential_id"],
      tag[0],
      tag[1],
    ];
    for (final value in candidates) {
      final bytes = _toBytes(value);
      if (bytes != null && bytes.isNotEmpty) {
        return bytes;
      }
    }
    return null;
  }

  Uint8List? _toBytes(dynamic value) {
    if (value is Uint8List) {
      return value;
    }
    if (value is List<int>) {
      return Uint8List.fromList(value);
    }
    if (value is String) {
      try {
        return base64Decode(value);
      } catch (_) {
        return null;
      }
    }
    return null;
  }

  EdgeInsets get _sectionPadding {
    if (SettingsSvc.settings.skin.value == Skins.iOS) {
      return const EdgeInsets.symmetric(horizontal: 20);
    }
    if (SettingsSvc.settings.skin.value == Skins.Samsung) {
      return const EdgeInsets.symmetric(vertical: 5);
    }
    return EdgeInsets.zero;
  }

  double get _sectionRadius {
    if (SettingsSvc.settings.skin.value == Skins.Samsung) {
      return 25;
    }
    if (SettingsSvc.settings.skin.value == Skins.iOS) {
      return 10;
    }
    return 0;
  }

  @override
  Widget build(BuildContext context) {
    return SettingsScaffold(
      title: widget.title,
      initialHeader: "Credentials",
      iosSubtitle: iosSubtitle,
      materialSubtitle: materialSubtitle,
      headerColor: headerColor,
      tileColor: tileColor,
      fab: _buildFab(context),
      bodySlivers: [
        FutureBuilder<List<CredentialEntry>>(
          future: _credentialsFuture,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return _buildStatusSliver(
                child: const Center(child: CircularProgressIndicator()),
              );
            }
            if (snapshot.hasError) {
              return _buildStatusSliver(
                child: Text(
                  "Unable to load credentials. ${snapshot.error}",
                  style: context.theme.textTheme.bodyMedium,
                ),
              );
            }

            final credentials = snapshot.data ?? const <CredentialEntry>[];
            if (credentials.isEmpty) {
              return _buildStatusSliver(
                child: Text(
                  "No credentials saved in this group.",
                  style: context.theme.textTheme.bodyMedium,
                ),
              );
            }

            return SliverPadding(
              padding: _sectionPadding,
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) {
                    final entry = credentials[index];
                    final isFirst = index == 0;
                    final isLast = index == credentials.length - 1;
                    return ClipRRect(
                      borderRadius: BorderRadius.only(
                        topLeft: Radius.circular(isFirst ? _sectionRadius : 0),
                        topRight: Radius.circular(isFirst ? _sectionRadius : 0),
                        bottomLeft: Radius.circular(isLast ? _sectionRadius : 0),
                        bottomRight: Radius.circular(isLast ? _sectionRadius : 0),
                      ),
                      child: Container(
                        color: tileColor,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            _buildCredentialTile(entry),
                            if (!isLast) const SettingsDivider(),
                          ],
                        ),
                      ),
                    );
                  },
                  childCount: credentials.length,
                ),
              ),
            );
          },
        ),
      ],
    );
  }

  Widget _buildCredentialTile(CredentialEntry entry) {
    Future<void> openDetails() async {
      final result = await NavigationSvc.pushSettings(
        context,
        CredentialDetailPanel(
          credential: entry,
          provider: widget.provider,
          groupNamesById: widget.groupNamesById,
          groupUserId: widget.groupUserId,
        ),
      );
      if (result == true && mounted) {
        setState(
          () => _credentialsFuture = _loadCredentials(widget.groupType),
        );
      }
    }

    if (widget.groupType == PasswordGroupType.codes) {
      final totp = entry.passwordMeta?.getPasswordData().totp;
      return TotpCodeListTile(
        title: entry.item.title,
        subtitle: entry.item.subtitle,
        totp: totp,
        tileColor: tileColor,
        onLongPress: openDetails,
      );
    }
    return SettingsTile(
      backgroundColor: tileColor,
      title: entry.item.title,
      subtitle: entry.item.subtitle,
      onTap: openDetails,
      leading: CredentialAvatar(credential: entry.item),
      trailing: const NextButton(),
    );
  }

  SliverList _buildStatusSliver({required Widget child}) {
    return SliverList(
      delegate: SliverChildListDelegate(
        [
          SettingsSection(
            backgroundColor: tileColor,
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: child,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget? _buildFab(BuildContext context) {
    if (widget.groupType != PasswordGroupType.web && widget.groupType != PasswordGroupType.wifi) {
      return null;
    }
    return FloatingActionButton(
      backgroundColor: context.theme.colorScheme.primary,
      child: Icon(
        Icons.add,
        color: context.theme.colorScheme.onPrimary,
        size: 22,
      ),
      onPressed: () async {
        final result = await NavigationSvc.pushSettings(
          context,
          PasswordEditorPanel(
            provider: widget.provider,
            groupType: widget.groupType,
            availableGroups: widget.groupNamesById,
            groupUserId: widget.groupUserId,
          ),
        );
        if (result == true && mounted) {
          setState(
            () => _credentialsFuture = _loadCredentials(widget.groupType),
          );
        }
      },
    );
  }
}

class TotpCodeListTile extends StatefulWidget {
  final String title;
  final String subtitle;
  final api.PasswordManagerTotp? totp;
  final Color tileColor;
  final VoidCallback? onLongPress;

  const TotpCodeListTile({
    super.key,
    required this.title,
    required this.subtitle,
    required this.totp,
    required this.tileColor,
    this.onLongPress,
  });

  @override
  State<TotpCodeListTile> createState() => _TotpCodeListTileState();
}

class _TotpCodeListTileState extends State<TotpCodeListTile> with SingleTickerProviderStateMixin {
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
    final totp = widget.totp;
    if (totp == null) {
      _code = "";
      _expiryMicros = 0;
      return;
    }
    final (code, expiry) = totp.generateOtp();
    _expiryMicros = expiry.toInt() * 1000000;
    _code = code.toString().padLeft(totp.digits, '0');
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
    final totp = widget.totp;
    final nowMicros = DateTime.now().toUtc().microsecondsSinceEpoch;
    final periodMicros = (totp?.period ?? 0) * 1000000;
    final remainingMicros = (_expiryMicros - nowMicros).clamp(0, periodMicros);
    final progress = periodMicros == 0 ? 0.0 : (1.0 - (remainingMicros / periodMicros)).clamp(0.0, 1.0);
    return SettingsTile(
      backgroundColor: widget.tileColor,
      title: widget.title,
      subtitle: _code.isEmpty ? widget.subtitle : _code,
      onTap: _copyCode,
      onLongPress: widget.onLongPress,
      trailing: SizedBox(
        width: 28,
        height: 28,
        child: CircularProgressIndicator(
          value: progress,
          strokeWidth: 4.5,
          strokeCap: StrokeCap.round,
          backgroundColor: context.theme.colorScheme.outline.withOpacity(0.3),
        ),
      ),
    );
  }
}
