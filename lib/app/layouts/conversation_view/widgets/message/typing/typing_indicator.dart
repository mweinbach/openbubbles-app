import 'dart:math' as math;

import 'package:bluebubbles/app/components/avatars/contact_avatar_group_widget.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/typing/typing_clipper.dart';
import 'package:bluebubbles/app/state/chat_state_scope.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class TypingIndicator extends StatefulWidget {
  const TypingIndicator({
    super.key,
    this.visible,
    this.controller,
    this.scale = 1.0,
  });

  final bool? visible;
  final ConversationViewController? controller;
  final double scale;

  @override
  State<TypingIndicator> createState() => _TypingIndicatorState();
}

class _TypingIndicatorState extends State<TypingIndicator> with SingleTickerProviderStateMixin, ThemeHelpers {
  late final AnimationController _scaleController;
  late final Animation<double> _scaleAnimation;

  /// Whether the bubble content is present in the tree (false only after the
  /// hide animation has fully completed).
  bool _isShowing = false;

  /// GetX worker that reacts to [ConversationViewController.showTypingIndicatorFor]
  /// changes. Only created when a controller is provided.
  Worker? _visibilityWorker;

  bool get _currentVisibility => widget.controller?.showTypingIndicatorFor.isNotEmpty ?? widget.visible ?? false;

  @override
  void initState() {
    super.initState();
    _scaleController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 280),
    );
    _scaleAnimation = CurvedAnimation(
      parent: _scaleController,
      curve: Curves.easeOutBack,
      reverseCurve: Curves.easeIn,
    );

    // After the hide animation finishes, remove the content from the tree so
    // it no longer occupies layout space.
    _scaleController.addStatusListener((status) {
      if (status == AnimationStatus.dismissed && mounted) {
        setState(() => _isShowing = false);
      }
    });

    _isShowing = _currentVisibility;
    if (_isShowing) {
      _scaleController.forward(from: 0.0);
    }

    // When a controller is provided, use a GetX worker to reliably observe
    // the reactive list of typing participants. This is more direct than
    // relying on didUpdateWidget, which can miss repaints for scale-only changes.
    if (widget.controller != null) {
      _visibilityWorker =
          ever(widget.controller!.showTypingIndicatorFor, (_) => _onVisibilityChanged(_currentVisibility));
    }
  }

  void _onVisibilityChanged(bool isVisible) {
    if (!mounted) return;
    if (isVisible && !_isShowing) {
      setState(() => _isShowing = true);
      _scaleController.forward(from: 0.0);
    } else if (!isVisible && _isShowing) {
      // _scaleController status listener will set _isShowing = false once dismissed.
      _scaleController.reverse();
    } else if (isVisible && _isShowing) {
      // The set of typing participants (or their avatars) may have changed
      // while already showing — rebuild so the bubble reflects the new state.
      setState(() {});
    }
  }

  @override
  void dispose() {
    _visibilityWorker?.dispose();
    _scaleController.dispose();
    super.dispose();
  }

  Widget _buildBubble(BuildContext context) {
    return iOS || ChatStateScope.maybeChatOf(context) == null
        ? ClipPath(
            clipper: const TypingClipper(),
            child: Container(
              height: 50,
              color: context.theme.colorScheme.surfaceContainerHighest,
              padding: const EdgeInsets.fromLTRB(30, 10, 14, 20),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (widget.controller != null &&
                      widget.controller!.showTypingIndicatorFor.length == 1 &&
                      widget.controller!.typingIndicatorData[widget.controller!.showTypingIndicatorFor.first.address]
                              ?.$2 !=
                          null)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(99),
                        child: Image.memory(
                          widget.controller!
                              .typingIndicatorData[widget.controller!.showTypingIndicatorFor.first.address]!.$2!,
                        ),
                      ),
                    ),
                  const AnimatedDot(index: 2),
                  const AnimatedDot(index: 1),
                  const AnimatedDot(index: 0),
                ],
              ),
            ),
          )
        : Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.only(left: 10, right: 10),
                child: ContactAvatarGroupWidget(
                  participants: [...(widget.controller?.showTypingIndicatorFor ?? [])],
                  size: 25,
                  editable: false,
                ),
              ),
              if (widget.controller != null &&
                  widget.controller!.showTypingIndicatorFor.length == 1 &&
                  widget.controller!.typingIndicatorData[widget.controller!.showTypingIndicatorFor.first.address]?.$2 !=
                      null)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10),
                  height: 25,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(99),
                    child: Image.memory(
                      widget.controller!.typingIndicatorData[widget.controller!.showTypingIndicatorFor.first.address]!
                          .$2!,
                    ),
                  ),
                ),
              const AnimatedDot(index: 2),
              const AnimatedDot(index: 1),
              const AnimatedDot(index: 0),
            ],
          );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedSize(
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeOut,
      child: _isShowing
          ? ScaleTransition(
              scale: _scaleAnimation,
              // Anchor the grow/shrink at the bottom-left — the tail of the
              // speech bubble — so it feels like a real iMessage bubble.
              alignment: Alignment.bottomLeft,
              child: Padding(
                padding: const EdgeInsets.only(top: 5),
                child: _buildBubble(context),
              ))
          : const SizedBox.shrink(),
    );
  }
}

class AnimatedDot extends StatefulWidget {
  final int index;
  const AnimatedDot({super.key, required this.index});

  @override
  State<AnimatedDot> createState() => _AnimatedDotState();
}

class _AnimatedDotState extends State<AnimatedDot> with SingleTickerProviderStateMixin, ThemeHelpers {
  late final AnimationController _controller;
  late final Animation animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 700), animationBehavior: AnimationBehavior.preserve);
    _controller.addStatusListener((state) {
      if (state == AnimationStatus.completed && mounted) {
        _controller.forward(from: 0.0);
      }
    });

    animation = Tween(
      begin: 0.0,
      end: math.pi,
    ).animate(_controller);

    _controller.forward(from: 0.0);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (iOS) {
      return AnimatedBuilder(
        animation: animation,
        builder: (context, child) {
          final amt = (math.sin(animation.value + (widget.index) * math.pi / 4).abs() * 20).clamp(1, 20).toDouble();
          return Container(
            decoration: BoxDecoration(
              color: ThemeSvc.inDarkMode(context)
                  ? context.theme.colorScheme.surfaceContainerHighest.lightenPercent(amt)
                  : context.theme.colorScheme.surfaceContainerHighest.darkenPercent(amt),
              shape: BoxShape.circle,
            ),
            width: 10,
            height: 10,
            margin: const EdgeInsets.symmetric(horizontal: 2),
          );
        },
      );
    } else {
      return AnimatedBuilder(
        animation: animation,
        builder: (context, child) {
          return Padding(
            padding: EdgeInsets.only(
                bottom: (math.sin(animation.value + (widget.index) * math.pi / 4).abs() * 20).clamp(1, 20).toDouble()),
            child: Container(
              decoration: BoxDecoration(
                color: context.theme.colorScheme.surfaceContainerHighest,
                shape: BoxShape.circle,
              ),
              width: 4,
              height: 4,
              margin: const EdgeInsets.symmetric(horizontal: 2),
            ),
          );
        },
      );
    }
  }
}
