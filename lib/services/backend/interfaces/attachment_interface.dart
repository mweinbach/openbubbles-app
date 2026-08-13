import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/env.dart';
import 'package:bluebubbles/services/backend/actions/attachment_actions.dart';
import 'package:bluebubbles/services/backend/descriptors/attachment_query_descriptor.dart';
import 'package:bluebubbles/services/isolates/global_isolate.dart';
import 'package:get_it/get_it.dart';

class AttachmentInterface {
  static Future<Attachment> saveAttachmentAsync({
    required Map<String, dynamic> attachmentData,
    Map<String, dynamic>? messageData,
    bool hydrateMessage = true,
  }) async {
    final data = {
      'attachmentData': attachmentData,
      'messageData': messageData,
    };

    late int attachmentId;
    if (isIsolate) {
      attachmentId = await AttachmentActions.saveAttachmentAsync(data);
    } else {
      attachmentId = await GetIt.I<GlobalIsolate>().send<int>(IsolateRequestType.saveAttachmentAsync, input: data);
    }

    // Fetch attachment by ID using get
    final attachment = Database.attachments.get(attachmentId);
    if (attachment == null) {
      throw Exception('Failed to fetch attachment with ID $attachmentId after save');
    }

    return attachment;
  }

  static Future<void> bulkSaveAttachmentsAsync({
    required Map<Map<String, dynamic>, List<Map<String, dynamic>>> mapData,
  }) async {
    final data = {
      'mapData': mapData,
    };

    if (isIsolate) {
      return await AttachmentActions.bulkSaveAttachmentsAsync(data);
    } else {
      return await GetIt.I<GlobalIsolate>().send<void>(IsolateRequestType.bulkSaveAttachmentsAsync, input: data);
    }
  }

  static Future<Attachment> replaceAttachmentAsync({
    required String oldGuid,
    required Map<String, dynamic> newAttachmentData,
    bool hydrateMessage = true,
  }) async {
    final data = {
      'oldGuid': oldGuid,
      'newAttachmentData': newAttachmentData,
    };

    late int attachmentId;
    if (isIsolate) {
      attachmentId = await AttachmentActions.replaceAttachmentAsync(data);
    } else {
      attachmentId = await GetIt.I<GlobalIsolate>().send<int>(IsolateRequestType.replaceAttachmentAsync, input: data);
    }

    // Fetch attachment by ID using get
    final attachment = Database.attachments.get(attachmentId);
    if (attachment == null) {
      throw Exception('Failed to fetch attachment with ID $attachmentId after replace');
    }

    return attachment;
  }

  static Future<Attachment?> findOneAttachmentAsync({
    required String guid,
    bool hydrateMessage = true,
  }) async {
    final data = {
      'guid': guid,
    };

    late int? attachmentId;
    if (isIsolate) {
      attachmentId = await AttachmentActions.findOneAttachmentAsync(data);
    } else {
      attachmentId = await GetIt.I<GlobalIsolate>().send<int?>(IsolateRequestType.findOneAttachmentAsync, input: data);
    }

    if (attachmentId == null) return null;

    // Fetch attachment by ID using get
    final attachment = Database.attachments.get(attachmentId);
    return attachment;
  }

  static Future<List<Attachment>> findAttachmentsAsync({
    AttachmentQueryDescriptor? queryDescriptor,
    bool hydrateMessage = true,
  }) async {
    final data = {
      'queryDescriptor': queryDescriptor?.toMap(),
    };

    late List<int> attachmentIds;
    if (isIsolate) {
      attachmentIds = await AttachmentActions.findAttachmentsAsync(data);
    } else {
      attachmentIds =
          await GetIt.I<GlobalIsolate>().send<List<int>>(IsolateRequestType.findAttachmentsAsync, input: data);
    }

    // Fetch attachments by ID using getMany for efficiency
    final attachments = Database.attachments.getMany(attachmentIds).whereType<Attachment>().toList();
    return attachments;
  }

  static Future<void> deleteAttachmentAsync({
    required String guid,
  }) async {
    final data = {
      'guid': guid,
    };

    if (isIsolate) {
      return await AttachmentActions.deleteAttachmentAsync(data);
    } else {
      return await GetIt.I<GlobalIsolate>().send<void>(IsolateRequestType.deleteAttachmentAsync, input: data);
    }
  }
}
