import 'dart:convert';

import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/generated/objectbox.g.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/backend/descriptors/attachment_query_descriptor.dart';
import 'package:bluebubbles/services/backend/interfaces/attachment_interface.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/foundation.dart';
import 'package:mime_type/mime_type.dart';
// (needed when generating objectbox model code)
// ignore: unnecessary_import
import 'package:objectbox/objectbox.dart';
import 'package:path/path.dart';
import 'package:universal_io/io.dart';
import 'package:telephony_plus/src/models/attachment.dart' as TelephonyAttachment;
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:crypto/crypto.dart';
import 'package:convert/convert.dart';

@Entity()
class Attachment {
  int? id;
  int? originalROWID;

  @Index(type: IndexType.value)
  @Unique()
  String? guid;

  String? uti;
  String? mimeType;
  bool? isOutgoing;
  String? transferName;
  int? totalBytes;
  int? height;
  int? width;
  @Transient()
  Uint8List? bytes;
  String? webUrl;
  bool hasLivePhoto;

  @Transient()
  String? sourcePath;

  String? ckRecordId;

  bool isDownloaded;

  final message = ToOne<Message>();

  Map<String, dynamic>? metadata;

  Map<String, dynamic>? exif;

  String? get dbMetadata => metadata == null ? null : jsonEncode(metadata);
  set dbMetadata(String? json) => metadata = json == null ? null : jsonDecode(json) as Map<String, dynamic>;

  Attachment({
    this.id,
    this.originalROWID,
    this.guid,
    this.uti,
    this.mimeType,
    this.isOutgoing,
    this.transferName,
    this.totalBytes,
    this.height,
    this.width,
    this.metadata,
    this.exif,
    this.bytes,
    this.webUrl,
    this.sourcePath,
    this.hasLivePhoto = false,
    this.isDownloaded = false,
  });

  /// Convert JSON to [Attachment]
  factory Attachment.fromMap(Map<String, dynamic> json) {
    String? mimeType = json["mimeType"];
    if (json["uti"] == "com.apple.coreaudio_format" || json['transferName'].toString().endsWith(".caf")) {
      mimeType = "audio/caf";
    }

    // Load the metadata
    var metadata = json["metadata"];
    if (metadata is String && metadata.isNotEmpty) {
      try {
        metadata = jsonDecode(metadata);
      } catch (_) {}
    }

    // exif uses null = never loaded, {} = loaded with no EXIF data
    var exif = json["exif"];
    if (exif is String && exif.isNotEmpty) {
      try {
        exif = jsonDecode(exif);
      } catch (_) {}
    }

    return Attachment(
      id: json["ROWID"] ?? json["id"],
      originalROWID: json["originalROWID"],
      guid: json["guid"],
      uti: json["uti"],
      mimeType: mimeType ?? mime(json['transferName']),
      isOutgoing: json["isOutgoing"] == true,
      transferName: json['transferName'],
      totalBytes: json['totalBytes'] is int ? json['totalBytes'] : 0,
      height: json["height"] ?? 0,
      width: json["width"] ?? 0,
      metadata: metadata is String ? null : metadata,
      exif: exif is String ? null : exif,
      hasLivePhoto: json["hasLivePhoto"] ?? false,
      isDownloaded: json["isDownloaded"] ?? false,
      sourcePath: json["sourcePath"],
    );
  }

  PlatformFile getFile() {
    return PlatformFile(name: transferName!, bytes: bytes, path: kIsWeb ? null : path, size: totalBytes ?? 0);
  }

  Future<void> writeToDisk() async {
    final file = File(path);
    await file.create(recursive: true);
    await file.writeAsBytes(bytes!);
  }

  Future<TelephonyAttachment.Attachment> toTelephony() async {
    PlatformFile file;
    if (getFile().exists()) {
      file = getFile();
    } else {
      file = await BackendSvc.downloadAttachment(this, original: true, onReceiveProgress: (_, __) {});
    }
    final data = await file.getBytes();
    return TelephonyAttachment.Attachment(bytes: data, mimeType: mimeType!, filename: transferName!);
  }

  Future<Attachment> saveAsync(Message? message) async {
    if (kIsWeb) return this;

    final result = await AttachmentInterface.saveAttachmentAsync(
      attachmentData: toMap(),
      messageData: message?.toMap(),
    );

    id = result.id;
    return this;
  }

  Attachment save(Message? message) {
    if (kIsWeb) return this;
    Database.runInTransaction(TxMode.write, () {
      Attachment? existing = guid == null ? null : Attachment.findOne(guid!);
      if (existing != null) {
        id = existing.id;
        ckRecordId ??= existing.ckRecordId;
      }
      if (message != null) {
        this.message.target = message;
      }
      try {
        id = Database.attachments.put(this);
      } on UniqueViolationException catch (_) {}
    });
    return this;
  }

  static Future<void> bulkSaveAsync(Map<Message, List<Attachment>> map) async {
    // Convert the map to serializable format
    Map<Map<String, dynamic>, List<Map<String, dynamic>>> mapData = {};
    for (var entry in map.entries) {
      mapData[entry.key.toMap()] = entry.value.map((e) => e.toMap()).toList();
    }

    await AttachmentInterface.bulkSaveAttachmentsAsync(mapData: mapData);
  }

  /// replaces a temporary attachment with the new one from the server (async version)
  /// Note: This must be called from the main thread to access cm/cvc services
  static Future<Attachment> replaceAttachmentAsync(String? oldGuid, Attachment newAttachment) async {
    if (kIsWeb) return newAttachment;

    Attachment? existing = await Attachment.findOneAsync(oldGuid!);
    if (existing == null) {
      return Future.error("Old GUID ($oldGuid) does not exist!");
    }

    // Handle cm/cvc services on main thread BEFORE calling isolate
    if (ChatsSvc.activeChat != null) {
      // Image caching is now handled by Flutter's image cache automatically
    }

    // Call the isolate-safe database operations
    final updatedAttachment = await AttachmentInterface.replaceAttachmentAsync(
      oldGuid: oldGuid,
      newAttachmentData: newAttachment.toMap(),
    );

    // Handle file system operations on main thread AFTER isolate call
    String appDocPath = FilesystemSvc.appDocDir.path;
    String pathName = "$appDocPath/attachments/$oldGuid";
    Directory directory = Directory(pathName);

    if (directory.existsSync()) {
      final newDirPath = "$appDocPath/attachments/${newAttachment.guid}";
      await directory.rename(newDirPath);

      // After the directory rename the file inside still has its old name (the temp transferName).
      // If the server assigned a different transferName, rename the file to match so that
      // attachment.path resolves correctly and the file won't be re-downloaded.
      if (newAttachment.transferName != null) {
        final expectedPath = newAttachment.path; // uses new guid + new transferName
        if (!File(expectedPath).existsSync()) {
          final files = Directory(newDirPath).listSync().whereType<File>().toList();
          if (files.isNotEmpty) {
            await files.first.rename(expectedPath);
          }
        }
      }
    }

    // Update newAttachment with values from result
    newAttachment.id = updatedAttachment.id;
    newAttachment.width = updatedAttachment.width;
    newAttachment.height = updatedAttachment.height;
    newAttachment.metadata = updatedAttachment.metadata;
    newAttachment.exif = updatedAttachment.exif;
    // Preserve isDownloaded from the DB record — the action layer does not overwrite it,
    // so if prepAttachment set it to true the value survives the GUID swap.
    newAttachment.isDownloaded = updatedAttachment.isDownloaded;

    return newAttachment;
  }

  static Future<Attachment?> findOneAsync(String guid) async {
    if (kIsWeb) return null;
    return await AttachmentInterface.findOneAttachmentAsync(guid: guid);
  }

  static Attachment? findOne(String guid) {
    if (kIsWeb) return null;
    final query = Database.attachments.query(Attachment_.guid.equals(guid)).build();
    query.limit = 1;
    final result = query.findFirst();
    query.close();
    return result;
  }

  static Future<List<Attachment>> findAsync({
    AttachmentQueryDescriptor? queryDescriptor,
  }) async {
    if (kIsWeb) return [];
    return await AttachmentInterface.findAttachmentsAsync(queryDescriptor: queryDescriptor);
  }

  static Future<void> deleteAsync(String guid) async {
    if (kIsWeb) return;

    await AttachmentInterface.deleteAttachmentAsync(guid: guid);
  }

  static void delete(String guid) {
    if (kIsWeb) return;
    Database.runInTransaction(TxMode.write, () {
      final result = Attachment.findOne(guid);
      if (result?.id != null) {
        if (result?.ckRecordId != null && !PushSvc.syncStopDelete) {
          final list = PrefsSvc.i.getStringList("attachmentDeletionIds-1") ?? [];
          list.add(result!.ckRecordId!);
          PrefsSvc.i.setStringList("attachmentDeletionIds-1", list);
        }
        Database.attachments.remove(result!.id!);
      }
    });
  }

  void applyFromCloud(api.CloudAttachment c, String cloudKitId) {
    ckRecordId = cloudKitId;
    final decoded = api.decodeAttachmentmeta(wrapped: c.cm);
    uti = decoded.uti;
    mimeType = decoded.mimeType;
    isOutgoing = decoded.isOutgoing;
    transferName = decoded.transferName;
    totalBytes = decoded.totalBytes;
    metadata ??= {};
    metadata!["cloud"] = cloudKitId;
    if (decoded.guid.startsWith("at")) {
      final items = decoded.guid.split("_");
      final message = Message.findOne(guid: items[2]);
      guid = "${items[2]}_${items[1]}";
      save(message);
    } else {
      guid = decoded.guid;
      save(null);
    }
  }

  String unconvertAttachmentGuid(String guid) {
    final items = guid.split("_");
    if (items.length == 1) return guid;
    return "at_${items[1]}_${items[0]}";
  }

  Future<api.AttachmentMeta> getAttachmentMeta() async {
    final sum = md5.convert(File(path).readAsBytesSync());
    return api.AttachmentMeta(
      mimeType: mimeType,
      startDate: RustPushBBUtils.nsSinceAppleEpoch(DateTime.now()),
      totalBytes: totalBytes ?? File(path).lengthSync(),
      transferState: 5,
      isSticker: false,
      guid: unconvertAttachmentGuid(guid!),
      hideAttachment: false,
      userInfo: metadata?["rustpush"] != null
          ? api.attachmentToCloud(att: api.restoreAttachment(data: metadata!["rustpush"]))
          : null,
      filename: "~/Library/Messages/Attachments/24/04/${unconvertAttachmentGuid(guid!)}/test.png",
      extras: const api.AttachmentMetaExtra(previewGenerationState: api.NumOrString.num(1)),
      isOutgoing: message.target?.isFromMe ?? true,
      transferName: transferName ?? "unknown",
      version: 1,
      uti: uti,
      pathc: transferName ?? "unknown",
      createdDate: RustPushBBUtils.nsSinceAppleEpoch(DateTime.now()),
      md5: hex.encode(sum.bytes.sublist(0, 8)),
    );
  }

  String getFriendlySize({int decimals = 2}) {
    return (totalBytes ?? 0.0).toDouble().getFriendlySize(decimals: decimals);
  }

  /// Returns the best available width for display purposes.
  /// Prefers the dedicated DB field; falls back to a `width` key in metadata
  /// (e.g. sent by the server before local dimension extraction runs).
  int? get displayWidth {
    if (width != null && width! > 0) return width;
    return (metadata?['width'] as num?)?.toInt();
  }

  /// Returns the best available height for display purposes.
  /// Prefers the dedicated DB field; falls back to a `height` key in metadata.
  int? get displayHeight {
    if (height != null && height! > 0) return height;
    return (metadata?['height'] as num?)?.toInt();
  }

  bool get hasValidSize => (displayWidth ?? 0) > 0 && (displayHeight ?? 0) > 0;

  double get aspectRatio => hasValidSize ? (displayWidth! / displayHeight!).abs() : 0.78;

  String? get mimeStart => mimeType?.split("/").first;

  static String get baseDirectory => FilesystemSvc.attachmentsPath;

  String get directory => "$baseDirectory/$guid";

  String get path {
    String file;
    switch (Platform.operatingSystem) {
      case "windows":
        file = "$directory/${"$transferName".replaceAll(RegExp(r'[<>:"/\|?*]'), "_")}";
      case "linux":
      case "macos":
        file = "$directory/${"$transferName".replaceAll(RegExp(r'/'), "_")}";
      default:
        file = "$directory/${"$transferName".replaceAll(RegExp(r'/'), "_")}";
    }

    if (!canonicalize(file).startsWith(canonicalize(directory))) {
      throw Exception("Path traversal detected, are we under attack??");
    }

    return file;
  }

  String get convertedPath => "$path.png";

  bool get existsOnDisk => File(path).existsSync();

  Future<bool> get existsOnDiskAsync async => await File(path).exists();

  bool get canCompress => mimeStart == "image" && !mimeType!.contains("gif");

  static Attachment merge(Attachment attachment1, Attachment attachment2) {
    attachment1.id ??= attachment2.id;
    attachment1.bytes ??= attachment2.bytes;
    attachment1.guid ??= attachment2.guid;
    attachment1.height ??= attachment2.height;
    attachment1.width ??= attachment2.width;
    attachment1.isOutgoing ??= attachment2.isOutgoing;
    attachment1.mimeType ??= attachment2.mimeType;
    attachment1.totalBytes ??= attachment2.totalBytes;
    attachment1.transferName ??= attachment2.transferName;
    attachment1.uti ??= attachment2.uti;
    attachment1.webUrl ??= attachment2.webUrl;
    attachment1.metadata = mergeTopLevelDicts(attachment1.metadata, attachment2.metadata);
    attachment1.exif = mergeTopLevelDicts(attachment1.exif, attachment2.exif);
    if (attachment2.hasLivePhoto) {
      attachment1.hasLivePhoto = attachment2.hasLivePhoto;
    }
    // Only overwrite isDownloaded if the new attachment is downloaded
    if (!attachment1.isDownloaded && attachment2.isDownloaded) {
      attachment1.isDownloaded = attachment2.isDownloaded;
    }
    if (!attachment1.message.hasValue) {
      attachment1.message.target = attachment2.message.target;
    }
    return attachment1;
  }

  Map<String, dynamic> toMap() => {
        "ROWID": id,
        "originalROWID": originalROWID,
        "guid": guid,
        "uti": uti,
        "mimeType": mimeType,
        "isOutgoing": isOutgoing!,
        "transferName": transferName,
        "totalBytes": totalBytes,
        "height": height,
        "width": width,
        "metadata": jsonEncode(metadata),
        "exif": jsonEncode(exif),
        "hasLivePhoto": hasLivePhoto,
        "isDownloaded": isDownloaded,
        "sourcePath": sourcePath,
        "ckRecordId": ckRecordId,
      };
}
