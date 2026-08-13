# widgets/text_field/ — Message Composer

## Buttons (`buttons/`)
Action buttons inside and around the input field:
- Attachment picker button
- Emoji picker button
- Audio record button
- Send / schedule button

## Helpers (`helpers/`)
Input field utilities: mention detection, text formatting, cursor management.

## Controller
All composer state lives in `ConversationViewController` (`lib/services/ui/chat/conversation_view_controller.dart`):
- Current text content
- Pending attachments list (→ `AttachmentsService`)
- Selected reply message
- Scheduled send time
- Send progress

## Key Interactions
- Attachments added here → tracked by `AttachmentsService`
- Send → `OutgoingMsgHandler` (`OutgoingMessageHandler`)
- Reply selection rendered by `widgets/message/reply/`
- Mention autocomplete → `custom_text_editing_controllers.dart` in `lib/app/components/`
