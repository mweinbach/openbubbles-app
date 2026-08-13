import 'package:bluebubbles/app/layouts/conversation_view/pages/conversation_view.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/passwords_panel.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class SharedPasswords extends StatefulWidget {
  final iMessageAppData data;
  final Message message;

  SharedPasswords({
    super.key,
    required this.data,
    required this.message,
  });

  @override
  State<SharedPasswords> createState() => _SharedPasswordsState();
}

class _SharedPasswordsState extends State<SharedPasswords> with AutomaticKeepAliveClientMixin<SharedPasswords> {
  iMessageAppData get data => widget.data;

  @override
  bool get wantKeepAlive => true;

  Future<void> _openPasswords() async {
    final currentChat = ChatsSvc.activeChat?.chat;
    NavigationSvc.closeAllConversationView(context);
    await ChatsSvc.setAllInactive();
    await Navigator.of(Get.context!).push(
      ThemeSwitcher.buildPageRoute(
        builder: (BuildContext context) {
          return const PasswordsPanel();
        },
      ),
    );
    if (currentChat != null) {
      await ChatsSvc.setActiveChat(currentChat);
      if (SettingsSvc.settings.tabletMode.value) {
        await NavigationSvc.pushAndRemoveUntil(
          context,
          ConversationView(
            chat: currentChat,
          ),
          (route) => route.isFirst,
        );
      } else {
        cvc(currentChat).close();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final str = data.ldText?.trim().isNotEmpty == true ? data.ldText!.trim() : "You have been invited";
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: _openPasswords,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(
            children: [
              Icon(
                Icons.password_rounded,
                color: context.theme.colorScheme.primary,
                size: 28,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      "Shared Passwords",
                      style: context.theme.textTheme.bodyLarge?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      str,
                      style: context.theme.textTheme.bodySmall?.copyWith(
                        color: context.theme.colorScheme.outline,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Icon(
                Icons.chevron_right,
                color: context.theme.colorScheme.outline,
                size: 22,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
