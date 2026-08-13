# Database Rules — ObjectBox ORM

## Entity Definition

```dart
@Entity()
class MyEntity {
  int? id;                          // ObjectBox auto-assigns; always nullable

  @Index(type: IndexType.value)
  @Unique()
  String guid;                      // Indexed unique business key

  @Property(uid: 1234567890)        // Add uid when schema already exists in prod
  String? optionalField;

  @Transient()
  String? computedField;            // Not persisted; use for cached/reactive values
}
```

Rules:
- Primary key is always `int? id` (nullable so ObjectBox assigns it on first put).
- Add `@Unique()` + `@Index(type: IndexType.value)` on any field used for existence lookups.
- Add `@Property(uid: ...)` when adding a field to an existing production entity (prevents schema conflicts).
- `@Transient()` on **all** `Rx*` fields and any GetX observables — they must never be persisted.
- Pure data entities (ContactV2 pattern) should not contain `Rx*` fields at all. Reactive state belongs in `lib/app/state/`.

After any `@Entity` annotation change: **`dart run build_runner build`**. Never edit `lib/generated/objectbox.g.dart` directly.

## Relations

```dart
// ToOne (foreign key side)
final chat = ToOne<Chat>();

// ToMany (owning side)
final handles = ToMany<Handle>();

// Backlink (inverse, not stored here)
@Backlink('chat')
final messages = ToMany<Message>();
```

- Update ToMany with `.clear()` + `.addAll()` + `.applyToDb()` — never mutate the list without calling `applyToDb()`.
- ToOne: set `.target = object` then put the owning entity.

## Queries

```dart
// Always close queries after use
final query = (Database.messages
      .query(Message_.dateDeleted.isNull())
      ..link(Message_.chat, Chat_.id.equals(chat.id!))
      ..order(Message_.dateCreated, flags: Order.descending))
    .build();
query.limit  = 25;
query.offset = 0;
final results = query.find();   // or findAsync() for non-blocking
query.close();                  // always close
```

- Use `.watch()` for reactive queries that need live updates; subscribe in `initState()`, cancel in `dispose()`.
- Run synchronous queries inside `Database.runInTransaction(TxMode.read, () { ... })`.
- Run from background: `await runAsync(() => query.find())` — never block the UI thread with large queries.

## Transactions

```dart
Database.runInTransaction(TxMode.write, () {
  try {
    Database.chats.put(chat);
  } on UniqueViolationException catch (_) {
    Logger.warn('Duplicate chat — skipping', tag: 'ChatActions');
  }
});
```

- Use `TxMode.read` for queries, `TxMode.write` for puts/removes.
- Catch `UniqueViolationException` on puts where duplicates are possible — log and continue.
- Never nest transactions.

## Serialization

Every entity must implement:
```dart
factory MyEntity.fromMap(Map<String, dynamic> json) { ... }
Map<String, dynamic> toMap() { ... }
```

- Map server key `"ROWID"` or `"id"` → local `id`: `json["ROWID"] ?? json["id"]`.
- Nested objects: call their own `fromMap`/`toMap` recursively.
- Complex fields stored as JSON strings use paired getter/setter:
```dart
String? get dbAttributedBody =>
    jsonEncode(attributedBody.map((e) => e.toMap()).toList());
set dbAttributedBody(String? json) =>
    attributedBody = json == null ? [] : (jsonDecode(json) as List).map((e) => AttributedBody.fromMap(e)).toList();
```

## Platform Guard

Web has no ObjectBox. Guard every DB method:
```dart
static Future<void> doThing() async {
  if (kIsWeb) return;           // or throw Exception("Not supported on web")
  // ... DB work
}
```

Use `kIsWeb`, `kIsDesktop` from `flutter/foundation.dart` — don't check `Platform.isAndroid` etc. at the service/DB level.

## Initialization

`lib/database/database.dart` owns the ObjectBox `Store`. Don't create additional Store instances. Platform paths: `_initDatabaseMobile()` / `_initDatabaseDesktop()`.

## Migrations

Add migrations in `lib/database/migrations/` and register them in the version loop inside `database.dart`:
```dart
switch (nextVersion) {
  case N: MyMigration.migrate(); break;
}
```

Bump `Database.version` when adding a migration.
