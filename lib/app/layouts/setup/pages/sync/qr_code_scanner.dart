import 'dart:convert';

import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/wrappers/bb_scaffold.dart';
import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

class QRCodeScanner extends StatefulWidget {
  const QRCodeScanner({super.key});

  @override
  State<QRCodeScanner> createState() => _QRCodeScannerState();
}

class _QRCodeScannerState extends State<QRCodeScanner> {
  final GlobalKey qrKey = GlobalKey(debugLabel: 'QR');
  bool scanned = false;

  @override
  Widget build(BuildContext context) {
    return BBScaffold(
      body: MobileScanner(
        key: qrKey,
        onDetect: (capture) {
          if (!scanned && !isNullOrEmpty(capture.barcodes.first.rawValue)) {
            scanned = true;
            Navigator.of(context).pop(utf8.encode(capture.barcodes.first.rawValue!));
          }
          if (!scanned && !isNullOrEmpty(capture.barcodes.first.rawBytes)!) {
            scanned = true;
            Navigator.of(context).pop(capture.barcodes.first.rawBytes!);
          }
        },
      ),
    );
  }
}
