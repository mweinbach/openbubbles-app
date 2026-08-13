import 'package:bluebubbles/app/layouts/settings/pages/passwords/group_credentials_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/credential_detail_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/password_models.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/passwords_group_panel.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/helpers/ui/theme_helpers.dart';
import 'package:bluebubbles/helpers/ui/ui_helpers.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:universal_io/io.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/src/rust/lib.dart' as lib;
import 'package:get/get.dart';

class PasswordsPanel extends StatefulWidget {
  const PasswordsPanel({super.key});

  @override
  State<PasswordsPanel> createState() => _PasswordsPanelState();
}

class _PasswordsPanelState extends State<PasswordsPanel> with ThemeHelpers {
  lib.ArcPasswordManagerDefaultAnisetteProvider? manager;
  bool _isCheckingClique = true;
  bool _isInClique = false;
  bool _isJoiningClique = false;
  bool _isCreatingGroup = false;
  Map<String, String> _groupNamesById = const {};
  Map<String, api.GroupSummary> _groupsById = const {};
  String? _groupsUserId;
  Map<String, api.ShareInviteContentData> _invitesById = const {};
  final Set<String> _pendingInviteActions = <String>{};

  @override
  void initState() {
    super.initState();
    _refreshCliqueStatus();
  }

  Future<void> _refreshCliqueStatus() async {
    final keychain = PushSvc.state?.icloudServices?.keychain;
    if (keychain == null) {
      if (!mounted) return;
      setState(() {
        _isInClique = false;
        _isCheckingClique = false;
      });
      return;
    }

    await PushSvc.initFuture;
    final inClique = PushSvc.cachedInClique;
    if (inClique && manager == null) {
      manager = PushSvc.state!.icloudServices!.passwords!;
    }
    if (inClique && manager != null) {
      await _loadCredentialCaches();
      await _loadGroups();
    }
    PushSvc.checkClique().then((inclique) {
      if (!inclique) return;
      api.syncWifiPasswords(manager: manager!, userApprove: true);
    });
    if (!mounted) return;
    setState(() {
      _isInClique = inClique;
      _isCheckingClique = false;
    });
  }

  Future<void> _loadGroups() async {
    if (manager == null) return;
    try {
      final groups = await api.getGroups(passwords: manager!);
      print("groups ${groups.$2.length} ${groups.$3.length}");
      if (!mounted) return;
      setState(() {
        _groupsUserId = groups.$1;
        _groupNamesById = {
          for (final entry in groups.$2.entries)
            entry.key: entry.value.displayName.trim().isEmpty ? "(unknown group)" : entry.value.displayName,
        };
        _groupsById = groups.$2;
        _invitesById = groups.$3;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _groupsUserId = null;
        _groupNamesById = const {};
        _groupsById = const {};
        _invitesById = const {};
      });
    }
  }

  Future<void> _loadCredentialCaches() async {
    if (manager == null) return;
    try {
      await api.getPasswords(passwords: manager!);
    } catch (_) {}
    try {
      await api.getPasskeys(passwords: manager!);
    } catch (_) {}
    try {
      await api.getWifiPasswords(passwords: manager!);
    } catch (_) {}
    try {
      await api.getPasswordsMeta(passwords: manager!);
    } catch (_) {}
  }

  Future<void> _handleInviteAction(
    String inviteId, {
    required bool accept,
  }) async {
    if (manager == null || _pendingInviteActions.contains(inviteId)) return;
    setState(() => _pendingInviteActions.add(inviteId));
    try {
      if (accept) {
        await api.acceptInvite(passwords: manager!, inviteId: inviteId);
      } else {
        await api.declineInvite(passwords: manager!, inviteId: inviteId);
      }
      await api.syncPasswords(passwords: manager!, conn: PushSvc.state!.conn);
      await _loadGroups();
    } catch (error) {
      showSnackbar(
        "Error",
        "Unable to ${accept ? "accept" : "decline"} invite. $error",
      );
    } finally {
      if (mounted) {
        setState(() => _pendingInviteActions.remove(inviteId));
      }
    }
  }

  Future<void> _handlePullToRefresh() async {
    if (manager == null) return;
    try {
      await api.syncPasswords(
        passwords: manager!,
        conn: PushSvc.state!.conn,
      );
      await _loadCredentialCaches();
      await _loadGroups();
    } catch (error) {
      showSnackbar("Error", "Unable to refresh passwords. $error");
    }
  }

  Future<void> _joinClique() async {
    if (_isJoiningClique) return;
    final keychain = PushSvc.state?.icloudServices?.keychain;
    if (keychain == null) {
      showSnackbar(
        "Relog required!",
        "Relog required to use Passwords! Relog in Settings -> Reconfigure",
      );
      return;
    }

    setState(() => _isJoiningClique = true);
    try {
      if (await PushSvc.joinClique()) {
        await api.syncPasswords(passwords: PushSvc.state!.icloudServices!.passwords!, conn: PushSvc.state!.conn);
      }
      await _refreshCliqueStatus();
    } finally {
      if (mounted) {
        setState(() => _isJoiningClique = false);
      }
    }
  }

  Future<void> _promptCreateGroup() async {
    if (manager == null || _isCreatingGroup) return;
    final controller = TextEditingController();
    await showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("Create Group"),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: "Group Name",
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text("Cancel"),
          ),
          TextButton(
            onPressed: () async {
              final name = controller.text.trim();
              if (name.isEmpty) {
                showSnackbar("Error", "Please enter a group name.");
                return;
              }
              Navigator.of(context).pop();
              setState(() => _isCreatingGroup = true);
              try {
                await api.createGroup(passwords: manager!, name: name);
                await _loadGroups();
                showSnackbar("Created", "Group created.");
              } catch (error) {
                showSnackbar("Error", "Unable to create group. $error");
              } finally {
                if (mounted) {
                  setState(() => _isCreatingGroup = false);
                }
              }
            },
            child: const Text("Create"),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isAndroid = !kIsWeb && Platform.isAndroid;

    if (_isCheckingClique) {
      return SettingsScaffold(
        title: "Passwords",
        initialHeader: null,
        iosSubtitle: iosSubtitle,
        materialSubtitle: materialSubtitle,
        headerColor: headerColor,
        tileColor: tileColor,
        bodySlivers: [
          const SliverFillRemaining(
            hasScrollBody: false,
            child: Center(child: CircularProgressIndicator()),
          ),
        ],
      );
    }

    if (!_isInClique) {
      return SettingsScaffold(
        title: "Passwords",
        initialHeader: null,
        iosSubtitle: iosSubtitle,
        materialSubtitle: materialSubtitle,
        headerColor: headerColor,
        tileColor: tileColor,
        bodySlivers: [
          SliverList(
            delegate: SliverChildListDelegate(
              [
                SettingsHeader(
                  iosSubtitle: iosSubtitle,
                  materialSubtitle: materialSubtitle,
                  text: "iCloud Keychain",
                ),
                SettingsSection(
                  backgroundColor: tileColor,
                  children: [
                    SettingsTile(
                      backgroundColor: tileColor,
                      title: "Join iCloud Keychain Clique",
                      subtitle:
                          "Join this device to iCloud Keychain to view passwords, passkeys, codes, and Wi-Fi credentials.",
                      onTap: _isJoiningClique ? null : _joinClique,
                      trailing: _isJoiningClique
                          ? SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: context.theme.colorScheme.primary,
                              ),
                            )
                          : const NextButton(),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      );
    }

    return SettingsScaffold(
      title: "Passwords",
      initialHeader: null,
      iosSubtitle: iosSubtitle,
      materialSubtitle: materialSubtitle,
      headerColor: headerColor,
      tileColor: tileColor,
      actions: [
        IconButton(
          tooltip: "Search",
          icon: Icon(SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.search : Icons.search),
          onPressed: _openSearch,
        ),
      ],
      bodySlivers: [
        CupertinoSliverRefreshControl(
          onRefresh: _handlePullToRefresh,
        ),
        SliverList(
          delegate: SliverChildListDelegate(
            [
              if (isAndroid)
                SettingsSection(
                  backgroundColor: tileColor,
                  children: [
                    SettingsTile(
                      backgroundColor: tileColor,
                      title: "Configure device password and passkey providers",
                      subtitle:
                          "Enable to use iCloud Passkeys and Passwords on this device. To autofill passwords, set to preferred provider.",
                      leading: const SettingsLeadingIcon(
                        iosIcon: CupertinoIcons.settings,
                        materialIcon: Icons.settings,
                        containerColor: Colors.indigo,
                      ),
                      onTap: () async {
                        await MethodChannelSvc.invokeMethod(
                          "open-autofill-provider-settings",
                        );
                      },
                      trailing: const NextButton(),
                    ),
                  ],
                ),
              SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Groups"),
              SettingsSection(
                backgroundColor: tileColor,
                children: [
                  _buildGroupTile(
                    title: "Web Passwords",
                    subtitle: "Saved logins and recovery codes",
                    iosIcon: CupertinoIcons.globe,
                    materialIcon: Icons.public,
                    color: Colors.blueAccent,
                    groupType: PasswordGroupType.web,
                  ),
                  const SettingsDivider(),
                  _buildGroupTile(
                    title: "Passkeys",
                    subtitle: "Cloud synchronized sign-ins",
                    iosIcon: Icons.key,
                    materialIcon: Icons.key,
                    color: Colors.deepPurple,
                    groupType: PasswordGroupType.passkeys,
                  ),
                  const SettingsDivider(),
                  _buildGroupTile(
                    title: "Codes",
                    subtitle: "Credentials with one-time codes",
                    iosIcon: CupertinoIcons.number,
                    materialIcon: Icons.security,
                    color: Colors.teal,
                    groupType: PasswordGroupType.codes,
                  ),
                  const SettingsDivider(),
                  _buildGroupTile(
                    title: "Wi-Fi Codes",
                    subtitle: "Saved networks and passphrases",
                    iosIcon: CupertinoIcons.wifi,
                    materialIcon: Icons.wifi,
                    color: Colors.orangeAccent,
                    groupType: PasswordGroupType.wifi,
                  ),
                ],
              ),
              if (_invitesById.isNotEmpty)
                SettingsHeader(
                  iosSubtitle: iosSubtitle,
                  materialSubtitle: materialSubtitle,
                  text: "Invites",
                ),
              if (_invitesById.isNotEmpty)
                SettingsSection(
                  backgroundColor: tileColor,
                  children: _buildInviteTiles(),
                ),
              SettingsHeader(
                iosSubtitle: iosSubtitle,
                materialSubtitle: materialSubtitle,
                text: "Available Groups",
              ),
              SettingsSection(
                backgroundColor: tileColor,
                children: [
                  SettingsTile(
                    backgroundColor: tileColor,
                    title: "Create Group",
                    onTap: _isCreatingGroup ? null : _promptCreateGroup,
                    trailing: _isCreatingGroup
                        ? SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: context.theme.colorScheme.primary,
                            ),
                          )
                        : Icon(
                            SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.add : Icons.add,
                            color: context.theme.colorScheme.primary,
                          ),
                  ),
                  ..._buildGroupsListTiles()
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildGroupTile({
    required String title,
    required String subtitle,
    required IconData iosIcon,
    required IconData materialIcon,
    required Color color,
    required PasswordGroupType groupType,
  }) {
    return SettingsTile(
      backgroundColor: tileColor,
      title: title,
      subtitle: subtitle,
      onTap: () {
        if (manager == null) return;
        NavigationSvc.pushSettings(
          context,
          PasswordsGroupPanel(
            title: title,
            groupType: groupType,
            provider: manager!,
            groupNamesById: _groupNamesById,
            groupUserId: _groupsUserId,
          ),
        );
      },
      leading: SettingsLeadingIcon(
        iosIcon: iosIcon,
        materialIcon: materialIcon,
        containerColor: color,
      ),
      trailing: const NextButton(),
    );
  }

  List<Widget> _buildGroupsListTiles() {
    if (_groupsById.isEmpty) {
      return [];
    }

    final entries = _groupsById.entries.toList()
      ..sort((a, b) => a.value.displayName.toLowerCase().compareTo(b.value.displayName.toLowerCase()));

    final tiles = <Widget>[];
    for (var i = 0; i < entries.length; i++) {
      final groupId = entries[i].key;
      final summary = entries[i].value;
      final groupName = summary.displayName.trim().isEmpty ? "(unknown group)" : summary.displayName;
      tiles.add(
        SettingsTile(
          backgroundColor: tileColor,
          title: groupName,
          onTap: () async {
            if (manager == null) return;
            final result = await NavigationSvc.pushSettings(
              context,
              GroupCredentialsPanel(
                groupId: groupId,
                groupName: groupName,
                provider: manager!,
                groupNamesById: _groupNamesById,
                groupUserId: _groupsUserId,
                initialSummary: summary,
              ),
            );
            if (result == true) {
              await _loadGroups();
            }
          },
          trailing: const NextButton(),
        ),
      );
      if (i != entries.length - 1) {
        tiles.add(const SettingsDivider());
      }
    }
    return tiles;
  }

  List<Widget> _buildInviteTiles() {
    final entries = _invitesById.entries.toList()..sort((a, b) => a.key.compareTo(b.key));
    final tiles = <Widget>[];
    for (var i = 0; i < entries.length; i++) {
      final inviteId = entries[i].key;
      final invite = entries[i].value;
      final pending = _pendingInviteActions.contains(inviteId);
      final groupName = invite.groupName.trim().isEmpty ? "(unknown group)" : invite.groupName;
      tiles.add(
        SettingsTile(
          backgroundColor: tileColor,
          title: groupName,
          subtitle: _displayHandle(invite.inviteeHandle),
          trailing: pending
              ? SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: context.theme.colorScheme.primary,
                  ),
                )
              : Wrap(
                  spacing: 4,
                  children: [
                    TextButton(
                      onPressed: () => _handleInviteAction(
                        inviteId,
                        accept: false,
                      ),
                      child: const Text("Decline"),
                    ),
                    TextButton(
                      onPressed: () => _handleInviteAction(
                        inviteId,
                        accept: true,
                      ),
                      child: const Text("Accept"),
                    ),
                  ],
                ),
        ),
      );
      if (i != entries.length - 1) {
        tiles.add(const SettingsDivider());
      }
    }
    return tiles;
  }

  String _displayHandle(String handle) {
    try {
      return RustPushBBUtils.rustHandleToBB(handle).displayName;
    } catch (_) {
      return "Contact";
    }
  }

  Future<void> _openSearch() async {
    if (manager == null) return;
    final items = await _loadSearchItems();
    if (!mounted) return;
    final selected = await showSearch<_PasswordSearchItem?>(
      context: context,
      delegate: _PasswordSearchDelegate(items: items),
    );
    if (selected == null || !mounted) return;
    final result = await NavigationSvc.pushSettings(
      context,
      CredentialDetailPanel(
        credential: selected.entry,
        provider: manager!,
        groupNamesById: _groupNamesById,
        groupUserId: _groupsUserId,
      ),
    );
    if (result == true) {
      await api.syncPasswords(passwords: manager!, conn: PushSvc.state!.conn);
      await _loadGroups();
    }
  }

  Future<List<_PasswordSearchItem>> _loadSearchItems() async {
    if (manager == null) return const [];
    final passwords = await api.getPasswords(passwords: manager!);
    final wifi = await api.getWifiPasswords(passwords: manager!);
    final metas = await api.getPasswordsMeta(passwords: manager!);
    final metasBySiteUser = _indexMetasBySiteAndUser(metas);
    final items = <_PasswordSearchItem>[];

    for (final entry in passwords.entries) {
      final passwordGroup = entry.value.$1;
      final password = entry.value.$2;
      final match = _takeMatchingMeta(
        metasBySiteUser,
        site: password.srvr,
        user: password.acct,
        group: passwordGroup,
      );
      final meta = match?.$2;
      final data = meta?.getPasswordData();
      final website = (meta?.srvr ?? password.srvr).trim();
      final groupName = _resolveGroupName(passwordGroup);
      final credential = CredentialEntry(
        id: entry.key,
        group: passwordGroup,
        passwordMetaId: match?.$1,
        groupType: PasswordGroupType.web,
        item: buildPasswordCredential(
          meta: meta,
          password: password,
          data: data,
          group: groupName,
          groupType: PasswordGroupType.web,
        ),
        passwordMeta: meta,
        passwordRaw: password,
      );
      items.add(
        _PasswordSearchItem(
          entry: credential,
          queryText: website.toLowerCase(),
          subtitle: website.isNotEmpty ? "Website: $website" : "Website",
        ),
      );
    }

    for (final entry in wifi.entries) {
      final wifiGroup = entry.value.$1;
      final wifiEntry = entry.value.$2;
      final ssid = wifiEntry.acct.trim();
      final groupName = _resolveGroupName(wifiGroup);
      final credential = CredentialEntry(
        id: entry.key,
        group: wifiGroup,
        groupType: PasswordGroupType.wifi,
        item: buildWifiCredential(
          wifi: wifiEntry,
          group: groupName,
        ),
        wifiPassword: wifiEntry,
      );
      items.add(
        _PasswordSearchItem(
          entry: credential,
          queryText: ssid.toLowerCase(),
          subtitle: ssid.isNotEmpty ? "SSID: $ssid" : "SSID",
        ),
      );
    }

    return items;
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

  String _resolveGroupName(String? groupId) {
    if (groupId == null || groupId.trim().isEmpty) {
      return "(unknown group)";
    }
    return _groupNamesById[groupId] ?? "(unknown group)";
  }
}

class _PasswordSearchItem {
  final CredentialEntry entry;
  final String queryText;
  final String subtitle;

  const _PasswordSearchItem({
    required this.entry,
    required this.queryText,
    required this.subtitle,
  });
}

class _PasswordSearchDelegate extends SearchDelegate<_PasswordSearchItem?> {
  final List<_PasswordSearchItem> items;

  _PasswordSearchDelegate({required this.items});

  @override
  String get searchFieldLabel => "Search website or SSID";

  @override
  List<Widget>? buildActions(BuildContext context) {
    return [
      if (query.isNotEmpty)
        IconButton(
          onPressed: () => query = "",
          icon: const Icon(Icons.clear),
        ),
    ];
  }

  @override
  Widget? buildLeading(BuildContext context) {
    return IconButton(
      onPressed: () => close(context, null),
      icon: const Icon(Icons.arrow_back),
    );
  }

  @override
  Widget buildResults(BuildContext context) {
    return _buildList(context);
  }

  @override
  Widget buildSuggestions(BuildContext context) {
    return _buildList(context);
  }

  Widget _buildList(BuildContext context) {
    final normalizedQuery = query.trim().toLowerCase();
    final dividerColor = Theme.of(context).dividerColor.withOpacity(0.35);
    final filtered = normalizedQuery.isEmpty
        ? items
        : items.where((item) => item.queryText.contains(normalizedQuery)).toList(growable: false);
    if (filtered.isEmpty) {
      return const Center(
        child: Text("No matching passwords or Wi-Fi networks."),
      );
    }
    return ListView.separated(
      itemCount: filtered.length,
      separatorBuilder: (_, __) => Divider(
        height: 1,
        color: dividerColor,
      ),
      itemBuilder: (context, index) {
        final item = filtered[index];
        final style = styleForPasswordGroup(item.entry.groupType);
        return ListTile(
          onTap: () => close(context, item),
          leading: Icon(style.icon, color: style.color),
          title: Text(item.entry.item.title),
          subtitle: Text(item.subtitle),
          trailing: const Icon(Icons.chevron_right),
        );
      },
    );
  }
}
