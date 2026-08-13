# widgets/message/attachment/parts/ — Attachment Render States

Per-transfer-state renderers for a single attachment slot. `AttachmentContent` (parent) picks the right widget based on `AttachmentState.transferState`.

## Files

| File | Renders when... |
|------|----------------|
| `not_loaded_content.dart` | Attachment exists on server but has not been downloaded; shows thumbnail placeholder + download button |
| `downloading_content.dart` | Download in progress; shows progress bar |
| `download_progress_content.dart` | Shows byte count / percentage during download |
| `resolved_file_content.dart` | Download complete; renders the actual media (image, video, audio, file icon) |
| `upload_progress_content.dart` | Outgoing attachment being uploaded; shows circular progress |
| `sending_opacity_wrapper.dart` | Wraps any content with reduced opacity while the message is in `isSending` state |

## State Machine
`AttachmentTransferState` enum (in `lib/app/state/attachment_state.dart`):
`idle` → `downloading` → `complete`  
`idle` → `uploading` → `complete`  
Any state → `error`

## Related
- `AttachmentState`: `lib/app/state/attachment_state.dart`
- Parent dispatcher: `../CLAUDE.md` (attachment/)
- Download service: `lib/services/network/downloads_service.dart`
