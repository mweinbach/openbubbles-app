# services/backend/filesystem/ — File System Service

## File
`filesystem_service.dart` — `FilesystemService` (registered with GetIt via `FileSystemSvc` shorthand)

## Responsibilities
- Resolves platform-specific paths for attachments, cache, and temp files
- Manages attachment download destinations (per-GUID subdirectories)
- Cleans up orphaned/cached files when attachments are deleted
- Copies, moves, and deletes files as part of the attachment pipeline

## Key Methods
- `getAttachmentPath(Attachment)` → resolved local file path
- `saveAttachment(Attachment, Uint8List)` → writes bytes to the correct path
- `deleteAttachment(Attachment)` → removes the local file
- `getTempPath()` → temp directory for in-progress downloads / conversions

## Platform Paths
- **Android/iOS**: `getApplicationDocumentsDirectory()` / `getExternalStorageDirectory()`
- **Desktop**: user-configured download path from `Settings.attachmentsPath`
- Guard with `if (kIsWeb) return;` — no filesystem on web

## Related
- Attachment actions: `lib/services/backend/actions/attachment_actions.dart`
- Download manager: `lib/services/network/downloads_service.dart`
