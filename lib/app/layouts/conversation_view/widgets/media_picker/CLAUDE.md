# conversation_view/widgets/media_picker/ — Attachment Picker UI

UI for selecting media files to attach before sending a message.

## Files

| File | Purpose |
|------|---------|
| `text_field_attachment_picker.dart` | Bottom-sheet picker that browses the photo library and recent files; tapping an item adds it to the composer draft |
| `attachment_picker_file.dart` | Renders a single file/photo thumbnail item inside the picker |

## Integration
Opened from the compose bar via the attachment (paperclip / `+`) button in `widgets/text_field/`.
Selected attachments are stored in `ConversationViewController.pickedImages`.

## Related
- Compose bar: `../text_field/CLAUDE.md`
- Conversation view controller: `lib/services/ui/chat/conversation_view_controller.dart`
- Attachment state: `lib/app/state/attachment_state.dart`
