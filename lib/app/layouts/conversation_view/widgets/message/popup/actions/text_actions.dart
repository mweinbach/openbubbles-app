import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/popup/message_popup_action_context.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:universal_io/io.dart';

void openLink(MessagePopupActionContext ctx) {
  final String? url = ctx.part.url;
  MethodChannelSvc.actions.openBrowser(link: (url ?? ctx.part.text) ?? '');
  ctx.popDetails();
}

void copyText(MessagePopupActionContext ctx) {
  Clipboard.setData(ClipboardData(text: ctx.part.fullText));
  ctx.popDetails();
  if (!Platform.isAndroid || (FilesystemSvc.androidInfo?.version.sdkInt ?? 0) < 33) {
    ctx.showSnack("Copied", "Copied to clipboard!");
  }
}

void copySelection(MessagePopupActionContext ctx) {
  showBBDialog(
    context: ctx.context,
    title: "Copy Selection",
    content: SelectableText(ctx.part.fullText, style: Theme.of(ctx.context).extension<BubbleText>()!.bubbleText),
  );
}
