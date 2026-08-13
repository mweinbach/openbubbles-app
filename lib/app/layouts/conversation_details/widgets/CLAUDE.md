# conversation_details/widgets/ — Detail Panel Sub-Widgets

Reusable widgets composing the conversation details / info panel.

## Files

| File | Purpose |
|------|---------|
| `chat_info.dart` | Top section: avatar, name, participant count, edit name button |
| `chat_options.dart` | Action row: mute, pin, archive, block, delete |
| `contact_tile.dart` | Single participant row (avatar, name, address, remove button) |
| `participants_list.dart` | Scrollable list of `ContactTile`s for group chats |
| `attachments_loader.dart` | Loads and paginates shared media/attachments for the media gallery |
| `media_gallery_card.dart` | Tappable thumbnail card for a single shared media item |
| `media_grid_section.dart` | Grid layout section grouping media thumbnails by type |
| `documents_section.dart` | List of shared documents (PDFs, files) |
| `links_section.dart` | List of shared URL link previews |
| `locations_section.dart` | List of shared location messages |

## Related
- Parent panel: `../CLAUDE.md` (conversation_details)
- Dialogs (add participant, leave chat, etc.): `../dialogs/CLAUDE.md`
- Contact avatar: `lib/app/components/avatars/CLAUDE.md`
