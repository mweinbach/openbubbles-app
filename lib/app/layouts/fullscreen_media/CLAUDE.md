# app/layouts/fullscreen_media/ — Full-Screen Media Viewer

## Files
| File | Purpose |
|------|---------|
| `fullscreen_holder.dart` | Container widget — pinch-zoom, gesture detection, state management |
| `fullscreen_image.dart` | Full-screen image viewer |
| `fullscreen_video.dart` | Full-screen video player |

## Usage

Navigate to `FullscreenHolder` passing an `Attachment`:
```dart
Navigator.push(context, MaterialPageRoute(
  builder: (_) => FullscreenHolder(attachment: attachment),
));
```

`FullscreenHolder` inspects the attachment MIME type and renders either `FullscreenImage` or `FullscreenVideo` as the child.

## Key Behaviors
- Pinch-to-zoom is handled in `FullscreenHolder` — child widgets do not need to implement it
- Tap to toggle the system UI (hide/show status bar and navigation)
- Video player is disposed when the route is popped
- Shares/saves to gallery are triggered from within the holder via the action bar

## Related
- Attachment models: `lib/database/io/attachment.dart`
- Attachment download state: `lib/services/ui/attachments_service.dart`
- Thumbnails in the chat: `lib/app/layouts/conversation_view/widgets/message/attachment/`
