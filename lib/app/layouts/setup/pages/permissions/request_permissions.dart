import 'package:bluebubbles/app/layouts/setup/pages/page_template.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:universal_io/io.dart';

class RequestPermissions extends StatefulWidget {
  const RequestPermissions({super.key});

  @override
  State<RequestPermissions> createState() => _RequestPermissionsState();
}

class _RequestPermissionsState extends State<RequestPermissions> with WidgetsBindingObserver {
  PermissionStatus _contactsStatus = PermissionStatus.denied;
  PermissionStatus _notifStatus = PermissionStatus.denied;
  PermissionStatus _nearbyStatus = PermissionStatus.denied;
  bool _contactsRequested = false;
  bool _notifRequested = false;
  bool _nearbyRequested = false;


  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshAll();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refreshAll();
  }

  Future<void> _refreshAll() async {
    final results = await Future.wait([
      Permission.contacts.status,
      Permission.notification.status,
      Permission.bluetoothAdvertise.status,
    ]);
    if (!mounted) return;
    setState(() {
      _contactsStatus = results[0];
      _notifStatus = results[1];
      _nearbyStatus = results[2];
    });
  }

  Future<void> _requestContacts() async {
    if (Platform.isAndroid) {
      await ContactsSvcV2.requestContactPermission();
    } else {
      await Permission.contacts.request();
    }
    final status = await Permission.contacts.status;
    if (mounted) {
      setState(() {
        _contactsStatus = status;
        _contactsRequested = true;
      });
    }
  }

  Future<void> _requestNotifications() async {
    final result = await Permission.notification.request();
    if (mounted) {
      setState(() {
        _notifStatus = result;
        _notifRequested = true;
      });
    }
  }

  Future<void> _requestNearby() async {
    final statuses = await [
      Permission.bluetoothAdvertise,
      Permission.bluetoothConnect,
    ].request();
    if (mounted) {
      setState(() {
        _nearbyStatus = statuses[Permission.bluetoothAdvertise]!;
        _nearbyRequested = true;
      });
    }
  }

  bool get _notifRequired {
    // Default to 33 when androidInfo hasn't loaded yet so we never falsely
    // treat the notification permission as not-required on the first build.
    final sdkInt = FilesystemSvc.androidInfo?.version.sdkInt ?? 33;
    return Platform.isIOS || (Platform.isAndroid && sdkInt >= 33);
  }

  bool get _allGranted => _contactsStatus.isGranted && (_notifStatus.isGranted || !_notifRequired) && _nearbyStatus.isGranted;

  @override
  Widget build(BuildContext context) {
    return SetupPageTemplate(
      title: "App Permissions",
      subtitle: "Grant these permissions to get the most out of BlueBubbles.",
      belowSubtitle: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _PermissionCard(
              icon: Icons.contacts_rounded,
              label: "Contacts",
              description: "Show contact names and photos in your conversations.",
              status: _contactsStatus,
              hasRequested: _contactsRequested,
              onRequest: _requestContacts,
            ),
            const SizedBox(height: 12),
            if (_notifRequired)
              _PermissionCard(
                icon: Icons.notifications_rounded,
                label: "Notifications",
                description: "Get notified instantly when new messages arrive.",
                status: _notifStatus,
                hasRequested: _notifRequested,
                onRequest: _requestNotifications,
              ),
            const SizedBox(height: 12),
            _PermissionCard(
              icon: Icons.bluetooth,
              label: "Nearby Devices",
              description: "Use proximity detection to securely transfer data during login.",
              status: _nearbyStatus,
              hasRequested: _nearbyRequested,
              onRequest: _requestNearby,
            ),
          ],
        ),
      ),
      onNextPressed: () async {
        if (_nearbyStatus.isGranted) {
          try {
            await MethodChannelSvc.invokeMethod("enable-bt", {"request": true});
          } catch (e, s) {
            Logger.error("Failed to enable bt", error: e, trace: s);
            return false;
          }
        }

        if (_allGranted) return true;

        final missing = <String>[];
        if (!_contactsStatus.isGranted) missing.add("Contacts");
        if (!_notifStatus.isGranted && _notifRequired) missing.add("Notifications");
        if (!_nearbyStatus.isGranted) missing.add("Nearby");

        return await showBBDialog<bool>(
              context: context,
              title: "Missing Permissions",
              body:
                  "${missing.join(' and ')} ${missing.length == 1 ? 'permission has' : 'permissions have'} not been granted.\n\nAre you sure you want to proceed?",
              actions: [
                BBDialogAction(
                  text: "No",
                  onPressed: () => Navigator.of(context, rootNavigator: true).pop(false),
                ),
                BBDialogAction(
                  text: "Yes",
                  isDefault: true,
                  onPressed: () => Navigator.of(context, rootNavigator: true).pop(true),
                ),
              ],
            ) ??
            false;
      },
    );
  }
}

class _PermissionCard extends StatelessWidget {
  const _PermissionCard({
    required this.icon,
    required this.label,
    required this.description,
    required this.status,
    required this.hasRequested,
    required this.onRequest,
  });

  final IconData icon;
  final String label;
  final String description;
  final PermissionStatus status;
  final bool hasRequested;
  final VoidCallback? onRequest;

  @override
  Widget build(BuildContext context) {
    final granted = status.isGranted;
    final permanentlyDenied = hasRequested && status.isPermanentlyDenied;

    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        color: context.theme.colorScheme.surfaceContainerHighest,
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              // Icon
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [HexColor('2772C3'), HexColor('5CA7F8').darkenPercent(5)],
                  ),
                ),
                child: Icon(icon, color: Colors.white, size: 24),
              ),
              const SizedBox(width: 14),
              // Label + description
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      label,
                      style: context.theme.textTheme.titleSmall!.copyWith(
                        color: context.theme.colorScheme.onSurface,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      description,
                      style: context.theme.textTheme.bodySmall!.copyWith(
                        color: context.theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              // Status chip
              AnimatedContainer(
                duration: const Duration(milliseconds: 300),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(20),
                  color: (granted ? Colors.green : context.theme.colorScheme.error).withValues(alpha: 0.12),
                  border: Border.all(
                    color: (granted ? Colors.green : context.theme.colorScheme.error).withValues(alpha: 0.5),
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      granted ? Icons.check_circle_rounded : Icons.cancel_rounded,
                      size: 14,
                      color: granted ? Colors.green : context.theme.colorScheme.error,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      granted ? "Granted" : "Denied",
                      style: context.theme.textTheme.labelSmall!.copyWith(
                        color: granted ? Colors.green : context.theme.colorScheme.error,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          // Action row — animates away when permission is granted
          AnimatedCrossFade(
            duration: const Duration(milliseconds: 350),
            sizeCurve: Curves.easeInOut,
            crossFadeState: granted ? CrossFadeState.showFirst : CrossFadeState.showSecond,
            firstChild: const SizedBox.shrink(),
            secondChild: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 14),
                const Divider(height: 1),
                const SizedBox(height: 14),
                if (permanentlyDenied) ...[
                  Text(
                    "Permission permanently denied. Enable it in Settings to continue.",
                    style: context.theme.textTheme.bodySmall!.copyWith(
                      color: context.theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 10),
                  const _ActionButton(
                    label: "Open Settings",
                    icon: Icons.settings_rounded,
                    onPressed: openAppSettings,
                  ),
                ] else
                  _ActionButton(
                    label: "Grant Permission",
                    icon: Icons.lock_open_rounded,
                    onPressed: onRequest ?? () {},
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({required this.label, required this.icon, required this.onPressed});

  final String label;
  final IconData icon;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 40,
      child: DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          gradient: LinearGradient(
            begin: AlignmentDirectional.topStart,
            colors: [HexColor('2772C3'), HexColor('5CA7F8').darkenPercent(5)],
          ),
        ),
        child: ElevatedButton.icon(
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.transparent,
            shadowColor: Colors.transparent,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          ),
          onPressed: onPressed,
          icon: Icon(icon, color: Colors.white, size: 18),
          label: Text(
            label,
            style: context.theme.textTheme.bodyMedium!.copyWith(color: Colors.white, fontWeight: FontWeight.w600),
          ),
        ),
      ),
    );
  }
}
