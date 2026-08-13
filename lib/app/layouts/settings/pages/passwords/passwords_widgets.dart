import 'package:bluebubbles/app/layouts/settings/pages/passwords/password_models.dart';
import 'package:flutter/material.dart';

class CredentialAvatar extends StatelessWidget {
  final CredentialItem credential;

  const CredentialAvatar({super.key, required this.credential});

  @override
  Widget build(BuildContext context) {
    return CircleAvatar(
      radius: 18,
      backgroundColor: credential.color,
      child: Icon(
        credential.icon,
        color: Colors.white,
        size: 18,
      ),
    );
  }
}
