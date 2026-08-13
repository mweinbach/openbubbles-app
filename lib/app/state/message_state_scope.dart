import 'package:bluebubbles/app/state/message_state.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:flutter/widgets.dart';

/// InheritedWidget that makes [MessageState] available to all descendant
/// widgets without requiring explicit [Message] parameter passing.
///
/// Provided by [MessageHolder] (and [ReplyHolder] for the reply preview area).
/// Every widget inside the [MessageHolder] subtree can call
/// [MessageStateScope.of] or [MessageStateScope.messageOf] to obtain the
/// active state or raw message without needing a constructor parameter.
///
/// Usage:
/// ```dart
/// // Reactive field read (registers rebuild dependency)
/// final ms = MessageStateScope.of(context);
/// final isFromMe = ms.isFromMe.value;
///
/// // Raw message access
/// final message = MessageStateScope.messageOf(context);
/// ```
class MessageStateScope extends InheritedWidget {
  const MessageStateScope({
    super.key,
    required this.messageState,
    required super.child,
  });

  final MessageState messageState;

  /// The raw [Message] for this scope — a shorthand for [messageState.message].
  Message get message => messageState.message;

  // ---------------------------------------------------------------------------
  // Static accessors
  // ---------------------------------------------------------------------------

  /// Returns the nearest [MessageState] up the widget tree.
  ///
  /// Registers a rebuild dependency so the calling widget is notified when the
  /// scope's [messageState] instance changes.
  ///
  /// Must not be called from [State.dispose].
  static MessageState of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<MessageStateScope>();
    assert(scope != null, 'No MessageStateScope found in context. Ensure the widget is placed inside a MessageHolder.');
    return scope!.messageState;
  }

  /// Like [of] but returns `null` if no [MessageStateScope] is present.
  static MessageState? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<MessageStateScope>()?.messageState;

  /// Convenience accessor that returns the raw [Message] from the nearest scope.
  static Message messageOf(BuildContext context) => of(context).message;

  /// Like [messageOf] but returns `null` if no scope is present.
  static Message? maybeMessageOf(BuildContext context) => maybeOf(context)?.message;

  /// For use inside [State.initState] — reads the [Message] once without
  /// registering a rebuild dependency by walking up the widget tree directly.
  ///
  /// Use [messageOf] (which registers a dependency) inside [State.build]
  /// or [State.didChangeDependencies] instead.
  static Message readMessageOnce(BuildContext context) {
    final scope = context.findAncestorWidgetOfExactType<MessageStateScope>();
    assert(scope != null, 'No MessageStateScope found in context. Ensure the widget is placed inside a MessageHolder.');
    return scope!.messageState.message;
  }

  /// For use inside [State.initState] — reads the [MessageState] once without
  /// registering a rebuild dependency by walking up the widget tree directly.
  ///
  /// Use [of] (which registers a dependency) inside [State.build]
  /// or [State.didChangeDependencies] instead.
  static MessageState readStateOnce(BuildContext context) {
    final scope = context.findAncestorWidgetOfExactType<MessageStateScope>();
    assert(scope != null, 'No MessageStateScope found in context. Ensure the widget is placed inside a MessageHolder.');
    return scope!.messageState;
  }

  // ---------------------------------------------------------------------------
  // InheritedWidget
  // ---------------------------------------------------------------------------

  @override
  bool updateShouldNotify(MessageStateScope oldWidget) => messageState != oldWidget.messageState;
}
