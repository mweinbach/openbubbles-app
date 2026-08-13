import 'package:bluebubbles/app/components/avatars/contact_avatar_group_widget.dart';
import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/string_utils.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class ChatCreatorTile extends StatefulWidget {
  const ChatCreatorTile({
    super.key,
    required this.title,
    required this.subtitle,
    this.chat,
    this.contact,
    this.format = false,
    this.showTrailing = true,
    this.label,
  });

  final String title;
  final String subtitle;
  final Chat? chat;
  final ContactV2? contact;
  final bool format;
  final bool showTrailing;
  final String? label;

  @override
  State<StatefulWidget> createState() => _ChatCreatorTileState();
}

class _ChatCreatorTileState extends State<ChatCreatorTile> with ThemeHelpers {
  String? _formattedPhone;
  bool _isFormatting = false;

  @override
  void initState() {
    super.initState();
    if (widget.format && !_isFormatting) {
      _formatPhoneNumber();
    }
  }

  @override
  void didUpdateWidget(ChatCreatorTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.format && widget.subtitle != oldWidget.subtitle && !_isFormatting) {
      _formatPhoneNumber();
    }
  }

  Future<void> _formatPhoneNumber() async {
    _isFormatting = true;
    final formatted = formatPhoneNumber(cleansePhoneNumber(widget.subtitle));
    if (mounted) {
      setState(() {
        _formattedPhone = formatted;
        _isFormatting = false;
      });
    }
  }

  String _buildSubtitleText() {
    final base = widget.format ? (_formattedPhone ?? widget.subtitle) : widget.subtitle;
    if (widget.label != null && widget.label!.isNotEmpty) {
      return '$base  •  ${widget.label!}';
    }
    return base;
  }

  @override
  Widget build(BuildContext context) {
    return Obx(() => ListTile(
        mouseCursor: MouseCursor.defer,
        enableFeedback: true,
        dense: SettingsSvc.settings.denseChatTiles.value,
        minVerticalPadding: 10,
        horizontalTitleGap: 10,
        title: RichText(
          text: TextSpan(
            children: MessageHelper.buildEmojiText(
              widget.title,
              context.theme.textTheme.bodyLarge!,
            ),
          ),
          overflow: TextOverflow.ellipsis,
        ),
        subtitle: Text(
          _buildSubtitleText(),
          style: context.theme.textTheme.bodySmall!.copyWith(color: context.theme.colorScheme.outline),
        ),
        leading: Padding(
          padding: const EdgeInsets.only(right: 5.0),
          child: widget.chat != null
              ? ContactAvatarGroupWidget(
                  chat: widget.chat!,
                  editable: false,
                )
              : ContactAvatarWidget(
                  handle: Handle(address: widget.subtitle),
                  contact: widget.contact,
                  editable: false,
                ),
        ),
        trailing: widget.chat == null || !widget.showTrailing
            ? null
            : Icon(!material ? CupertinoIcons.forward : Icons.arrow_forward,
                color: context.theme.colorScheme.bubble(context, widget.chat!.isIMessage))));
  }
}
