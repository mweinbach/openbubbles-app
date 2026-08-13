# database/io/ — ObjectBox Entities (Native & Desktop)

Not used on web — `database/html/` provides web stubs.
After any `@Entity` annotation change: **`dart run build_runner build`**

## Entities

| File | Key Fields | Relations |
|------|-----------|-----------|
| `chat.dart` | guid (unique+indexed), chatIdentifier, isArchived, isPinned, muteType, displayName | → messages (ToMany backlink), → handles/participants (ToMany) |
| `message.dart` | guid (unique+indexed), text, dateCreated (indexed), isFromMe, error, hasDdResults | → chat (ToOne), → handle (ToOne), → attachments (ToMany), → associations (ToMany) |
| `attachment.dart` | guid (unique), uti, mimeType, transferName, totalBytes | → message (ToOne via backlink) |
| `handle.dart` | address+service unique pair, service (iMessage/SMS), country | → contact V1 (ToOne), ← ContactV2.handles (backlink) |
| `contact_v2.dart` | displayName, nativeContactId (unique), avatarPath | ↔ handles (ToMany N:M) |

| `theme.dart` | name (unique), serialized FlutterThemeData, googleFont, gradientBg | — |
| `theme_entry.dart` | reference to a Theme record | — |
| `theme_object.dart` | theme metadata wrapper | — |
| `fcm_data.dart` | FCM tokens and Firebase auth credentials | — |
| `tenor.dart` | GIF search result metadata | — |
| `launch_at_startup.dart` | startup behavior configuration | — |

## Rules
- Primary key: always `int? id` (nullable; ObjectBox assigns on first `put`)
- Unique business key: `@Unique()` + `@Index(type: IndexType.value)`
- Adding a field to an existing entity: include `@Property(uid: ...)` to avoid schema conflicts
- Non-persisted / `Rx*` fields: must be `@Transient()` — see `frontend.md`
- Pure data entities (like `contact_v2.dart`) should have no `Rx*` fields at all

## Platform Guard
```dart
if (kIsWeb) return; // always guard before any Database.* call
```

## Relationships
- ToMany updates: `.clear()` → `.addAll()` → `.applyToDb()`
- ToOne updates: set `.target = object` then `put()` the owning entity
