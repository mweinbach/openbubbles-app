import 'dart:convert';

import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path/path.dart';
import 'package:universal_html/html.dart' as html;
import 'package:universal_io/io.dart';

class ContactCard extends StatefulWidget {
  const ContactCard({
    super.key,
    required this.file,
    required this.attachment,
  });
  final PlatformFile file;
  final Attachment attachment;

  @override
  State<ContactCard> createState() => _ContactCardState();
}

class _ContactCardState extends State<ContactCard> with AutomaticKeepAliveClientMixin, ThemeHelpers {
  ContactV2? contact;

  @override
  void initState() {
    super.initState();
    init();
  }

  void init() async {
    late String appleContact;

    if (kIsWeb || widget.file.path == null) {
      appleContact = utf8.decode(widget.file.bytes!);
    } else {
      appleContact = await File(widget.file.path!).readAsString();
    }

    final lines = appleContact.split("\n");
    final indices = <int>[];
    final avatarLines = <String>[];
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].startsWith(" ")) {
        indices.add(i);
      }
    }

    if (indices.isNotEmpty) {
      avatarLines.add(lines[indices.first - 1].trim());
    }

    for (int i in indices) {
      avatarLines.add(lines[i].trim());
    }

    if (indices.isNotEmpty) {
      lines.removeRange(indices.first - 1, indices.last + 1);
    }

    final avatarStr = avatarLines.join();

    try {
      contact = AttachmentsSvc.parseAppleContact(appleContact);
    } catch (ex) {
      contact = ContactV2(displayName: "Invalid Contact", nativeContactId: randomString(8));
    }

    if (contact != null && avatarStr.isNotEmpty) {
      try {
        final b64 = "/${avatarStr.split("/").sublist(1).join('/').trim()}";
        final bytes = base64Decode(b64);
        final tempPath = '${Directory.systemTemp.path}/contact_card_${contact!.nativeContactId}.jpg';
        await File(tempPath).writeAsBytes(bytes);
        contact!.avatarPath = tempPath;
      } catch (_) {}
    }

    if (!kIsWeb && widget.file.path != null) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return SizedBox(
      height: 60,
      width: 250,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () async {
            if (kIsWeb || widget.file.path == null) {
              final content = base64.encode(widget.file.bytes!);
              html.AnchorElement(href: "data:application/octet-stream;charset=utf-16le;base64,$content")
                ..setAttribute("download", widget.file.name)
                ..click();
            } else {
              await OpenFilex.open(
                  join(FilesystemSvc.attachmentsPath, widget.attachment.guid!, basename(widget.file.path!)),
                  type: widget.attachment.mimeType);
            }
          },
          child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 15.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: <Widget>[
                  Expanded(
                    child: Text(
                      contact?.computedDisplayName ?? 'Unknown',
                      style: context.theme.textTheme.bodyLarge!.copyWith(fontWeight: FontWeight.bold),
                      overflow: TextOverflow.ellipsis,
                      maxLines: 2,
                      softWrap: true,
                    ),
                  ),
                  const SizedBox(width: 2),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      ContactAvatarWidget(
                        handle: Handle(),
                        contact: contact,
                        borderThickness: 0.5,
                      ),
                      Padding(
                        padding: const EdgeInsets.only(left: 5.0),
                        child: Icon(
                          iOS ? CupertinoIcons.forward : Icons.arrow_forward,
                          color: context.theme.colorScheme.outline,
                          size: 15,
                        ),
                      )
                    ],
                  )
                ],
              )),
        ),
      ),
    );
  }

  @override
  bool get wantKeepAlive => true;
}
