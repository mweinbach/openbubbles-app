# conversation_details/ — Chat Info Panel

Displayed as a right-side panel on tablet or pushed screen on mobile.

## Main Screen
`conversation_details.dart` — top-level screen

## Dialogs (`dialogs/`)
- `address_picker.dart` — pick which phone/email address to use for a contact
- `change_name.dart` — rename a group chat
- `timeframe_picker.dart` — date range selector (for media/docs filtering)
- `add_participant.dart` — add a member to a group chat
- `chat_sync_dialog.dart` — progress dialog for re-syncing chat history

## Widgets (`widgets/`)

**Info & Actions**
- `chat_info.dart` — header: avatar, chat name, description
- `chat_options.dart` — action buttons (mute, archive, FaceTime, custom avatar, etc.)
- `participants_list.dart` — group member list
- `contact_tile.dart` — individual participant row (tappable → contact details)

**Shared Media**
- `media_grid_section.dart` — photo/video thumbnail grid
- `media_gallery_card.dart` — tappable media card → opens `FullscreenMedia`
- `attachments_loader.dart` — attachment pagination and caching

**Shared Content**
- `links_section.dart` — shared URLs list
- `documents_section.dart` — shared files/documents list
- `locations_section.dart` — shared location messages list
