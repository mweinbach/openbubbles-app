import 'package:bluebubbles/app/state/attachment_state.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:flutter/widgets.dart';

/// InheritedWidget that makes [AttachmentState] available to all descendant
/// widgets without requiring explicit [Attachment] parameter passing.
///
/// Provided by [AttachmentHolder] for every attachment it renders.
/// Every widget inside the [AttachmentHolder] subtree can call
/// [AttachmentStateScope.of] or [AttachmentStateScope.attachmentOf] to obtain
/// the active state or raw attachment without needing a constructor parameter.
///
/// Usage:
/// ```dart
/// // Reactive field read (registers rebuild dependency)
/// final as = AttachmentStateScope.of(context);
/// final isSending = as.isSending.value;
///
/// // Raw attachment access
/// final attachment = AttachmentStateScope.attachmentOf(context);
/// ```
class AttachmentStateScope extends InheritedWidget {
  const AttachmentStateScope({
    super.key,
    required this.attachmentState,
    required super.child,
  });

  final AttachmentState attachmentState;

  /// The raw [Attachment] for this scope — a shorthand for [attachmentState.attachment].
  Attachment get attachment => attachmentState.attachment;

  // ---------------------------------------------------------------------------
  // Static accessors
  // ---------------------------------------------------------------------------

  /// Returns the nearest [AttachmentState] up the widget tree.
  ///
  /// Registers a rebuild dependency so the calling widget is notified when the
  /// scope's [attachmentState] instance changes.
  ///
  /// Must not be called from [State.dispose].
  static AttachmentState of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<AttachmentStateScope>();
    assert(
      scope != null,
      'No AttachmentStateScope found in context. Ensure the widget is placed inside an AttachmentHolder.',
    );
    return scope!.attachmentState;
  }

  /// Like [of] but returns `null` if no [AttachmentStateScope] is present.
  static AttachmentState? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AttachmentStateScope>()?.attachmentState;

  /// Convenience accessor that returns the raw [Attachment] from the nearest scope.
  static Attachment attachmentOf(BuildContext context) => of(context).attachment;

  /// Like [attachmentOf] but returns `null` if no scope is present.
  static Attachment? maybeAttachmentOf(BuildContext context) => maybeOf(context)?.attachment;

  /// For use inside [State.initState] — reads the [Attachment] once without
  /// registering a rebuild dependency by walking up the widget tree directly.
  ///
  /// Use [attachmentOf] (which registers a dependency) inside [State.build]
  /// or [State.didChangeDependencies] instead.
  static Attachment readAttachmentOnce(BuildContext context) {
    final scope = context.findAncestorWidgetOfExactType<AttachmentStateScope>();
    assert(
      scope != null,
      'No AttachmentStateScope found in context. Ensure the widget is placed inside an AttachmentHolder.',
    );
    return scope!.attachmentState.attachment;
  }

  // ---------------------------------------------------------------------------
  // InheritedWidget
  // ---------------------------------------------------------------------------

  @override
  bool updateShouldNotify(AttachmentStateScope oldWidget) => attachmentState != oldWidget.attachmentState;
}
