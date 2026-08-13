# Contributing to BlueBubbles

We encourage all contributions to this project! This guide will help you understand the codebase architecture and how to contribute effectively.

## Code of Conduct

* Write clean, well-commented code
* Follow [Dart documentation guidelines](https://dart.dev/guides/language/effective-dart)
* Format your code with `dart format ./ --line-length=120`
* Test your changes thoroughly before submitting

## Development Environment Setup

### Prerequisites

1. **Install Git**: [Download](https://git-scm.com/downloads)
2. **Install Java JDK**: [Download](https://www.oracle.com/java/technologies/javase-downloads.html)
3. **Install Flutter Version Manager (FVM)** - Recommended

   FVM allows you to manage multiple Flutter versions easily:

   ```bash
   dart pub global activate fvm
   ```
   
   Then install and use the Flutter version specified in the project:

   ```bash
   fvm install
   fvm use
   ```

   Alternatively, install Flutter directly: [Flutter installation guide](https://flutter.dev/docs/get-started/install)

4. **Install Android Studio**: [Download](https://developer.android.com/studio)

   - Install Flutter & Dart plugins via the Plugin Manager
   - Install Command Line Tools via SDK Manager
   - Configure an Android Virtual Device (AVD) via AVD Manager, or connect a physical Android device with USB debugging enabled

5. **Install Visual Studio Code** (Recommended): [Download](https://code.visualstudio.com/download)

   Install the following extensions:

   - Dart
   - Flutter
   - IntelliCode (optional but recommended)

### Android Device Setup

**Option 1: Android Virtual Device (AVD)**

- Open Android Studio → AVD Manager
- Create a new virtual device
- Choose a device definition (e.g., Pixel 6)
- Select a system image (API 30 or higher recommended)
- Finish setup and launch the emulator

**Option 2: Physical Android Device**

- Enable Developer Options on your device
- Enable USB Debugging
- Connect via USB
- Verify connection: `flutter devices`

## Repository Setup

### Forking and Cloning

1. Create a GitHub account if you don't have one
2. Fork the `bluebubbles-app` repository
3. Clone your forked repository:

   ```bash
   # HTTPS
   git clone https://github.com/YOUR_USERNAME/bluebubbles-app.git
   
   # SSH (recommended)
   git clone git@github.com:YOUR_USERNAME/bluebubbles-app.git
   ```

4. Set up the upstream remote:

   ```bash
   cd bluebubbles-app
   git remote add upstream git@github.com:BlueBubblesApp/bluebubbles-app.git
   ```

5. Fetch branches and pull latest changes:

   ```bash
   git fetch --all
   git pull upstream master
   ```

### Installing Dependencies

```bash
# If using FVM
fvm flutter pub get

# If using global Flutter
flutter pub get
```

## Architecture Overview

### Database: ObjectBox

BlueBubbles uses **ObjectBox** as its local database solution for efficient, fast data storage.

#### How ObjectBox Works

ObjectBox is a NoSQL object database optimized for mobile and IoT. Key features:

- **Direct object persistence**: No ORM mapping overhead
- **ACID compliance**: Transactions ensure data consistency
- **Reactive queries**: Automatic UI updates when data changes
- **Relations**: ToOne, ToMany, and backlinks for entity relationships

#### Efficient vs. Inefficient Operations

**Efficient Operations:**

- `get(id)` - Direct ID lookups are O(1)
- `getMany([ids])` - Batch retrieval by IDs
- Indexed queries (e.g., on `guid`, `dateCreated`)
- Limiting query results with `.limit(n)`
- Using `.find()` sparingly with proper conditions

**Inefficient Operations:**

- Loading all entities without limits
- Queries without indexes on frequently searched fields
- Accessing relationships outside of transactions (causes additional queries)
- Modifying data without using transactions

**Best Practice:**

```dart
// Good: Efficient batch retrieval
final messages = Database.messages.getMany(messageIds).whereType<Message>().toList();

// Bad: Querying in a loop
for (final id in messageIds) {
  final message = Database.messages.get(id); // Multiple separate queries
}
```

#### Database Models and Relationships

The main ObjectBox entities are:

**Message** (`lib/database/io/message.dart`)

- Represents an iMessage/SMS message
- Relations:
  - `ToOne<Handle>` - The sender/recipient handle
  - `ToMany<Attachment>` - Linked attachments
  - Associated messages (reactions, edits) via `associatedMessages` list

**Chat** (`lib/database/io/chat.dart`)

- Represents a conversation
- Relations:
  - `ToMany<Handle>` - Participants in the chat
  - `ToMany<Message>` - Messages in the chat
- Maintains latest message reference

**Handle** (`lib/database/io/handle.dart`)

- Represents a contact address (phone/email)
- Relations:
  - `ToMany<ContactV2>` - New contact system (backlink)
  - `ToMany<Chat>` - Chats this handle participates in
  - `ToOne<Contact>` - Legacy contact relation (**DEPRECATED** - use ContactV2)

**Attachment** (`lib/database/io/attachment.dart`)

- Represents a file attachment
- Relations:
  - `ToOne<Message>` - Parent message

**ContactV2** (`lib/database/io/contact_v2.dart`)

- New contact system (replaces deprecated Contact model)
- Relations:
  - `ToMany<Handle>` - Linked handles for this contact

**Contact** (`lib/database/io/contact.dart`) - **DEPRECATED**

- Legacy contact system - **DO NOT USE for new code**
- Use ContactV2 instead for all contact-related operations

**Important**: Always access ObjectBox relationships within a transaction context to avoid performance issues.

### Isolates System

BlueBubbles uses Dart isolates to offload heavy work from the main UI thread, ensuring smooth performance.

#### GlobalIsolate

The **GlobalIsolate** (`lib/services/isolates/global_isolate.dart`) is a long-running background thread designed for general-purpose work. Reusing this isolate is more efficient than spawning new ones repeatedly.

**Key Features:**

- Persistent background thread
- Request-response pattern with UUID tracking
- Event emission from isolate to main thread
- Automatic timeout handling
- Idle shutdown to conserve resources

#### Creating an Interface & Actions

To leverage the GlobalIsolate, you need to create:

1. **Interface** (`lib/services/backend/interfaces/`) - Handles object hydration and determines whether to call an isolate or execute directly (if already in an isolate)
2. **Actions** (`lib/services/backend/actions/`) - Contains the actual work to be executed

**Example Interface Pattern:**

```dart
// lib/services/backend/interfaces/example_interface.dart
import 'package:bluebubbles/env.dart';
import 'package:bluebubbles/services/backend/actions/example_actions.dart';
import 'package:bluebubbles/services/isolates/global_isolate.dart';
import 'package:get_it/get_it.dart';

class ExampleInterface {
  static Future<String> doWork({required String input}) async {
    final data = {'input': input};
    
    // Check if already in isolate
    if (isIsolate) {
      return await ExampleActions.doWork(data);
    } else {
      // Send to GlobalIsolate
      return await GetIt.I<GlobalIsolate>()
          .send<String>(IsolateRequestType.doWork, input: data);
    }
  }
}
```

**Example Actions:**

```dart
// lib/services/backend/actions/example_actions.dart
class ExampleActions {
  static Future<String> doWork(Map<String, dynamic> data) async {
    final input = data['input'] as String;
    // Perform heavy computation
    return "Result: $input";
  }
}
```

#### Registering with GlobalIsolate

Add your actions to the action map in `lib/services/isolates/isolate_actions.dart`:

```dart
class IsolateActons {
  static final Map<IsolateRequestType, dynamic> actions = {
    // ... existing actions
    IsolateRequestType.doWork: ExampleActions.doWork,
  };
}
```

And add the request type to `GlobalIsolate` enum (in `global_isolate.dart`):

```dart
enum IsolateRequestType {
  // ... existing types
  doWork,
}
```

#### Existing Interfaces

The following interfaces are currently available:

- **AppInterface** - App update checks
- **ServerInterface** - Server communication and updates
- **ImageInterface** - Image processing (PNG conversion, EXIF reading, GIF dimensions)
- **PrefsInterface** - Shared preferences operations
- **MessageInterface** - Message CRUD operations
- **ChatInterface** - Chat management (notifications, read/unread, transcript clearing)
- **HandleInterface** - Handle operations
- **ContactV2Interface** - Contact system operations (use this for all contact work)
- **ContactInterface** - **DEPRECATED** - Use ContactV2Interface instead
- **AttachmentInterface** - Attachment CRUD operations
- **SyncInterface** - Incremental sync operations
- **TestInterface** - Testing isolate functionality

#### Custom Isolates (Extending GlobalIsolate)

For specialized workloads, you can create a custom isolate by extending `GlobalIsolate`:

**Example: IncrementalSyncIsolate**

```dart
// lib/services/isolates/incremental_sync_isolate.dart
class IncrementalSyncIsolate extends GlobalIsolate {
  IncrementalSyncIsolate({
    super.taskTimeout = const Duration(minutes: 5),
    super.startupTimeout = const Duration(seconds: 10),
    super.idleTimeout = Duration.zero,
  });

  @override
  String get isolatePortName => 'IncrementalSyncIsolate';

  @override
  String get isolateDebugName => 'IncrementalSyncIsolate';

  @override
  Function get getIsolateEntryPoint => IncrementalSyncIsolate._syncIsolateEntryPoint;

  static Future<void> _syncIsolateEntryPoint(List<dynamic> args) async {
    await GlobalIsolate.sharedIsolateEntryPoint(
      args,
      StartupTasks.initSyncIsolateServices,  // Custom service initialization
      IsolateActons.actions,
    );
  }
}
```

Register your custom isolate in `lib/helpers/backend/startup_tasks.dart` (or wherever appropriate):

```dart
GetIt.I.registerSingleton<IncrementalSyncIsolate>(IncrementalSyncIsolate());
```

#### Isolate Notes

* Always ensure that heavy computations or blocking operations are offloaded to isolates to maintain UI responsiveness.
* Use the provided interfaces to interact with isolates rather than calling actions directly.
* When in doubt, refer to existing interfaces for patterns and best practices.
* Serialization & deserialization of complex objects is expensive; prefer passing primitive types or simple data structures.
    - Since isolates have access to the HTTP service and the database, we recommend making large data fetches or network requests within the isolate itself rather than passing large objects back and forth. You can pass IDs back to the main thread to be hydrated, which is more efficient.

### Services and Dependency Injection

BlueBubbles uses **GetIt** for dependency injection and service management.

#### Service Registration

Services are registered and initialized in `lib/helpers/backend/startup_tasks.dart`:

```dart
// Singleton registration
GetIt.I.registerSingleton<MyService>(MyService());

// Async singleton (waits for initialization)
GetIt.I.registerSingletonAsync<MyService>(() async {
  final service = MyService();
  await service.init();
  return service;
});

// Wait for service to be ready
await GetIt.I.isReady<MyService>();
```

#### Service Initialization Order

The order matters! Services are initialized in this sequence:

1. **FilesystemService** - File system operations
2. **SharedPreferencesService** - Persistent key-value storage
3. **SettingsService** - App settings management
4. **BaseLogger** - Logging infrastructure
5. **Database** - ObjectBox initialization
6. **GlobalIsolate & IncrementalSyncIsolate** - Background threads
7. **HttpService** - Network requests
8. **MethodChannelService** - Platform channel communication
9. **LifecycleService** - App lifecycle events
10. **CloudMessagingService** - Firebase Cloud Messaging
11. **ContactServiceV2** - Contact management
12. **IntentsService** - Deep link handling
13. **SyncService** - Data synchronization
14. **ThemesService** - Theme management
15. **NavigatorService** - Navigation management
16. **ChatsService** - Chat state management
17. **SocketService** - WebSocket communication
18. **NotificationsService** - Local notifications
19. **EventDispatcher** - Event bus

#### Accessing Services

```dart
import 'package:get_it/get_it.dart';

// Access a service
final settingsService = GetIt.I<SettingsService>();

// Using service shortcuts (defined in service files)
import 'package:bluebubbles/services/services.dart';

SettingsSvc.settings.redactedMode.value = true;
ChatsSvc.findChatByGuid('some-guid');
```

### Reactive UI with GetX

BlueBubbles uses **GetX** for reactive state management.

#### ChatState Pattern

Instead of using database models directly in the UI, we use **state wrappers** like `ChatState` (`lib/app/state/chat_state.dart`) to provide granular reactivity:

```dart
class ChatState {
  final Chat chat; // Underlying DB model
  
  // Observable fields
  final RxBool isPinned;
  final RxnInt pinIndex;
  final RxBool hasUnreadMessage;
  final RxnString displayName;
  final Rxn<Message> latestMessage;
  // ... more fields
}
```

**Why Use State Wrappers?**

- **Performance**: Widgets only rebuild when specific fields change
- **Granular control**: Subscribe to individual properties
- **Separation of concerns**: UI state separate from DB models

#### Using GetX in UI

**Obx Widget** - Automatically rebuilds when observed values change:

```dart
Obx(() => Text(chatState.displayName.value ?? 'Unknown'))
```

**GetX Controller** - For complex screen state:

```dart
class MyController extends GetxController {
  final RxInt counter = 0.obs;
  
  void increment() => counter.value++;
}

// In widget
final controller = Get.put(MyController());
Obx(() => Text('Count: ${controller.counter.value}'))
```

#### Best Practices

**DO:**

- Use `ChatState` or similar state wrappers for reactive UI
- Wrap only the smallest widget that needs to react in `Obx()`
- Use `.obs` for primitive types, `Rx<T>()` for objects

**DON'T:**

- Use DB models directly in reactive widgets (won't trigger rebuilds)
- Wrap large widget trees in `Obx()` - split into smaller reactive pieces
- Mutate observable values without `.value` setter

**Example:**

```dart
// ❌ Bad: Won't update UI when chat changes
Obx(() => Text(chat.displayName ?? 'Unknown'))

// ✅ Good: Reacts to ChatState changes
Obx(() => Text(chatState.displayName.value ?? 'Unknown'))
```

## Building the App

### Development Builds

Run the app in debug mode:

```bash
# If using FVM
fvm flutter run

# Standard Flutter
flutter run
```

### Production Builds

Build release APKs with flavor support:

```bash
# Beta flavor
flutter build apk --flavor=beta --release

# Production flavor
flutter build apk --flavor=prod --release

# Split per ABI (smaller file sizes)
flutter build apk --flavor=beta --release --split-per-abi
```

### Build Variants

- **beta**: Beta testing builds with Firebase Test Lab integration
- **prod**: Production builds for Google Play Store

## Code Formatting

Always format your code before committing:

```bash
dart format ./ --line-length=120
```

This project uses a max line length of **120 characters**.

## Workflow: Picking an Issue

1. Check the [issues page](https://github.com/BlueBubblesApp/bluebubbles-app/issues)
2. Filter by labels:
   - `Difficulty: Easy`, `Difficulty: Medium`, `Difficulty: Hard`
   - `good first issue` - Great for newcomers
   - `bug` - Bug fixes
   - `enhancement` - New features
3. If working on something without an issue, create one first for tracking

## Workflow: Making Changes

1. **Create a feature branch:**

   ```bash
   git checkout -b <your-name>/<feature|bug>/<short-description>
   # Example: git checkout -b john/feature/dark-mode-support
   ```

2. **Make your changes**
3. **Format your code:**

   ```bash
   dart format ./ --line-length=120
   ```

4. **Test thoroughly** on both emulator and physical device if possible
5. **Stage and commit:**

   ```bash
   git add <file>
   # or
   git add -A
   
   git commit -m "Clear, descriptive commit message"
   ```

6. **Push to your fork:**

   ```bash
   git push origin <your-branch-name>
   ```

## Workflow: Submitting a Pull Request

1. Go to your forked repository on GitHub
2. Click "Pull requests" → "New pull request"
3. Set base repository to `BlueBubblesApp/bluebubbles-app` and base branch to `development`
4. Include in your PR description:
   - **Problem**: What issue does this solve?
   - **Solution**: How did you fix it?
   - **Testing**: How did you test the changes?
   - **Screenshots**: If applicable, include before/after screenshots

5. Submit and wait for review!

## Additional Resources

- [Dart Documentation](https://dart.dev/guides)
- [Flutter Documentation](https://flutter.dev/docs)
- [ObjectBox Documentation](https://docs.objectbox.io/)
- [GetX Documentation](https://pub.dev/packages/get)

## Questions?

Join our [Discord community](https://discord.gg/hbx7EhNFjp) for help and discussions!
