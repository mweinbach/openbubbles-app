import 'package:bluebubbles/app/state/chat_state.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:flutter/widgets.dart';

/// InheritedWidget that makes [ChatState] available to all descendant widgets
/// without requiring explicit [Chat] parameter passing.
///
/// Provided by [ConversationView] at the root of the conversation widget tree.
/// Every widget inside the subtree can call [ChatStateScope.of] or
/// [ChatStateScope.chatOf] to obtain the active state or raw chat without
/// needing a constructor parameter.
///
/// Usage:
/// ```dart
/// // Reactive state read (registers rebuild dependency)
/// final chatState = ChatStateScope.of(context);
/// final title = chatState.title.value;
///
/// // Raw chat access
/// final chat = ChatStateScope.chatOf(context);
///
/// // Safe read in initState (no rebuild dependency)
/// final chat = ChatStateScope.readChatOnce(context);
/// ```
class ChatStateScope extends InheritedWidget {
  const ChatStateScope({
    super.key,
    required this.chatState,
    required super.child,
  });

  final ChatState chatState;

  /// The raw [Chat] for this scope — a shorthand for [chatState.chat].
  Chat get chat => chatState.chat;

  // ---------------------------------------------------------------------------
  // Static accessors
  // ---------------------------------------------------------------------------

  /// Returns the nearest [ChatState] up the widget tree.
  ///
  /// Registers a rebuild dependency so the calling widget is notified when the
  /// scope's [chatState] instance changes (i.e. a different chat is opened).
  ///
  /// Must not be called from [State.dispose].
  static ChatState of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<ChatStateScope>();
    assert(
      scope != null,
      'No ChatStateScope found in context. Ensure the widget is placed inside ConversationView.',
    );
    return scope!.chatState;
  }

  /// Like [of] but returns `null` if no [ChatStateScope] is present.
  static ChatState? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<ChatStateScope>()?.chatState;

  /// Convenience accessor that returns the raw [Chat] from the nearest scope.
  static Chat chatOf(BuildContext context) => of(context).chat;

  /// Like [chatOf] but returns `null` if no scope is present.
  static Chat? maybeChatOf(BuildContext context) => maybeOf(context)?.chat;

  /// For use inside [State.initState] — reads the [Chat] once without
  /// registering a rebuild dependency by walking up the widget tree directly.
  ///
  /// Use [chatOf] (which registers a dependency) inside [State.build] or
  /// [State.didChangeDependencies] instead.
  static Chat readChatOnce(BuildContext context) {
    final scope = context.findAncestorWidgetOfExactType<ChatStateScope>();
    assert(
      scope != null,
      'No ChatStateScope found in context. Ensure the widget is placed inside ConversationView.',
    );
    return scope!.chatState.chat;
  }

  /// Like [readChatOnce] but returns `null` rather than asserting when no
  /// [ChatStateScope] is found. Useful for widgets that may optionally appear
  /// inside or outside a conversation view.
  static Chat? maybeReadChatOnce(BuildContext context) =>
      context.findAncestorWidgetOfExactType<ChatStateScope>()?.chatState.chat;

  // ---------------------------------------------------------------------------
  // InheritedWidget
  // ---------------------------------------------------------------------------

  @override
  bool updateShouldNotify(ChatStateScope oldWidget) => chatState != oldWidget.chatState;
}
