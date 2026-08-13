import 'package:bluebubbles/app/layouts/settings/pages/passwords/group_invite_creator.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/src/rust/lib.dart' as lib;
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class GroupEditorPanel extends StatefulWidget {
  final lib.ArcPasswordManagerDefaultAnisetteProvider provider;
  final String groupId;
  final api.GroupSummary initialSummary;
  final String? currentUserId;

  const GroupEditorPanel({
    super.key,
    required this.provider,
    required this.groupId,
    required this.initialSummary,
    this.currentUserId,
  });

  @override
  State<GroupEditorPanel> createState() => _GroupEditorPanelState();
}

class _GroupEditorPanelState extends State<GroupEditorPanel> with ThemeHelpers {
  late final TextEditingController _nameController;
  late api.GroupSummary _summary;
  bool _busy = false;
  bool get _canEditGroup => _summary.isOwner;
  bool get _canDeleteGroup => true;
  bool get _isOwner => _summary.isOwner;

  @override
  void initState() {
    super.initState();
    _summary = widget.initialSummary;
    _nameController = TextEditingController(text: _summary.displayName);
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _refreshGroup() async {
    final groups = await api.getGroups(passwords: widget.provider);
    final updated = groups.$2[widget.groupId];
    if (updated == null) {
      if (mounted) Navigator.of(context).pop(true);
      return;
    }
    if (!mounted) return;
    setState(() {
      _summary = updated;
      _nameController.text = updated.displayName;
    });
  }

  Future<void> _renameGroup() async {
    if (!_canEditGroup) return;
    final newName = _nameController.text.trim();
    if (newName.isEmpty) {
      showSnackbar("Error", "Please enter a group name.");
      return;
    }
    if (newName == _summary.displayName) {
      return;
    }
    setState(() => _busy = true);
    try {
      await api.renameGroup(
        passwords: widget.provider,
        gid: widget.groupId,
        newname: newName,
      );
      await _refreshGroup();
      showSnackbar("Updated", "Group renamed.");
    } catch (error) {
      showSnackbar("Error", "Unable to rename group. $error");
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _openInviteCreator() async {
    if (!_canEditGroup) return;
    final handles = await Navigator.of(context).push<List<String>>(
      CupertinoPageRoute(
        builder: (_) => const GroupInviteCreator(),
      ),
    );
    if (handles == null || handles.isEmpty) {
      return;
    }
    final normalized = handles.map((e) => e.trim()).where((e) => e.isNotEmpty).toSet().toList(growable: false);
    if (normalized.isEmpty) {
      return;
    }
    setState(() => _busy = true);
    try {
      var invited = 0;
      for (final handle in normalized) {
        await api.inviteUser(
          passwords: widget.provider,
          gid: widget.groupId,
          handle: handle,
        );
        invited++;
      }
      await _refreshGroup();
      if (invited > 0) {
        showSnackbar(
          "Invited",
          "Sent $invited invite${invited == 1 ? "" : "s"}.",
        );
      } else {
        showSnackbar("Error", "No handles were selected.");
      }
    } catch (error) {
      showSnackbar("Error", "Unable to invite participant. $error");
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _removeParticipant(String handle) async {
    if (!_canEditGroup) return;
    setState(() => _busy = true);
    try {
      await api.removeUser(
        passwords: widget.provider,
        gid: widget.groupId,
        handle: handle,
      );
      await _refreshGroup();
      showSnackbar("Removed", "Participant removed.");
    } catch (error) {
      showSnackbar("Error", "Unable to remove participant. $error");
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _confirmDeleteGroup() {
    if (!_canDeleteGroup || _busy) return;
    final title = _isOwner ? "Delete Group?" : "Leave Group?";
    final content = _isOwner ? "This will permanently delete the group." : "You will be removed from this group.";
    showDialog(
      context: context,
      builder: (context) => areYouSure(
        context,
        title: title,
        content: Text(content),
        onNo: () => Navigator.of(context).pop(),
        onYes: () async {
          Navigator.of(context).pop();
          await _deleteGroup();
        },
      ),
    );
  }

  Future<void> _deleteGroup() async {
    if (!_canDeleteGroup) return;
    setState(() => _busy = true);
    try {
      await api.deleteGroup(
        passwords: widget.provider,
        gid: widget.groupId,
      );
      showSnackbar(
        _isOwner ? "Deleted" : "Left Group",
        _isOwner ? "Group deleted." : "You left the group.",
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      showSnackbar(
        "Error",
        "Unable to ${_isOwner ? "delete" : "leave"} group. $error",
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _displayHandle(String handle) {
    try {
      return RustPushBBUtils.rustHandleToBB(handle).displayName;
    } catch (_) {
      return "Contact";
    }
  }

  bool _isOwnerMember(api.GroupSummaryMember member) {
    final uid = widget.currentUserId?.trim();
    if (uid == null || uid.isEmpty) return false;
    return member.userId?.trim() == uid;
  }

  @override
  Widget build(BuildContext context) {
    final members = _summary.members;
    return SettingsScaffold(
      title: _canEditGroup ? "Edit Group" : "Group Details",
      initialHeader: "Group",
      iosSubtitle: iosSubtitle,
      materialSubtitle: materialSubtitle,
      tileColor: tileColor,
      headerColor: headerColor,
      bodySlivers: [
        SliverList(
          delegate: SliverChildListDelegate(
            [
              SettingsSection(
                backgroundColor: tileColor,
                children: [
                  if (_canEditGroup) ...[
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: TextField(
                        controller: _nameController,
                        enabled: !_busy,
                        decoration: InputDecoration(
                          labelText: "Group Name",
                          enabledBorder: OutlineInputBorder(
                            borderSide: BorderSide(
                              color: context.theme.colorScheme.outline,
                            ),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderSide: BorderSide(
                              color: context.theme.colorScheme.primary,
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SettingsDivider(),
                    SettingsTile(
                      backgroundColor: tileColor,
                      title: "Save Group Name",
                      onTap: _busy ? null : _renameGroup,
                      trailing: _busy
                          ? SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: context.theme.colorScheme.primary,
                              ),
                            )
                          : Icon(
                              iOS ? CupertinoIcons.check_mark : Icons.check,
                              color: context.theme.colorScheme.primary,
                            ),
                    ),
                  ] else
                    SettingsTile(
                      backgroundColor: tileColor,
                      title: _summary.displayName.trim().isEmpty ? "(unknown group)" : _summary.displayName,
                      subtitle: "Group Name",
                    ),
                ],
              ),
              SettingsHeader(
                iosSubtitle: iosSubtitle,
                materialSubtitle: materialSubtitle,
                text: "Participants",
              ),
              SettingsSection(
                backgroundColor: tileColor,
                children: [
                  if (_canEditGroup)
                    SettingsTile(
                      backgroundColor: tileColor,
                      title: "Invite Participant",
                      onTap: _busy ? null : _openInviteCreator,
                      trailing: Icon(
                        iOS ? CupertinoIcons.add : Icons.person_add_outlined,
                        color: context.theme.colorScheme.primary,
                      ),
                    ),
                  if (_canEditGroup && members.isNotEmpty) const SettingsDivider(),
                  if (members.isEmpty)
                    SettingsTile(
                      backgroundColor: tileColor,
                      title: "No participants",
                    ),
                  for (var i = 0; i < members.length; i++) ...[
                    ...() {
                      final member = members[i];
                      final isOwner = _isOwnerMember(member);
                      final titleBase =
                          member.name?.trim().isNotEmpty == true ? member.name! : _displayHandle(member.handle);
                      final title = isOwner ? "$titleBase (Owner)" : titleBase;
                      return [
                        SettingsTile(
                          backgroundColor: tileColor,
                          title: title,
                          subtitle: isOwner ? "Owner" : "Participant",
                          trailing: !_canEditGroup || isOwner
                              ? null
                              : IconButton(
                                  onPressed: _busy ? null : () => _removeParticipant(member.handle),
                                  icon: Icon(
                                    iOS ? CupertinoIcons.minus_circle : Icons.remove_circle_outline,
                                    color: context.theme.colorScheme.error,
                                  ),
                                ),
                        ),
                      ];
                    }(),
                    if (i != members.length - 1) const SettingsDivider(),
                  ],
                ],
              ),
              if (_canDeleteGroup)
                SettingsHeader(
                  iosSubtitle: iosSubtitle,
                  materialSubtitle: materialSubtitle,
                  text: "Danger Zone",
                ),
              if (_canDeleteGroup)
                SettingsSection(
                  backgroundColor: tileColor,
                  children: [
                    SettingsTile(
                      backgroundColor: tileColor,
                      title: _isOwner ? "Delete Group" : "Leave Group",
                      onTap: _busy ? null : _confirmDeleteGroup,
                      trailing: Icon(
                        iOS ? CupertinoIcons.trash : Icons.delete_outline,
                        color: context.theme.colorScheme.error,
                      ),
                    ),
                  ],
                ),
            ],
          ),
        ),
      ],
    );
  }
}
