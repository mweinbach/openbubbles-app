import 'dart:async';

import 'package:bluebubbles/helpers/backend/startup_tasks.dart';
import 'package:bluebubbles/services/isolates/global_isolate.dart';
import 'package:bluebubbles/services/isolates/isolate_actions.dart';

/// A specialized isolate for handling incremental sync operations
/// This isolate is lighter than the global isolate and only loads services needed for sync
class IncrementalSyncIsolate extends GlobalIsolate {
  IncrementalSyncIsolate({
    super.taskTimeout = const Duration(minutes: 5),
    super.startupTimeout = const Duration(seconds: 30),
    super.idleTimeout = Duration.zero,
  });

  @override
  String get isolatePortName => 'IncrementalSyncIsolate';

  @override
  String get isolateDebugName => 'IncrementalSyncIsolate';

  @override
  Function get getIsolateEntryPoint => IncrementalSyncIsolate._syncIsolateEntryPoint;

  /// Entry point for the sync isolate - uses shared logic with sync-specific service initialization
  static Future<void> _syncIsolateEntryPoint(List<dynamic> args) async {
    await GlobalIsolate.sharedIsolateEntryPoint(
      args,
      StartupTasks.initSyncIsolateServices,
      IsolateActons.actions,
    );
  }
}
