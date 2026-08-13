import 'dart:collection';
import 'dart:io';

import 'package:bluebubbles/helpers/helpers.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

class TrackpadBugWrapper extends StatefulWidget {
  const TrackpadBugWrapper({super.key, required this.builder});

  final Widget Function(BuildContext context, bool bugDetected) builder;

  @override
  State<TrackpadBugWrapper> createState() => _TrackpadBugWrapperState();
}

// https://github.com/fleaflet/flutter_map/issues/1905#issuecomment-3281150931
class _TrackpadBugWrapperState extends State<TrackpadBugWrapper> {
  // These can be further tuned if necessary

  /// Distance (in events) between two events to compare
  static const _numDiffEvents = 5;

  /// Absolute difference between two evts on either x/y axis
  static const _panDiffTrigger = 4;

  /// Absolute difference between two evts on the scale dimension
  static const _scaleDiffTrigger = 0.01;

  final _events = ListQueue<(Offset, double)>(_numDiffEvents);
  bool _latched = false;

  @override
  Widget build(BuildContext context) {
    if (!kIsDesktop || !Platform.isWindows) {
      return widget.builder(context, false);
    }
    return Listener(
      onPointerPanZoomUpdate: (evt) {
        if (_latched || evt.kind != PointerDeviceKind.trackpad) return;

        _events.add((evt.pan, evt.scale));
        if (_events.length > _numDiffEvents) {
          final cmp = _events.removeFirst();

          final panDiff = evt.pan - cmp.$1;
          final scaleDiff = (evt.scale - cmp.$2).abs();

          if ((panDiff.dx.abs() > _panDiffTrigger || panDiff.dy.abs() > _panDiffTrigger) &&
              scaleDiff > _scaleDiffTrigger) {
            setState(() => _latched = true);
          }
        }
      },
      child: widget.builder(context, _latched),
    );
  }
}
