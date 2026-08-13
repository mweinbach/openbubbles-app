import 'dart:convert';
import 'dart:typed_data';

import 'package:bluebubbles/app/layouts/settings/pages/passwords/credential_detail_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/group_editor_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/password_editor_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/password_models.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/passwords_widgets.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/helpers/ui/theme_helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/src/rust/lib.dart' as lib;
import 'package:cbor/simple.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class GroupCredentialsPanel extends StatefulWidget {
  final String groupId;
  final String groupName;
  final lib.ArcPasswordManagerDefaultAnisetteProvider provider;
  final Map<String, String> groupNamesById;
  final String? groupUserId;
  final api.GroupSummary initialSummary;

  const GroupCredentialsPanel({
    super.key,
    required this.groupId,
    required this.groupName,
    required this.provider,
    required this.groupNamesById,
    required this.groupUserId,
    required this.initialSummary,
  });

  @override
  State<GroupCredentialsPanel> createState() => _GroupCredentialsPanelState();
}

class _GroupCredentialsPanelState extends State<GroupCredentialsPanel> with ThemeHelpers {
  late Future<List<CredentialEntry>> _credentialsFuture;

  @override
  void initState() {
    super.initState();
    _credentialsFuture = _loadCredentials();
  }

  Future<List<CredentialEntry>> _loadCredentials() async {
    final passwords = await api.getPasswords(passwords: widget.provider);
    final passkeys = await api.getPasskeys(passwords: widget.provider);
    final metas = await api.getPasswordsMeta(passwords: widget.provider);
    final metasBySiteUser = _indexMetasBySiteAndUser(metas);
    final items = <CredentialEntry>[];

    for (final entry in passwords.entries) {
      if (!_sameGroup(entry.value.$1, widget.groupId)) {
        continue;
      }
      final password = entry.value.$2;
      final match = _takeMatchingMeta(
        metasBySiteUser,
        site: password.srvr,
        user: password.acct,
        group: entry.value.$1,
      );
      final meta = match?.$2;
      final data = meta?.getPasswordData();
      items.add(
        CredentialEntry(
          id: entry.key,
          group: entry.value.$1,
          passwordMetaId: match?.$1,
          groupType: PasswordGroupType.web,
          item: buildPasswordCredential(
            meta: meta,
            password: password,
            data: data,
            group: widget.groupName,
            groupType: PasswordGroupType.web,
          ),
          passwordMeta: meta,
          passwordRaw: password,
        ),
      );
    }

    for (final entry in passkeys.entries) {
      if (!_sameGroup(entry.value.$1, widget.groupId)) {
        continue;
      }
      final passkey = entry.value.$2;
      final match = _takeMatchingMeta(
        metasBySiteUser,
        site: passkey.labl,
        user: _extractPasskeyKeyIdBase64(passkey),
        group: entry.value.$1,
      );
      items.add(
        CredentialEntry(
          id: entry.key,
          group: entry.value.$1,
          passwordMetaId: match?.$1,
          groupType: PasswordGroupType.passkeys,
          item: buildPasskeyCredential(
            passkey: passkey,
            group: widget.groupName,
          ),
          passwordMeta: match?.$2,
          passkey: passkey,
        ),
      );
    }

    items.sort((a, b) => _entryModifiedAt(b).compareTo(_entryModifiedAt(a)));
    return items;
  }

  int _entryModifiedAt(CredentialEntry entry) {
    if (entry.passkey != null) {
      return entry.passkey!.mdat;
    }
    return entry.passwordMeta?.mdat ?? entry.passwordRaw?.mdat ?? 0;
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

  @override
  Widget build(BuildContext context) {
    return SettingsScaffold(
      title: widget.groupName,
      initialHeader: null,
      iosSubtitle: iosSubtitle,
      materialSubtitle: materialSubtitle,
      headerColor: headerColor,
      tileColor: tileColor,
      actions: [
        IconButton(
          tooltip: widget.initialSummary.isOwner ? "Edit Group" : "View Participants",
          onPressed: () => _openGroupParticipants(),
          icon: Icon(
            widget.initialSummary.isOwner ? Icons.edit_outlined : Icons.group_outlined,
          ),
        ),
      ],
      fab: FloatingActionButton(
        backgroundColor: context.theme.colorScheme.primary,
        onPressed: () async {
          final result = await NavigationSvc.pushSettings(
            context,
            PasswordEditorPanel(
              provider: widget.provider,
              groupType: PasswordGroupType.web,
              group: widget.groupId,
              availableGroups: widget.groupNamesById,
              groupUserId: widget.groupUserId,
            ),
          );
          if (result == true && mounted) {
            setState(() => _credentialsFuture = _loadCredentials());
          }
        },
        child: Icon(
          Icons.add,
          color: context.theme.colorScheme.onPrimary,
          size: 22,
        ),
      ),
      bodySlivers: [
        SliverList(
          delegate: SliverChildListDelegate(
            [
              SettingsHeader(
                iosSubtitle: iosSubtitle,
                materialSubtitle: materialSubtitle,
                text: "Credentials",
              ),
            ],
          ),
        ),
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

            return SliverList(
              delegate: SliverChildListDelegate(
                [
                  SettingsSection(
                    backgroundColor: tileColor,
                    children: [
                      for (var index = 0; index < credentials.length; index++) ...[
                        _buildCredentialTile(credentials[index]),
                        if (index != credentials.length - 1) const SettingsDivider(),
                      ],
                    ],
                  ),
                ],
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
        setState(() => _credentialsFuture = _loadCredentials());
      }
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

  Future<void> _openGroupParticipants() async {
    final result = await NavigationSvc.pushSettings(
      context,
      GroupEditorPanel(
        provider: widget.provider,
        groupId: widget.groupId,
        initialSummary: widget.initialSummary,
        currentUserId: widget.groupUserId,
      ),
    );
    if (result == true && mounted) {
      Navigator.of(context).pop(true);
    }
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
}
