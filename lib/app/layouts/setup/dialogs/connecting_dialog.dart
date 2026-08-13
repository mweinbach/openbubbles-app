import 'package:bluebubbles/app/layouts/setup/dialogs/failed_to_connect_dialog.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class ConnectingDialog extends StatefulWidget {
  const ConnectingDialog({super.key, required this.onConnect});
  final Function(bool) onConnect;

  @override
  State<ConnectingDialog> createState() => _ConnectingDialogState();
}

class _ConnectingDialogState extends State<ConnectingDialog> {
  @override
  void initState() {
    super.initState();

    if (SocketSvc.state.value == SocketState.connected) {
      widget.onConnect(true);
    } else {
      // Set up a listener to wait for connect events
      ever(SocketSvc.state, (event) {
        if (event == SocketState.connected) {
          widget.onConnect(true);
        } else if (event == SocketState.error || event == SocketState.disconnected) {
          widget.onConnect(false);
        }
        if (mounted) setState(() {});
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (SocketSvc.state.value == SocketState.error) {
      return FailedToConnectDialog(
        onDismiss: () => Navigator.of(context).pop(),
      );
    } else {
      return PopScope(
        canPop: false,
        child: AlertDialog(
          title: Text(
            "Connecting...",
            style: context.theme.textTheme.titleLarge,
          ),
          backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
          content: LinearProgressIndicator(
            backgroundColor: context.theme.colorScheme.outline,
            valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
          ),
        ),
      );
    }
  }
}
