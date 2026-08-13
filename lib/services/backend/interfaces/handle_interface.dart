import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/env.dart';
import 'package:bluebubbles/services/backend/actions/handle_actions.dart';
import 'package:get_it/get_it.dart';
import 'package:bluebubbles/services/isolates/global_isolate.dart';

class HandleInterface {
  static Future<Handle> saveHandleAsync({
    required Map<String, dynamic> handleData,
    required bool updateColor,
    required bool updatePoster,
    required bool updateBlocked,
    required bool matchOnOriginalROWID,
  }) async {
    final data = {
      'handleData': handleData,
      'updateColor': updateColor,
      'updatePoster': updatePoster,
      'updateBlocked': updateBlocked,
      'matchOnOriginalROWID': matchOnOriginalROWID,
    };

    late int handleId;
    if (isIsolate) {
      handleId = await HandleActions.saveHandleAsync(data);
    } else {
      handleId = await GetIt.I<GlobalIsolate>().send<int>(IsolateRequestType.saveHandleAsync, input: data);
    }

    // Fetch handle by ID using get
    final handle = Database.handles.get(handleId);
    if (handle == null) {
      throw Exception('Failed to fetch handle with ID $handleId after save');
    }

    return handle;
  }

  static Future<List<Handle>> bulkSaveHandlesAsync({
    required List<Map<String, dynamic>> handlesData,
    required bool matchOnOriginalROWID,
  }) async {
    final data = {
      'handlesData': handlesData,
      'matchOnOriginalROWID': matchOnOriginalROWID,
    };

    late List<int> handleIds;
    if (isIsolate) {
      handleIds = await HandleActions.bulkSaveHandlesAsync(data);
    } else {
      handleIds = await GetIt.I<GlobalIsolate>().send<List<int>>(IsolateRequestType.bulkSaveHandlesAsync, input: data);
    }

    // Fetch handles by ID using getMany for efficiency
    return Database.handles.getMany(handleIds).whereType<Handle>().toList();
  }

  static Future<Handle?> findOneHandleAsync({
    int? id,
    int? originalROWID,
    String? address,
    String? service,
  }) async {
    final data = {
      'id': id,
      'originalROWID': originalROWID,
      'address': address,
      'service': service,
    };

    late int? handleId;
    if (isIsolate) {
      handleId = await HandleActions.findOneHandleAsync(data);
    } else {
      handleId = await GetIt.I<GlobalIsolate>().send<int?>(IsolateRequestType.findOneHandleAsync, input: data);
    }

    if (handleId == null) return null;

    // Fetch handle by ID using get
    return Database.handles.get(handleId);
  }

  static Future<List<Handle>> findHandlesAsync() async {
    late List<int> handleIds;
    if (isIsolate) {
      handleIds = await HandleActions.findHandlesAsync({});
    } else {
      handleIds = await GetIt.I<GlobalIsolate>().send<List<int>>(IsolateRequestType.findHandlesAsync, input: {});
    }

    // Fetch handles by ID using getMany for efficiency
    return Database.handles.getMany(handleIds).whereType<Handle>().toList();
  }
}
