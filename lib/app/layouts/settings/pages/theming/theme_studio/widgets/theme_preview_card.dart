import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/misc/tail_clipper.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// A live, non-interactive mockup of the app's UI that re-renders whenever
/// [struct] changes.  Wrap the caller in [Obx] and read version to rebuild.
/// The preview adapts to the currently selected skin (iOS / Material / Samsung).
class ThemePreviewCard extends StatelessWidget {
  const ThemePreviewCard({super.key, required this.struct});

  final ThemeStruct struct;

  @override
  Widget build(BuildContext context) {
    final skin = SettingsSvc.settings.skin.value;
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: Theme(
        data: struct.data,
        child: LayoutBuilder(
          builder: (ctx, constraints) {
            final wide = constraints.maxWidth > 460;
            return SizedBox(
              height: skin == Skins.iOS ? 295 : 240,
              child: wide ? _wideLayout(ctx, skin) : _narrowLayout(ctx, skin),
            );
          },
        ),
      ),
    );
  }

  Widget _wideLayout(BuildContext context, Skins skin) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(child: IgnorePointer(child: _chatListPane(context, skin))),
        VerticalDivider(width: 1, color: context.theme.colorScheme.outline.withValues(alpha: 0.3)),
        Expanded(child: _chatViewPane(context, skin)),
      ],
    );
  }

  Widget _narrowLayout(BuildContext context, Skins skin) => _chatViewPane(context, skin);

  // ── Chat list pane ──────────────────────────────────────────────────────────

  Widget _chatListPane(BuildContext context, Skins skin) {
    final cs = context.theme.colorScheme;
    return Container(
      color: cs.surface,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _listHeader(context, skin, "BlueBubbles"),
          Expanded(
            child: ListView(
              padding: EdgeInsets.zero,
              children: [
                _fakeChatRow(context, skin, "Alice", "Hey, are you free later?", "9:41 AM", cs.primaryContainer,
                    cs.onPrimaryContainer),
                _chatDivider(context, skin),
                _fakeChatRow(context, skin, "Bob & Emma", "See you tomorrow!", "Yesterday", cs.secondaryContainer,
                    cs.onSecondaryContainer),
                _chatDivider(context, skin),
                _fakeChatRow(
                    context, skin, "Work Chat", "Meeting at 3pm", "Mon", cs.tertiaryContainer, cs.onTertiaryContainer),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _listHeader(BuildContext context, Skins skin, String title) {
    final cs = context.theme.colorScheme;
    final bool isIOS = skin == Skins.iOS;
    return Container(
      height: 46,
      decoration: BoxDecoration(
        color: isIOS ? cs.surfaceContainerHighest.withValues(alpha: 0.7) : cs.surfaceContainerHighest,
        border: isIOS ? Border(bottom: BorderSide(color: cs.outlineVariant.withValues(alpha: 0.25), width: 0.5)) : null,
      ),
      padding: const EdgeInsets.symmetric(horizontal: 12),
      alignment: Alignment.centerLeft,
      child: Text(
        title,
        style: context.theme.textTheme.titleSmall
            ?.copyWith(color: cs.onSurface, fontWeight: isIOS ? FontWeight.bold : FontWeight.w500),
      ),
    );
  }

  Widget _chatDivider(BuildContext context, Skins skin) {
    final divColor = context.theme.colorScheme.outline.withValues(alpha: 0.15);
    if (skin == Skins.iOS) {
      return Padding(
        padding: const EdgeInsets.only(left: 58),
        child: Divider(height: 0.5, thickness: 0.5, color: divColor),
      );
    }
    return Divider(height: 0.5, thickness: 0.5, color: divColor);
  }

  Widget _fakeChatRow(
      BuildContext context, Skins skin, String name, String preview, String time, Color avatarBg, Color avatarFg) {
    final cs = context.theme.colorScheme;
    return Container(
      color: cs.surface,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      child: Row(
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: avatarBg,
            child: Text(name.substring(0, 1),
                style: TextStyle(color: avatarFg, fontSize: 13, fontWeight: FontWeight.bold)),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(name,
                        style: context.theme.textTheme.bodySmall
                            ?.copyWith(fontWeight: FontWeight.w600, color: cs.onSurface)),
                    Text(time, style: context.theme.textTheme.labelSmall?.copyWith(color: cs.outline)),
                  ],
                ),
                const SizedBox(height: 2),
                Text(preview,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: context.theme.textTheme.labelSmall?.copyWith(color: cs.onSurfaceVariant)),
              ],
            ),
          ),
          if (skin != Skins.iOS) ...[
            const SizedBox(width: 6),
            Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(color: cs.primary, shape: BoxShape.circle),
            ),
          ],
        ],
      ),
    );
  }

  // ── Chat view pane ──────────────────────────────────────────────────────────

  Widget _chatViewPane(BuildContext context, Skins skin) {
    final cs = context.theme.colorScheme;
    final bubbleColors = context.theme.extensions[BubbleColors] as BubbleColors?;
    final sentColor = cs.bubble(context, true);
    final receivedColor = bubbleColors?.receivedBubbleColor ?? cs.surfaceContainerHighest;

    return Container(
      color: cs.surface,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          IgnorePointer(child: _chatHeader(context, skin)),
          Expanded(
            child: Container(
              color: cs.surface,
              padding: const EdgeInsets.fromLTRB(8, 6, 8, 10),
              child: skin == Skins.iOS
                  ? _iOSMessageList(context, sentColor, receivedColor)
                  : _groupedMessageList(context, sentColor, receivedColor),
            ),
          ),
          IgnorePointer(child: _textField(context, skin)),
        ],
      ),
    );
  }

  // ── Per-skin chat header ────────────────────────────────────────────────────

  Widget _chatHeader(BuildContext context, Skins skin) {
    final cs = context.theme.colorScheme;
    if (skin == Skins.iOS) {
      return Container(
        height: 100,
        decoration: BoxDecoration(
          color: cs.surfaceContainerHighest.withValues(alpha: 0.72),
          border: Border(bottom: BorderSide(color: cs.outlineVariant.withValues(alpha: 0.25), width: 0.5)),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 8),
        child: Row(
          children: [
            Padding(
              padding: const EdgeInsets.only(left: 10),
              child: Text(
                String.fromCharCode(CupertinoIcons.back.codePoint),
                style: TextStyle(
                  fontFamily: CupertinoIcons.back.fontFamily,
                  package: CupertinoIcons.back.fontPackage,
                  fontSize: 36,
                  color: cs.primary,
                ),
              ),
            ),
            const Spacer(),
            Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                CircleAvatar(
                    radius: 27,
                    backgroundColor: cs.primaryContainer,
                    child: Text("B", style: TextStyle(fontSize: 30, color: cs.onPrimaryContainer))),
                const SizedBox(height: 2),
                Text("BlueBubbles", style: context.theme.textTheme.bodyMedium?.copyWith(color: cs.onSurface)),
              ],
            ),
            const Spacer(),
            // Offset left padding on the arrow
            const SizedBox(width: 16),
          ],
        ),
      );
    } else {
      // Material / Samsung — standard AppBar style
      return Container(
        height: 56,
        color: cs.surfaceContainerHighest,
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
        child: Row(
          children: [
            const SizedBox(width: 2),
            Icon(Icons.arrow_back, size: 24, color: cs.onSurface),
            const SizedBox(width: 4),
            CircleAvatar(
                radius: 17.5,
                backgroundColor: cs.primaryContainer,
                child: Text("B", style: TextStyle(fontSize: 14, color: cs.onPrimaryContainer))),
            const SizedBox(width: 8),
            Expanded(
              child: Text("BlueBubbles",
                  style: context.theme.textTheme.titleLarge?.apply(color: cs.onSurface, fontSizeFactor: 0.85),
                  maxLines: 1,
                  overflow: TextOverflow.fade,
                  softWrap: false),
            ),
            Icon(Icons.more_vert, size: 18, color: cs.onSurface),
            const SizedBox(width: 4),
          ],
        ),
      );
    }
  }

  // ── iOS message list (independent bubbles with tail + avatar) ───────────────

  Widget _iOSMessageList(BuildContext context, Color sentColor, Color receivedColor) {
    return SingleChildScrollView(
      reverse: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _iOSBubbleRow(context, "Hey, whatsup!", false, receivedColor, showTail: true),
          const SizedBox(height: 5),
          _iOSBubbleRow(context, "Nothin much, you?", true, sentColor, showTail: true),
          const SizedBox(height: 5),
          _iOSBubbleRow(context, "Enjoying BlueBubbles!", false, receivedColor, showTail: true),
        ],
      ),
    );
  }

  Widget _iOSBubbleRow(BuildContext context, String text, bool isMe, Color bg, {required bool showTail}) {
    final cs = context.theme.colorScheme;
    if (isMe) {
      return Row(
        mainAxisAlignment: MainAxisAlignment.end,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [_bubble(context, text, true, bg, showTail: showTail)],
      );
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        showTail
            ? CircleAvatar(
                radius: 15,
                backgroundColor: cs.primaryContainer,
                child: Text("B", style: TextStyle(fontSize: 15, color: cs.onPrimaryContainer)))
            : const SizedBox(width: 30),
        const SizedBox(width: 2),
        _bubble(context, text, false, bg, showTail: showTail),
      ],
    );
  }

  // ── Material/Samsung message list (connected bubble groups) ────────────────

  Widget _groupedMessageList(BuildContext context, Color sentColor, Color receivedColor) {
    return SingleChildScrollView(
      reverse: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Received group
          Align(
            alignment: Alignment.centerLeft,
            child: _bubble(context, "Hey! How are you?", false, receivedColor,
                showTail: false, connectLower: true, connectUpper: false),
          ),
          const SizedBox(height: 2),
          Align(
            alignment: Alignment.centerLeft,
            child: _bubble(context, "Miss you! 😊", false, receivedColor,
                showTail: true, connectLower: false, connectUpper: true),
          ),
          const SizedBox(height: 6),
          // Sent group
          Align(
            alignment: Alignment.centerRight,
            child: _bubble(context, "Doing great!", true, sentColor,
                showTail: false, connectLower: true, connectUpper: false),
          ),
          const SizedBox(height: 2),
          Align(
            alignment: Alignment.centerRight,
            child: _bubble(context, "Coffee soon? ☕", true, sentColor,
                showTail: true, connectLower: false, connectUpper: true),
          ),
        ],
      ),
    );
  }

  // ── Preview font size helpers ─────────────────────────────────────────────

  /// Returns the theme's bubbleText font size for use directly in the preview.
  double _previewBubbleFontSize(BuildContext context) {
    return (context.theme.extensions[BubbleText] as BubbleText?)?.bubbleText.fontSize ?? 15.0;
  }

  /// Returns the theme's bodyMedium font size for use directly in the preview.
  double _previewTextFieldFontSize(BuildContext context) {
    return context.theme.textTheme.bodyMedium?.fontSize ?? 14.0;
  }

  // ── Shared bubble widget (shape via TailClipper) ────────────────────────────

  Widget _bubble(BuildContext context, String text, bool isMe, Color bg,
      {required bool showTail, bool connectLower = false, bool connectUpper = false}) {
    final bubbleColors = context.theme.extensions[BubbleColors] as BubbleColors?;
    final textStyle = (context.theme.extensions[BubbleText] as BubbleText).bubbleText.apply(
        color: isMe
            ? context.theme.colorScheme.onBubble(context, true)
            : bubbleColors?.onReceivedBubbleColor ?? context.theme.colorScheme.onSurfaceVariant);
    return ClipPath(
      clipper: TailClipper(
        isFromMe: isMe,
        showTail: showTail,
        connectLower: connectLower,
        connectUpper: connectUpper,
      ),
      child: Container(
        constraints: const BoxConstraints(maxWidth: 200),
        padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 10)
            .add(EdgeInsets.only(left: isMe ? 0 : 10, right: isMe ? 10 : 0)),
        color: bg,
        child: Text(text, style: textStyle.copyWith(fontSize: _previewBubbleFontSize(context))),
      ),
    );
  }

  // ── Per-skin text field ─────────────────────────────────────────────────────

  Widget _textField(BuildContext context, Skins skin) {
    switch (skin) {
      case Skins.iOS:
        return _iOSTextField(context);
      case Skins.Samsung:
        return _samsungTextField(context);
      case Skins.Material:
        return _materialTextField(context);
    }
  }

  Widget _iOSTextField(BuildContext context) {
    final cs = context.theme.colorScheme;
    return Container(
      height: 54,
      decoration: BoxDecoration(
        color: cs.surface,
        border: Border(top: BorderSide(color: cs.outlineVariant.withValues(alpha: 0.3), width: 0.5)),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 7),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: cs.outline.withValues(alpha: 0.2),
              shape: BoxShape.circle,
            ),
            child: Icon(Icons.add, size: 18, color: cs.outline),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Container(
              height: 32,
              decoration: BoxDecoration(
                border: Border.all(color: cs.outlineVariant.withValues(alpha: 0.25), width: 1),
                borderRadius: BorderRadius.circular(20),
              ),
              padding: const EdgeInsets.only(left: 10, right: 5),
              child: Row(
                children: [
                  Expanded(
                    child: Text("iMessage",
                        style: context.theme.textTheme.labelSmall
                            ?.copyWith(color: cs.outline, fontSize: _previewTextFieldFontSize(context))),
                  ),
                  Container(
                    width: 24,
                    height: 24,
                    decoration: BoxDecoration(color: cs.primary, shape: BoxShape.circle),
                    child: Icon(Icons.arrow_upward_rounded, size: 18, color: cs.onPrimary),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _materialTextField(BuildContext context) {
    final cs = context.theme.colorScheme;
    return Container(
      height: 46,
      color: cs.surface,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: cs.outline.withValues(alpha: 0.2),
              shape: BoxShape.circle,
            ),
            child: Icon(Icons.add, size: 18, color: cs.outline),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Container(
              height: 32,
              decoration: BoxDecoration(
                color: cs.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(20),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 10),
              child: Row(
                children: [
                  Expanded(
                    child: Text("Message",
                        style: context.theme.textTheme.labelSmall
                            ?.copyWith(color: cs.outline, fontSize: _previewTextFieldFontSize(context))),
                  ),
                  Icon(Icons.send_rounded, size: 16, color: cs.primary),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _samsungTextField(BuildContext context) {
    final cs = context.theme.colorScheme;
    return Container(
      height: 46,
      color: cs.surface,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: cs.outline.withValues(alpha: 0.2),
              shape: BoxShape.circle,
            ),
            child: Icon(Icons.add, size: 18, color: cs.outline),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Container(
              decoration: BoxDecoration(
                color: cs.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(20),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 10),
              alignment: Alignment.centerLeft,
              child: Text("Message",
                  style: context.theme.textTheme.labelSmall
                      ?.copyWith(color: cs.outline, fontSize: _previewTextFieldFontSize(context))),
            ),
          ),
          const SizedBox(width: 6),
          Icon(Icons.send_rounded, size: 20, color: cs.primary),
        ],
      ),
    );
  }
}
