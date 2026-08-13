# Common Development Tasks

Step-by-step recipes for the most frequent changes in this codebase. Each recipe lists the files to touch in order.

---

## 1. Add a New App Setting

**Files to touch (in order):**

1. **`lib/database/global/settings.dart`** — Add the field as an `Rx*` observable:
   ```dart
   final RxBool myNewSetting = false.obs;
   ```
   Also add it to `fromMap()` (deserialize) and `toMap()` (serialize).

2. **`lib/app/layouts/settings/pages/<category>/`** — Add a tile in the appropriate page using widgets from `lib/app/layouts/settings/widgets/tiles/`. For a boolean toggle:
   ```dart
   SettingsTile(
     title: "My New Setting",
     subtitle: "Description of what it does",
     onTap: () {
       SettingsSvc.settings.myNewSetting.toggle();
       saveSettings();
     },
     trailing: Obx(() => Switch(
       value: SettingsSvc.settings.myNewSetting.value,
       onChanged: (val) {
         SettingsSvc.settings.myNewSetting.value = val;
         saveSettings();
       },
     )),
   )
   ```

3. **Access anywhere** via `SettingsSvc.settings.myNewSetting.value`.

---

## 2. Add a New Backend Action (Isolate Operation)

**Files to touch (in order):**

1. **`lib/services/backend/actions/<resource>_actions.dart`** — Add the static method:
   ```dart
   static Future<int?> myAction(Map<String, dynamic> data) async {
     final someId = data['someId'] as int;
     // Do DB work, return primitive
     return Database.runInTransaction(TxMode.write, () { ... });
   }
   ```

2. **`lib/services/isolates/global_isolate.dart`** — Add to `IsolateRequestType` enum:
   ```dart
   myAction,
   ```

3. **`lib/services/isolates/isolate_actions.dart`** — Register in the actions map:
   ```dart
   IsolateRequestType.myAction: ResourceActions.myAction,
   ```

4. **`lib/services/backend/interfaces/<resource>_interface.dart`** — Add the interface method with isolate routing:
   ```dart
   static Future<MyModel?> myAction({required int someId}) async {
     final data = {'someId': someId};
     final int? id;
     if (isIsolate) {
       id = await ResourceActions.myAction(data);
     } else {
       id = await GetIt.I<GlobalIsolate>().send<int?>(IsolateRequestType.myAction, input: data);
     }
     return id != null ? Database.myBox.get(id) : null;
   }
   ```

5. **Call from service layer** (never from UI directly):
   ```dart
   final result = await ResourceInterface.myAction(someId: id);
   ```

---

## 3. Add a New Socket Event Handler

**Files to touch (in order):**

1. **`lib/services/network/socket_service.dart`** — Find the `_handleSocketEvent` method (or equivalent dispatch). Add a case for the new event type string.

2. **Handler logic** — Either inline in the socket handler or delegate to a service method. If it triggers a DB write, call via an interface:
   ```dart
   case 'my-new-event':
     final chat = await ChatInterface.saveChat(guid: data['chatGuid'], ...);
     if (chat != null) ChatsSvc.updateChat(chat);
     break;
   ```

3. **If the event requires UI notification** — Emit via `EventDispatcherSvc`:
   ```dart
   EventDispatcherSvc.emit("my-new-event", data);
   ```

4. **Widget subscription** (if needed) — In `initState`:
   ```dart
   _sub = EventDispatcherSvc.stream.listen((e) {
     if (e.item1 == "my-new-event") setState(() { ... });
   });
   ```
   Cancel in `dispose`.

---

## 4. Add a New Chat or Message State Field

**Files to touch (in order):**

1. **`lib/database/io/chat.dart`** (or `message.dart`) — Add the field to the entity class. Run `dart run build_runner build` after.

2. **`lib/app/state/chat_state.dart`** (or `message_state.dart`) — Add the `Rx*` field, initialize it in the constructor, and add an `updateXxxInternal()` method.

3. **`lib/services/ui/chat/chats_service.dart`** (or `messages_service.dart`) — Add the service method that performs the DB write and then calls `updateXxxInternal()`:
   ```dart
   Future<void> setMyField(String chatGuid, bool value) async {
     await ChatInterface.saveChat(guid: chatGuid, myField: value, ...);
     getChatState(chatGuid)?.updateMyFieldInternal(value);
   }
   ```

4. **UI widget** — Wrap the reading widget in `Obx()`:
   ```dart
   Obx(() => Text(chatState.myField.value.toString()))
   ```

---

## 5. Add a New Message Renderer / Attachment Type

**Files to touch (in order):**

1. **`lib/app/layouts/conversation_view/widgets/message/attachment/`** or **`interactive/`** — Create a new small widget file for the renderer. Follow the single-responsibility rule; keep it under ~300 lines.

2. **`lib/app/layouts/conversation_view/widgets/message/parts/`** — If this is a new message part type, add dispatch logic in the part router to render your new widget.

3. **`lib/app/layouts/conversation_view/widgets/message/attachment/attachment_holder.dart`** (or equivalent attachment dispatcher) — Add a case to route to the new renderer based on MIME type or attachment type.

4. **Test** by running the app on the target platform and sending/receiving the new attachment type.

---

## 6. Add a New Screen / Page

**Files to touch (in order):**

1. **Create the page widget** in the appropriate `lib/app/layouts/<area>/pages/` directory. If it supports multiple skins, use `ThemeSwitcher` and create skin-specific variants.

2. **Register navigation** — Add a route or use `NavigationSvc.push(context, MyNewPage())` from the calling widget.

3. **If it needs a controller** — Create a `StatefulController` subclass, use `CustomStateful<MyController>` + `CustomState<MyPage, MyController, MyController>` as base classes.

4. **If it has settings** — Follow recipe #1 for any new preference fields.

---

## 7. Add a New Keyboard Shortcut (Desktop)

**Files to touch (in order):**

1. **`lib/services/backend_ui_interop/intents.dart`** — Add an `Intent` subclass and an `Action` subclass that performs the operation.

2. **Register the binding** — In the widget that should handle the shortcut, add to the `Shortcuts` widget map:
   ```dart
   LogicalKeySet(LogicalKeyboardKey.keyN): const MyNewIntent(),
   ```
   And add the action to the `Actions` widget map:
   ```dart
   MyNewIntent: MyNewAction(context),
   ```

---

## 8. Run Lint / Code Generation

```bash
# Auto-fix common lint issues
bash scripts/dart-fix-common-issues.sh

# Regenerate ObjectBox after editing @Entity classes
dart run build_runner build
```
