import 'package:bluebubbles/app/layouts/conversation_view/pages/handlers/message_list_animation_config.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:flutter/material.dart';

/// Orchestrates message animations when they're added to the list.
///
/// Responsibilities:
/// - Track which messages are currently animating
/// - Apply appropriate animation based on message type (sent vs received)
/// - Build animated widgets with slide/size/fade transitions
/// - Clear animation flags when animations complete
class MessageAnimationOrchestrator {
  /// Track GUIDs of messages currently being animated
  final Set<String> animatingMessageGuids = {};

  /// Check if a message should be animated
  bool isMessageAnimating(Message message) {
    return message.guid != null && animatingMessageGuids.contains(message.guid!);
  }

  /// Mark a message as animating
  void markAnimating(Message message) {
    if (message.guid != null) {
      animatingMessageGuids.add(message.guid!);
    }
  }

  /// Clear animation flag for a message
  void clearAnimating(Message message, {bool mounted = true}) {
    if (mounted && message.guid != null) {
      animatingMessageGuids.remove(message.guid!);
    }
  }

  /// Build the animated widget for a sent (outgoing) message.
  /// Includes slide + size + fade transitions.
  Widget buildSentMessageAnimation({
    required Widget child,
    required Animation<double> animation,
  }) {
    return SlideTransition(
      position: animation.drive(
        Tween<Offset>(
          begin: MessageListAnimationConfig.slideStartOffset,
          end: Offset.zero,
        ).chain(CurveTween(curve: MessageListAnimationConfig.insertionSlideCurve)),
      ),
      child: SizeTransition(
        sizeFactor: animation.drive(
          Tween<double>(
            begin: MessageListAnimationConfig.sizeTransitionStartRatio,
            end: 1.0,
          ).chain(
            CurveTween(curve: MessageListAnimationConfig.insertionSizeCurve),
          ),
        ),
        axisAlignment: MessageListAnimationConfig.sizeTransitionAxisAlignment,
        child: FadeTransition(
          opacity: animation.drive(
            Tween<double>(begin: 0.0, end: 1.0).chain(
              CurveTween(
                curve: MessageListAnimationConfig.fadeInterval,
              ),
            ),
          ),
          child: child,
        ),
      ),
    );
  }

  /// Build the animated widget for a received (incoming) message.
  /// Includes slide + size transitions (no fade).
  Widget buildReceivedMessageAnimation({
    required Widget child,
    required Animation<double> animation,
  }) {
    return SlideTransition(
      position: animation.drive(
        Tween<Offset>(
          begin: MessageListAnimationConfig.slideStartOffset,
          end: Offset.zero,
        ).chain(CurveTween(curve: MessageListAnimationConfig.insertionSlideCurve)),
      ),
      child: SizeTransition(
        sizeFactor: animation.drive(
          Tween<double>(
            begin: MessageListAnimationConfig.sizeTransitionStartRatio,
            end: 1.0,
          ).chain(
            CurveTween(curve: MessageListAnimationConfig.insertionSizeCurve),
          ),
        ),
        axisAlignment: MessageListAnimationConfig.sizeTransitionAxisAlignment,
        child: child,
      ),
    );
  }

  /// Get the insertion duration for new messages
  Duration getInsertionDuration() => MessageListAnimationConfig.insertionDuration;
}
