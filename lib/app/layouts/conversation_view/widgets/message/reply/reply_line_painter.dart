import 'dart:math';

import 'package:bluebubbles/app/state/message_state.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';

class ReplyLineDecoration extends Decoration {
  final bool isFromMe;
  final bool connectUpper;
  final bool connectLower;
  final Color color;
  final BuildContext context;

  const ReplyLineDecoration({
    required this.isFromMe,
    required this.connectUpper,
    required this.connectLower,
    required this.color,
    required this.context,
  });

  @override
  BoxPainter createBoxPainter([VoidCallback? onChanged]) {
    return ReplyLinePainter(
      isFromMe: isFromMe,
      connectUpper: connectUpper,
      connectLower: connectLower,
      color: color,
      context: context,
    );
  }
}

class ReplyLinePainter extends BoxPainter {
  // Outgoing corner tuning by visual result in UI (not by method name).
  // Empirical mapping:
  // - `_addTopConnector` controls the corner that appears visually on top.
  // - `_addBottomConnector` controls the corner that appears visually on bottom.
  static const double _outgoingVisualTopRadiusFactor = 1.0;
  static const double _outgoingVisualBottomRadiusFactor = 1.4;
  static const double _incomingVisualTopRadiusFactor = 0.9;
  static const double _incomingVisualBottomRadiusFactor = 1.2;

  final bool isFromMe;
  final bool connectUpper;
  final bool connectLower;
  final Color color;
  final BuildContext context;

  const ReplyLinePainter({
    required this.isFromMe,
    required this.connectUpper,
    required this.connectLower,
    required this.color,
    required this.context,
  });

  @override
  void paint(Canvas canvas, Offset offset, ImageConfiguration configuration) {
    final size = configuration.size!;
    // Outgoing and incoming message cells use slightly different local anchors.
    final localOffset = offset + Offset(isFromMe ? 35 : 0, 0);
    final midY = localOffset.dy + size.height / 2;

    // Corner radius where the vertical thread line bends into the horizontal segment.
    final radius = min(size.height / 2, 30.0);

    // Left/right edge that the vertical thread segment is pinned to.
    final verticalX = isFromMe ? localOffset.dx : localOffset.dx + size.width - 35;

    // Horizontal endpoint limit near the bubble edge.
    final horizontalLimit = isFromMe
        ? localOffset.dx + size.width - NavigationSvc.width(context) * MessageState.maxBubbleSizeFactor - 30
        : localOffset.dx + NavigationSvc.width(context) * MessageState.maxBubbleSizeFactor;

    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..strokeJoin = StrokeJoin.round;

    final path = Path();

    if (connectUpper) {
      _addBottomConnector(
        path: path,
        localOffset: localOffset,
        size: size,
        midY: midY,
        radius: radius,
        verticalX: verticalX,
        horizontalLimit: horizontalLimit,
      );
    }

    if (connectLower) {
      _addTopConnector(
        path: path,
        localOffset: localOffset,
        size: size,
        midY: midY,
        radius: radius,
        verticalX: verticalX,
        horizontalLimit: horizontalLimit,
      );
    }

    canvas.drawPath(path, paint);
  }

  void _addBottomConnector({
    required Path path,
    required Offset localOffset,
    required Size size,
    required double midY,
    required double radius,
    required double verticalX,
    required double horizontalLimit,
  }) {
    final effectiveRadius =
        isFromMe ? radius * _outgoingVisualBottomRadiusFactor : radius * _incomingVisualBottomRadiusFactor;
    final verticalEndY = localOffset.dy + (size.height / 2 - effectiveRadius).clamp(0, double.infinity);

    if (isFromMe) {
      final turnEndX = verticalX + effectiveRadius;
      final arcRect = Rect.fromLTWH(
        verticalX,
        midY - (2 * effectiveRadius),
        2 * effectiveRadius,
        2 * effectiveRadius,
      );
      // Start at left-middle (pi) and sweep upward->right by 90 degrees.
      path.arcTo(arcRect, pi, -pi / 2, false);
      path.lineTo(max(turnEndX, horizontalLimit), midY);
      return;
    }

    // Incoming: top -> down vertical -> curve left into midline -> continue left.
    path.moveTo(verticalX, localOffset.dy);
    path.lineTo(verticalX, verticalEndY);

    final turnEndX = verticalX - effectiveRadius;
    path.arcToPoint(Offset(turnEndX, midY), clockwise: true, radius: Radius.circular(effectiveRadius));
    path.lineTo(min(turnEndX, horizontalLimit - 35), midY);
  }

  void _addTopConnector({
    required Path path,
    required Offset localOffset,
    required Size size,
    required double midY,
    required double radius,
    required double verticalX,
    required double horizontalLimit,
  }) {
    final effectiveRadius =
        isFromMe ? radius * _outgoingVisualTopRadiusFactor : radius * _incomingVisualTopRadiusFactor;
    final verticalEndY = localOffset.dy + size.height - (size.height / 2 - effectiveRadius).clamp(0, double.infinity);

    if (isFromMe) {
      // Outgoing: bottom -> up vertical -> curve right into midline -> continue right.
      path.moveTo(verticalX, localOffset.dy + size.height);
      // Small overlap avoids tiny seam with the upper connector on stacked replies.
      path.lineTo(verticalX, verticalEndY);

      final turnEndX = verticalX + effectiveRadius;
      final arcRect = Rect.fromLTWH(verticalX, midY, 2 * effectiveRadius, 2 * effectiveRadius);
      // Start at left-middle (pi) and sweep downward->right by 90 degrees.
      path.arcTo(arcRect, pi, pi / 2, false);
      path.lineTo(max(turnEndX, horizontalLimit), midY);
      return;
    }

    // Incoming: bottom -> up vertical -> curve left into midline -> continue left.
    path.moveTo(verticalX, localOffset.dy + size.height);
    path.lineTo(verticalX, verticalEndY);

    final turnEndX = verticalX - effectiveRadius;
    path.arcToPoint(Offset(turnEndX, midY), clockwise: false, radius: Radius.circular(effectiveRadius));
    path.lineTo(min(turnEndX, horizontalLimit - 35), midY);
  }
}
