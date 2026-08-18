# What's new?

Below are the last few OpenBubbles App release changelogs

## v2.3.4

### Enhancements

- A performance overhaul of the messaging engine. Sending is dramatically
  faster: the engine saves its contact-key cache once per send in a compact
  format instead of rewriting a large file for every recipient, runs on
  multiple threads so sends, receipts, and syncs no longer queue behind one
  another, and stops writing thousands of internal trace lines per message.
- Sends and attachment transfers now suspend instead of tying up app
  threads, and downloads are capped at four at a time — several large
  attachments can move at once without slowing conversations, and a
  media-heavy chat can no longer starve the rest of the app.
- The chat list and transcripts redraw only when something visible actually
  changed, and contact names are cached instead of rescanned on every
  update, so history sync and busy group chats stay smooth.
- The app reaches its first frame faster: the native engine now loads off
  the main thread.

### Fixes

- An incoming contact profile update (name or poster) no longer holds up
  the messages behind it while its network fetch completes.
- Incoming-message journal writes moved off the engine's receive loop, so
  bursts of messages land without stalling the connection.

## v2.3.3

### Enhancements

- Conversations now include the remaining native message tools: richer
  tapback and reply actions, forwarding, editing and unsending, bookmarks,
  reminder controls, spam reporting, and Recently Deleted recovery.
- The composer now supports mentions, subject lines, camera capture,
  location sharing, and Android's share-to-OpenBubbles flow. Live Photos
  and supported iMessage app balloons also render in the transcript.
- Settings now exposes iCloud Passwords and Shared Albums, plus profile
  sharing, Focus status, and expanded connection diagnostics.

### Fixes

- Native libraries now meet Android's 16 KB page-size requirements. The
  recovered OpenBubbles compatibility bridge is aligned for 16 KB pages
  and the JNA dispatcher has been updated, removing the compatibility
  warning on newer devices.
- Restoring a backup can no longer reopen the app against a store that is
  still shutting down, and outgoing group messages stop showing Sending…
  as soon as the first recipient acknowledges them.

## v2.3.2

### Enhancements

- Image, video, and PDF attachments now show real previews in the
  transcript instead of a filename tile. Videos keep a play control, and
  a caption plus a rich-link preview stay together as one message.
- Chat-list snippets name the person who reacted or wrote in a group,
  so those rows read like the conversation they belong to.
- Foldable list-detail stays usable when the window is short, and
  picking someone in new-chat search clears the query so the next
  recipient starts clean.

### Fixes

- Chat wallpapers set on another Apple device now come down from
  Messages in iCloud. Incremental history had already walked past the
  type-138 background record and never retried it, and photo posters
  often leave the watch image empty — those used to look like a cleared
  wallpaper. History sync now queries those records on their own,
  rewinds the message zone once if needed, and draws the photo
  layer-stack image when the watch payload is blank.
- URL punctuation no longer sticks to a following word, handle
  detection is tighter, video play controls stay reachable, and
  video previews expose the right accessibility label.

## v2.3.1

### Fixes

- Chat backgrounds set on another Apple device show again after the
  native upgrade. The Flutter client stored those wallpapers as a poster
  archive (a folder plus a misnamed .jpg plist), and the native app
  treated a missing regular image file as "no background." Those posters
  are read again — the watch image or decoded photo layer — and rewritten
  to a real picture the conversation can draw.
- Photos and videos from iCloud message history download again. Large
  attachments were dying in Rust when Apple's Ford keying blob did not
  put the chunk keys at the field the decoder expected, which aborted
  the transfer with no useful error. The decoder now walks wrapped, bare,
  and nested Ford layouts and returns a download error instead of
  crashing the thread.
- Failed photo and video sends no longer look successful. The local echo
  of an outgoing attachment was treated as Apple's ACK, so a timed-out
  or undeliverable send stayed a silent success. The bubble now stays on
  Sending… until Apple confirms, exhausted deliveries mark Send failed,
  and that in-flight label stays visible on media rows (not only your
  latest message).

## v2.3.0

### Enhancements

- Tablets and foldables now use a real two-pane messaging layout. Unfolded
  foldables and portrait tablets show the conversation list beside the
  open chat, with chat details as a third pane on extra-wide screens. The
  panes sit flush with no gutter so the list and transcript read as one
  surface, and a book-posture hinge keeps Settings split around the crease.
- Incoming FaceTime and the QR pairing scanner stay off a tabletop hinge:
  call controls and the scanner sit below the fold so they remain usable
  when the device is tented.
- Search is now adaptive: phones still get a full-screen search bar, while
  wide windows dock search in the detail pane so the conversation list
  stays visible.
- The chat list gets out of the way while you scroll — the top bar and
  new-chat button hide on the way down and return when you scroll up.
  Pinned conversations also size their columns to the available width.
- Incoming FaceTime calls publish as conversation notifications, and
  Settings can send you to Android's full-screen incoming-call permission
  when that grant is missing.

### Fixes

- Incoming FaceTime calls ring again. The native app never created the
  incoming-call notification channel the retired Flutter client used to,
  so Android 8+ silently dropped every incoming ring on a fresh install.
  The channel is now registered at service startup — ringtone, vibration,
  high importance — so calls notify even before the first one arrives.
- QR pairing no longer shows a black camera preview. If the back camera is
  unavailable the scanner falls through to the front camera, then any
  remaining camera, instead of getting stuck.
- Binding the QR camera or delivering a scanned payload can no longer
  crash the login sheet.

## v2.2.3

### Fixes

- Clearing a large backlog of waiting messages no longer rewrites the
  whole delivery journal between every message. The journal now compacts
  itself only once its dead records outnumber the live ones (and once
  more when the queue empties), so a backlog drains in seconds instead of
  roughly one message per second.

## v2.2.2

### Fixes

- Messages stopped arriving after a contact set a chat background: the
  wallpaper payload could not be decoded, which wedged the delivery journal
  and blocked every message queued behind it. Wallpapers that omit the
  fields Apple's newer payloads leave out now parse, a background that
  cannot apply no longer fails message delivery, and journal entries that
  still fail after three attempts are dropped instead of retrying forever.

## v2.2.1

### Fixes

- Fixed a crash loop introduced in 2.2.0 when media auto-download inspected
  an attachment whose stored size was missing.

## v2.2.0

### Enhancements

- Voice messages now play right in the conversation. Audio messages show an
  expressive inline player — a big play/pause button, a seekable wave that
  flattens while paused, and the recording's length — instead of opening as
  a file, for memos you send and receive alike. Only one memo plays at a
  time, and it stops when you leave the conversation.
- Recording is now review-first: tap the new stop button mid-take and the
  recording parks in the composer as a playable draft, so you can listen
  back and type a caption before sending it all as one message. (Tapping
  send while recording still sends the take immediately.)
- Photos, videos, and voice memos now download themselves. Incoming media
  up to the size you pick in Settings → Messaging → Auto-download media
  arrives on its own — 10 MB by default, adjustable up to unlimited — and
  anything bigger still shows a download button.
- Per-conversation send-from address: long-press a chat and choose
  Send from… to pin it to one of your registered addresses (or back to the
  app default). Your default sending address now also wins over the address
  a conversation happened to arrive on.
- Group chats anchor each sender's avatar beside the bottom bubble of their
  run, the way iOS does.

### Fixes

- Conversation time separators land above the first message of each cluster
  again, following the Apple Messages cadence: a new calendar day or an
  hour-plus quiet gap starts a fresh timestamped cluster.
- iCloud chat wallpapers sync again: backgrounds set from another device now
  resolve their conversation correctly, a cleared background applies as a
  removal, and one broken wallpaper record can no longer wedge
  message-history sync (which also stalled new messages for some chats).

## v2.1.2

### Enhancements

- New dedicated search page: search chats, people, messages, and links from
  one field. Results are grouped into sections with the matching text
  highlighted — tapping a chat or message opens the conversation, a person
  opens (or starts) your 1:1 chat, and a link opens right in the browser.
- Voice messages: the composer's new + menu offers photos/videos, any file,
  and audio messages. Recording shows a live level meter and timer right in
  the composer, and the send button stops and sends the take — typed text
  rides along as the caption.
- The send button is now a circle beside the text box that springs to life
  when there's something to send and morphs shape while pressed. The text
  box grows to three lines as you type, then scrolls, and the keyboard keeps
  a normal return key for multiline drafts.
- The new-chat button is an expressive cookie shape that morphs into a
  circle when pressed.
- Conversation details (shared photos, contact info, Find My location) now
  preload while you're viewing a chat, so the details pane opens instantly
  instead of loading each section on tap.

### Fixes

- The composer's placeholder text and buttons are properly centered.
- The chat list's inline search field no longer overlaps pinned
  conversations — search now lives on its own page.

## v2.1.1

### Enhancements

- Sending a message now feels immediate. Text, iMessage attachments, SMS, and
  MMS are saved to the conversation locally before network or modem delivery
  continues in the background, so the composer no longer clears into an empty
  gap while waiting for database or transport state.
- The composer only clears after the outgoing row is visible, and it will not
  overwrite text, attachments, reply state, or edits started after Send was
  tapped. Duplicate send taps are blocked while local staging is in progress.
- Edits, tapbacks, unsends, and stickers now update the conversation
  optimistically. Failed operations roll back the temporary UI state and show
  an error instead of leaving the conversation looking stuck.
- Photos, videos, and files can be staged as removable draft thumbnails.
  Multiple selections plus an optional caption are sent together as one
  iMessage or carrier MMS, with local previews and combined upload progress.
- Reply threads can be opened in a focused conversation pane. Replies retain
  the selected message part, stay targeted to the thread while it is open, and
  quote previews align with the side of the reply bubble like iMessage.
- Warmed conversations now render their cached messages and initial draft on
  the first UI state. Opening a recently visible chat no longer waits for a
  second database query before showing its transcript.

### Fixes

- Prevents outgoing text and attachment drafts from briefly disappearing
  before their sending bubbles appear.
- Prevents a slower send completion from erasing a newer draft or newly added
  attachment.
- Scroll-to-latest now waits until the locally staged outgoing row has actually
  reached the transcript, avoiding premature or ineffective scroll attempts.
- Attachment message rows and all attachment metadata are committed in one
  transaction, eliminating caption-only or empty bubbles during staging.
- Prepared attachment files move directly into canonical storage with a copy
  fallback; failed database staging cleans up orphaned files.
- Upload progress is overlaid onto existing message items without repeatedly
  rebuilding the full ObjectBox transcript on every progress tick.
- SMS and MMS routing uses the conversation metadata already loaded by the UI.
  Pre-staging failures preserve the draft, while failures after staging leave a
  visible failed bubble that can be diagnosed instead of silently restoring a
  duplicate draft.
- Obsolete viewport-prefetch jobs are cancelled, and generation/epoch checks
  prevent stale warm-cache work from repopulating chats that were evicted or
  replaced.
- Message, attachment, and contact database changes invalidate warmed
  transcripts consistently. Short conversations no longer refetch forever
  because requested cache capacity is tracked separately from item count.
- Loading older messages updates the warm snapshot immediately, and duplicate
  transcript-prime calls from navigation and notification entry points have
  been removed.
- Newly arriving messages are marked read while their conversation remains
  open, and matching notifications are dismissed with the focused thread.

## v2.1.0

### Settings and updates

- Settings is reorganized into clear sections with icons, visible toggles
  and actions, and an at-a-glance connection status.
- New update center (Settings → About → App updates): see the current
  version, when the app last checked, and read the full release notes before
  installing. Checks run twice a day in the background and when you open the
  app, and a notification arrives when an update is ready.
- Choose how much message history downloads from iCloud (Settings → iCloud →
  History download limit).

### Conversations

- Long-press chats to select several at once, then archive or delete them;
  archived chats are managed from Settings → Messaging.
- Contact photos show on one-to-one chats, and contact sheets merge a
  person's iMessage addresses into one identity with Find My states.
- Chats open faster: visible conversations prefetch their recent messages.
- Chat backgrounds set from other devices now apply from the live push, not
  only after a history sync.
- Slide a bubble toward the start edge to reply inline.
- Smoother navigation transitions and predictive back.

### Fixes

- Notification replies sent right after a cold start now go through.
- SMS messages are saved in the system store when OpenBubbles is the default
  SMS app.
- Notification history shows contact names instead of phone numbers.
- Group and contact images sync reliably from iCloud, and photo orientation
  metadata is respected.
- "Skip this version" in the updater now actually hides that update.
- Cancelled read receipts no longer mark messages as read.
- Selecting a recipient dismisses the contact search in a new chat.

## v2.0.0

### Native client

- The app is rebuilt as a native Kotlin + Rust client: no Dart/Flutter runtime,
  same application id, in-place upgrade over the previous client with chats
  and attachments preserved.
- Direct Apple messaging without a Mac server: provisioning via a self-hosted
  OABS payload, live APNs push or 15-minute battery-saver polling.
- Full chat features: reactions/tapbacks, replies, edits, unsend, group
  controls, pinned/muted/archived chats, notifications with reply and
  mark-read actions.
- SIM SMS/MMS with default-SMS role, carrier MMS attachments, and iCloud
  Keychain credential provider and autofill services.
- FaceTime calls, Find My, shared media, and chat backgrounds.

### Self-updating

- In-app updates published through GitHub Releases: the app checks daily and
  on open, downloads and SHA-256-verifies the update, and installs it with
  one tap (no Play Store needed).
- CI builds and signs each release automatically on push to main.

## v1.15.0

### Enhancements

- Support for the Unified Push protocol (thanks @Garland-g)
- New `Notification Providers` settings page
    - You'll be able to manage all of the different providers for notifications (i.e. Firebase, Background Service, or Unified Push)
    - Better tools to manage your Firebase configuration
- Editing a message will be disabled for messages older than 15 minutes
- Adds the ability for the app to remember the reply-state for chats (thanks @cameronaaron)
- Adds troubleshooting tool to clear the last opened chat "state"
    - This is useful for users who run into the bug where the app will get stuck opening the same chat
- Adds support for setting custom headers during the setup process
- Adds support for using custom headers when connecting to the server's websocket via the background service

### Fixes

- Fixes app crash when background service is enabled and your password includes a `%` in it
- Fixes issue sending links on macOS Sonoma+
- Fixes issue where the app would not exit from the chat list, when using the Material or Samsung themes
- Improved background service error handling
- Fixes issue where Firebase tokens would not be revoked when resetting the app.
- Fixes issue loading FindMy devices from the server

## v1.14.0

This update brings a ton of QOL improvements and bug fixes.

### Big Stuff

- BlueBubbles can now run as a true background service
- Settings redesign
- Ability to re-order message details context menu

### Improvements

- Tons of UI tweaks for iOS, Material, and Samsung
- Slightly new send animation
- New overflow menu for iOS
- Improvements to how smooth the app runs
- Lowers average battery utilization by fixing some possible leaks
- Audio transcripts (when available)
- Video player UX improvements

### Bug Fixes

- Fixes issues where the unread indicators would not update in a timely manner
- Fixes issue causing duplicate images in your gallery due to HEIC images
- Fixes issue where downloading an original video would cause the app to crash on Samsung devices
- Fixes FCM registration issue where your app would try to re-register itself using a new ID
- Fixes issue with the Tasker Intent being incorrect
- Fixes issue using the universal back button when viewing archived chas
- Fixes issue where the date picker would not close properly when time selection was disabled
- Fixes issue where selecting a group chat in the new chat creator would append participants rather than replace the existing selection
- Fixes rendering issues on a handful of pages
- Fixes issues displaying URL previews
- Fixes issues sharing to an already open chat
- Fixes issue showing digital touch messages in the message view
- Fixes issue where downloading a live photo would crash the app
- Fixes issue where send/receive sounds would be backed-up causing a missing file path issue when restored
- Fixes issue where the FindMy page's initial location refresh may not update locations properly

### Desktop Specific

- Fixes issue where the system tray icon would not display properly for Flatpak installs
- Improves spellcheck
- Adds emoji picker
- Fixes issue launching from startup for Microsoft Store installs
- Escape key now closes the emoji picker

### Developer Specific

- Complete refactor of startup logic
- Unified logging across the codebase
- Flutter upgrade to v3.24.4
- Tons of dependency upgrades
- Adds indexes to the ObjectBox database to improve read speeds

## v1.13.3

This is a hotfix release

### Bug Fixes

- Fixes issue with app unnecessarily re-registering device under a different FCM ID

## v1.13.2

This is a hotfix release, fixing some issues introduced in v1.13.1

### Bug Fixes

- Fixes crash when running BlueBubbles as a Foreground Service on Android 14
- Fixes crash when using the photo picker on Android 10 and older

## v1.13.1

This update includes a couple of new minor features as well as a bunch of QOL enhancements and bug fixes.

### Enhancements

- Search improvements
    - You can now filter based on chat, sender, and date
- Updates iOS emoji pack to iOS 17.4
- App stays connected when "inactive" but not necessarily hidden
- Hiding the full screen image viewer controls will now also hide the app bar
- Adds light haptic feedback when sending a message
- App startup time is now slightly quicker
- Location widgets now show the Apple Maps preview
- Adds ability to switch linked Google Firebase projects

### Bug Fixes

- Fixes issue delivering background messages (i.e. replies from notifications)
- Fixes issue where an event dispatched to tasker would have the wrong intent
- Fixes issue where a custom landing page for the server would break the detect localhost feature
- Fixes issue where the keyboard would be dismissed when trying to change GBoard languages
- Fixes issue where non-US phone numbers would be formatted incorrectly
- Fixes issue causing the device ID for the app to change when your phone updated
- Fixes issue where a new chat would not show up in the chat list until a full app restart (sometimes two)
- Fixes issue where the search would return case-sensitive results from the server. The search is now case-insensitive
- Fixes issue where the app would hang on startup due to a failed network request
- Fixes issue with infinite "Server Password" popups when connecting to your Google Account

### Developer Notes

- Increased target SDK to 34
- Upgraded a ton of dependencies
- iOS emoji pack now always links to the latest release
    - This is so we don't need to update the app to push emoji font updates

## v1.13.0

This update sgnificantly overhauls the underlying Java code for the Android app, bringing better stability, fixing bugs, and more features.

### The Big Stuff

**New Stuff**

- Android backend is completely rewritten from Java to Kotlin, enabling some of the features and bug fixes in this list
- Notification when phone number deregisters

**Important Bug Fixes**

- Replying from the notification shade or in-car via Android Auto should now send much more reliably
- Firebase authentication detects if Google Services are available to avoid crashes on de-googled ROMs
- (Mostly) Fixes issue where the incorrect chat will be opened when opening from a notification

### The Nitty Gritty

#### Enhancements

- Replying from a notification will now confirm the reply only once it has sent from the client side (otherwise the loading animation still shows)
- Media colors now generates a Material You theme based on the album art itself
- "Open In Browser" now opens links in Android's Custom Tabs, which supports all default browsers, not just Chrome
- Improved Kotlin worker process for processing incoming items when the app is backgrounded or closed - Dart VM should be correctly cleaned up and all processes should be killed which improves battery life
- Changed FindMy to open exact coordinates in Maps app rather than the address
- FindMy Friends shows last location update
- FindMy Friends shows location status
- Faster FindMy load and refresh
- Added tooltip to manual mark unread/read button to make it more clear
- Allow adding newlines in text when creating a scheduled message
- Notification when phone number deregisters
- New profile screen to view/manage all iMessage account related tasks

#### Fixes

- Fixed issues with opening a contact's page or creating a new contact
- Creating a new contact from an unknown number allows you to add the number to an existing contact first
- Firebase authentication detects if Google Services are available to avoid crashes on de-googled ROMs
- Fixed quick reply actions not consistently showing in notifications
- Fixed legacy URL preview titles showing just "www"
- Fixed URL previews too condensed in tablet mode
- Fixed app not accepting URLs with port in manual entry
- Fixed send and receive sound volume not following the preference set in settings
- Fixed chat list not loading on Windows if Secure App is enabled and initial authentication is canceled.
- Fixed custom themes with long names not uploading to server
- Fixed issue displaying URL previews

### For Developers

- Upgraded to Flutter 3.19
- Updated dependencies

## v1.12.7

This update brings a handful of bug fixes & improvements, as well as some experimental features around better supporting FaceTime (Monterey+)!

### Changes

- Implements (experimental) ability to answer FaceTime calls
    - **This requires macOS Monterey+ and the Private API to be enabled for FaceTime.**
    - **This also requires the BlueBubbles Server v1.9.2 update**
- Fixes some issues parsing mentions in messages
- Adds button to open a FindMy Friends location in Google Maps
- Adds notice when the Private API is enabled on the server, but not on the client side
- Fixes to FaceTime call notifications

## v1.12.6

This is a hotfix update bringing bug fixes to recent issues,

### Changes

- Fixes issue with detecting and showing FaceTime notifications.
- Fixes issue where name would appear as "App Killer Manager" on French devices.
- Fixes issue where images would be pixelated when zooming in.
- Removes blurred background for message popups when on High Performance Mode.
- Fixes some issues with Firebase causing app crashes.
- Adds `ngrok-skip-browser-warning` header and custom User-Agent to fix Ngrok Tunnel compatibility issues.
- Fixes FindMy Friends issues for tablets and large-screen devices

## v1.12.5

This update fixes bugs (especially on Desktop) and brings some of the latest server's features to the client apps.

### The Big Stuff
**New Stuff**
- Added ability to check if an address is iMessage capable in the chat creator
- Brand new incoming FaceTime notification (aaaaaand maybe some extra new features if you're brave enough to try them)
- Added FindMy friends & redesigned FindMy screen

**Important Bug Fixes**
- Reworked "jump to last unread" logic to prevent lagging/freezing chats
- Fixed text field losing focus if mouse moved outside [Desktop]
- Fixed gesture interference preventing moving cursor when editing a message

### Enhancements

- Added autofill hints for password managers
- Improved send/receive sound UI & added volume setting
- Added ability to check if an address is iMessage capable in the chat creator
- Disabled swipe to reply when editing a message (gesture interference)
- Reworked "jump to last unread" logic to prevent lagging/freezing chats
- Added ability to jump to a message when tapping on it from the reply thread view
- Added better clarity to group member count text in conversation details
- "Always show avatar" now functions as expected [Material skin]
- Chat avatar now shows in conversation header [Material skin]
- Message content detection prioritizes the longest detected string in the message
- Improved Google Sign In UI
- Added ability to schedule messages on an hourly basis (Don't abuse this!)
- Added audio player seek bar [Desktop]
- Added ability to change user color even if colorful avatar is off
- Check if chat exists on server before creating a chat, in case it exists on the app but not on the server
- Added ability to sign in via Google in connection settings
- Ctrl + tab switches to the next chat even if the text field is focused [Desktop]
- Brand new FaceTime notification (aaaaaand maybe some extra new features if you're brave enough to try them)
- Added FindMy friends & redesigned FindMy screen
- Refocus main text field after editing a message
- Added up arrow shortcut setting to edit the last sent message

### Fixes

- Added better error handling to localhost detection
- Fixed initial conversation page not reacting to window effect change without a restart [Windows]
- Dispose video players properly [Desktop]
- Fixed non-functional send/receive sound [Desktop]
- Fixed text field losing focus if mouse moved outside [Desktop]
- Fixed issue where the app searches a chat called "minimized" when starting the app minimized [Desktop]
- Fixed text field focus when iMessage replying
- Fixed color emoji [Web]
- Fixed a few issues with displaying "Someone" (not all are fixed) [Web]
- Fixed issues loading settings backups
- Fixed window sizing with multiple monitors [Desktop]
- Fixed issues with window not coming to front when clicking a notification [Desktop]
- Fixed crash if Firestore collection is null [Android]
- Fixed right click not functioning on conversation tile [Desktop, Material / Samsung skin]
- Fixed video player controls [Desktop]
- Fixed whitespace getting saved as message drafts
- Fixed gesture interference preventing moving cursor when editing a message
- Fixed status indicators not updating for delivered or read messages in the chat list
- Fixed middle click scroll reversed and glitchy [Desktop]
- Fixed auto dark mode not working when window effects enabled [Windows]
- Fixed firebase error when project / configuration changes
- Fixed shared vCards without a contact photo using your personal avatar
- Fixed transparency issues in various components with a window effect enabled [Windows]
- Prevent crashes and unsupported behavior when running as Snap [Linux]

### For Developers
- Upgraded to Flutter 3.13
- Updated dependencies
- Improved snap build process

## v1.12.4

This update fixes a few bugs and brings the client apps up to speed with the latest server release's features.

### Enhancements

- Adds private API group chat creation (MacOS 11+)
- Adds support for imessage deep links (i.e. `imessage://` links) [Desktop]
- Adds video playback and audio recording support for all platforms [Desktop]
- Adds better localhost detection with ipv4 and ipv6
- Message info summary now shows human readable dates
- Tapping a message in iOS skin will show a timestamp

### Fixes

- Fixed issue where text cursor is blinking and BlueBubbles is not the active window
- Fixed missing scrollbars
- Fixed non-FCM servers not allowing to proceed with setup
- Fixed contacts not sorted alphabetically when adding to a group chat
- Fixed colors on switches in chat details
- Fixed esc key not backing out of photo fullscreen view
- Improved applying of window effects
- Fixed issues with multiple instances on Linux
- Fixed crash when replying to a notification on Android <9
- Fixed tapback options not visible for long messages
- Improved readability of contact options in chat details
- Fixed attachments not getting cleared after sharing to the app
- Fixed keyboard glitches when editing a message

## 1.12.3

### Enhancements

- Google Sign In
- Adds support for Google Firestore setups
- Replaces `Show Smart Replies` toggle with a more universal, `Smart Suggestions` toggle to encompass other "MLKit" related features
- Adds support for sharing location on Linux [Desktop]
- Adds support for imessage deep links (i.e. `imessage://` links)
- Adds video playback support for all platforms [Desktop]
- Adds showing your live location in the FindMy maps
- Updates iOS emojis to v16.4
- Ability to generate a custom theme color scheme from an image

### Fixes

- Fixes issue where notifications may be spammed when a manual or incremental sync is completed
- Fixes issues with loading shared attachments into the chat creator screen
- Fixes issue where reactions disappear when they are edited
- Fixes issue where edited and unsent messages were not being updated in the chat list
- Fixes issue with transparency in the chat creator [Desktop]
- Fixes issue where the socket error notification would be shown prematurely
- Fixes issue where GIFs would play at a high speed (Thanks @MatthewStadter)
- Fixes issue where special characters in an attachment name would cause a download to fail (Thanks @MatthewStadter)
- Fixes issue with downloading original attachments (i.e. an heic converted to a jpeg)
- Fixes issue where the camera icon would show on desktop/web
- Fixes issue where URL previews would not load properly
- Fixes potential issue with the QRCode scanner during setup

### Upgrades

- Flutter v3.10

## 1.12.2

### Fixes

* Fixed issue where shared media would not show properly in the text field when trying to share to a contact.
* Fixed issue where the sync would get stuck on 0%

## 1.12.1

### Fixes

- Fixed issue where transparency would not be applied correctly (Desktop)
- Fixed issue with not registering the client with the server to receive notifications (Android)
- Fixed issue where marking a chat as unread via the Private API would mark it read immediately after
- Fixed issue where texts/images would not be removed from the message view after being unsent

### Other Changes

- Username set in settings is now purely cosmetic
   - Any instance of yourself will be represented by `You`
- Keyboard status should now restore when returning from a different app

## 1.12.0

### The Big Stuff

- Send Mentions (Big Sur+) by typing "@" in the text field to initiate the mention picker
- Download live photos
- Bookmark messages for later
- Tasker integration (see settings for more details)
- Revamped backup and restore page
- Support FCM-less notifications using always-open socket connection & foreground service

### The Nitty Gritty

#### New Features

- Send Mentions (Big Sur+)
- Auto apply message effects for some phrases like iMessage
- Re-added copy text selection (long press copy option)
- Download live photos
- Bookmark messages for later
- Detect when the recipient keeps an audio message
- Tasker integration
- Revamped backup and restore page
- Added avatar-only view for chat list (Desktop / Web)
- Added shortcut to restore from backup directly after initial sync
- Support FCM-less notifications using always-open socket connection & foreground service
- Support extracting flight number / tracking number / dates from messages
- Toggle to unarchive chat when receiving a new message in it
- Added ability to scroll to last read message when opening a chat
- Added ability to initiate Google Duo call from chat details
- Added ability to set a custom name and avatar for "yourself"
- Added ability to secure Desktop app with Windows security
- When refocusing the Desktop app, the last focused chat text field is refocused
- View and modify message reminders (Android)

#### Bug Fixes

- Fixed server logs fetch status not resetting on Desktop / Web
- Fixed keyboard jitter when changing conversation name
- Fixed playing some screen effects would brick other effects from playing
- Fixed some issues with emoji picker
- Fixed issue fetching user focus state in some cases
- Fixed not being able to set custom avatar color in DM chats
- Fixed handwrittten message pad would show even if color picker was canceled
- Fixed typing indicators not sending after sending a message
- Fixed clicking on notifications not bringing window to foreground on Desktop
- Fixed invisible titlebar covering hitboxes for some buttons at the top of the app
- Fixed notification activation opening additional instance on Linux
- Fixed mentions not showing on Desktop or Web
- Fixed page pop bug when in tablet mode and downloading iOS font
- Fixed some weirdness with settings dividers in a few places
- Fixed handle is not found for searched for message
- Fixed search message service would persist when opening the chat from a non-search context
- Fixed database migration bug for new installs
- Fixed conversation details fetching attachments for deleted messages
- Fixed cases where passwords with special characters were not encoded correctly
- Fixed message reminder not getting canceled when canceling the time picker
- Properly remember when a chat is closed

#### Improvements

- Applied international phone number matching fixes everywhere
- Un-delete chats when creating a new chat to the same address
- Improved read receipts to show in more cases
- Support replying and sending effects to existing chats from the new chat creator
- Removed emojis tab from Giphy
- Clear search results when changing the search type
- Hide FindMy option for users below Catalina (FindMy doesn't exist before Catalina)
- Improved API status display in server management

#### For Developers

- Upgraded dependencies, fixing a few critical security vulnerabilities


## 1.11.5

### The Big Stuff

- New Private API features!
   - Leave group chat
   - Change / remove group chat icon (Big Sur+)
   - View and save digital touch or handwritten messages (Big Sur+)
   - View recipient focus mode (Monterey+)
   - Forcefully notify your message (break other user's focus mode) (Monterey+)
- Auto-update group chat icon changes
- Display Apple Pay transaction amounts

### The Nitty Gritty

#### New Features

- New Private API features!
   - Leave group chat
   - Change / remove group chat icon (Big Sur+)
   - View and save digital touch or handwritten messages (Big Sur+)
   - View recipient focus mode (Monterey+)
   - Forcefully notify your message (break other user's focus mode) (Monterey+)
- Auto-update group chat icon changes
- Display Apple Pay transaction amounts
- Better replies rendering with extremely complex threads
- Toggle to disable scroll to bottom when sending a new message
- Support creating chats with specific service (SMS Forwarding vs iMessage)
- New setting to lock the current group chat name / icon
- Added indicator in connection settings informing that server URL has bad certificate

#### Bug Fixes

- Fixed issues with attachments occassionally not showing up until a restart of the app
- Fixed crash when sharing images from Google Messages
- Fixed send sound playing even if the chat was not active
- Fixed broken chat list if unknown senders enabled and chat has empty participants
- Fixed material progress indicator shapes in a few places
- Fixed app would allow sending images as a reply even if Private API attachment send was not enabled
- Fixed popup rendering error if text is null
- Fixed interactive message with no payload data rendering incorrectly
- Fixed app incorrectly handling participant and group events sent by the server
- Fixed app not getting mark read/unread from socket properly
- Fixed new chat not showing up in chat list until close and reopen
- Fixed "loading more messages" not going away
- Fixed new messages not showing for newly created chats
- Fixed contacts sometimes getting duplicated in chat creator

#### Improvements

- Improved rendering of very thin media
- Display empty text on messages with subject and empty text to be more consistent with Apple
- Added failsafe to fetch chat details automatically (should hopefully prevent the issues with new chats not showing up or having the rendering issues)
- Reduced the number of places from which a chat is marked read via Private API to vastly reduce unnecessary duplicate calls to perform the same action
- Incremental sync refactor for better reliability
- Bad certificate override now applies to all isolates


## 1.11.4

### Changes

- Fixes issue sending attachments if the BlueBubbles server was v1.5.3/v1.5.4, and the Private API was not enabled.
- Audio player will now stop after it completes (rather than repeat)
- Fixes issue where timestamp dividers would not appear on the Samsung theme
- Fixes issue where audio would not pause when leaving a chat or closing the app

## 1.11.3

### The Big Stuff

- QOL improvements and bug fixes from the major rewrite
- Private API Attachment sending
   - Send attachment with effect
   - Send attachment as a reply
   - Voice notes now show up as voice notes for the recipient
- A few other minor new features

### The Nitty Gritty

#### New Features

- Automatically re-upload contacts to server when contacts changes are detected
- Added ability to connect with custom headers
- Added ability to enable read receipts / typing indicators for specific chats without enabling globally
- Connection status now has two categories - REST API connection & socket connection
- Show message sent status and date if tapped on (Material / Samsung)
- Long press camera button starts video recording (iOS)
- Private API Attachment sending
   - Send attachment with effect
   - Send attachment as a reply
   - Voice notes now show up as voice notes for the recipient

#### Bug Fixes

- Fixed issues with matching contacts if phone number starts with "0" for contact
- Fixed message size in message popup when in tablet mode
- Fixed delete chat not working on iOS
- Fixed scheduled message save button not appearing until clicking into the text field
- Fixed connection error messages on setup
- Fixed attachments not showing on first load (Desktop)
- Fixed sharing to app not getting the image when both text and image shared at once
- Fixed issue where app would not clear notifications / mark read on iDevices when actively in the chat
- Fixed issues with filtering unknown senders
- Fixed retrying failed attachment send makes it disappear
- Fixed accessing message details popup would sometimes result in a gray screen
- Fixed popping manually sync messages dialog would pop the underlying page
- Fixed color of navigation bar buttons
- Fixed sending a message to existing chat via chat creator would not send (tablet mode)

#### Improvements

- Added custom renderbox to chat list on samsung theme to fix weird issues with divider lines
- Mark all as read will now fetch chats from database to accurately mark everything as read
- Scheduled message interval field will not clear itself when a bad input is entered (Desktop)
- Improved algorithm for getting initials of contacts
- Reply thread viewer will always take up the whole screen now when in tablet mode
- Added "waiting for iMessage..." indicator when sending attachment
- Updated emoji regex for unicode 15
- Improved audio player design & timestamp display
- Improved design of a few screens in the setup menu
- System titlebar can now be removed properly (Linux)
- Auto submit address in chat creator if the user did not, but is sending a message
- Group events are now parsed more correctly
- Incremental sync now uses local IP override (incremental sync can complete even if proxy is inaccessible)
- Render subjects on interactive messages or attachment messages if they dont have plaintext
- iOS emojis are used in chat titles for the chat details page
- Material theme chat list got some love to look closer to Google Messages
- Audio recordings made from the app should now sound *much* better
- Force square aspect ratio when rendering QR code
- Account for left system padding (e.g. punch hole camera) when rendering message popup

#### Re-added Features

- Confetti effect re-added (Flutter 3.7 crash is fixed)

#### For Developers

- Updated to Flutter 3.7.3
- Parts of backend updated to successfully parse new server payload type (support for encryption)

## 1.11.2

### Changes

- Fixes issue where contact info would not show when searching
- Ability to set a default email for a given handle
- Mentions are now bold (previously was the primary theme color)
- When a 502 Gateway error is hit (for Cloudflare), the request is auto-retried
- The refresh button for the FindMy devices page will actually refresh locations now
- Improved URL preview design
- Better reply generation on swipe to show timestamp
- Fixes issue where media/files were not able to be saved to the device
- Fixes the connection indicator
- Fixes issue where the re-sync handles button would run against servers < v1.5.2

## 1.11.1

### The Big Stuff

- QOL improvements and bug fixes from the major rewrite
- Upgrade to Flutter 3.7
- New method to fully fix contacts issues

### The Nitty Gritty

#### New Features
- New switch design
- Confirmation dialog when deleting chat
- New function to properly reset / fix contacts glitches
- Open chat details when tapping group name in header (Material / Samsung)
- Cancel attachment send
- New camera button on iOS skin

#### Bug Fixes
- Fixed iOS pinned chats not reacting well to divider width changes when in tablet mode
- Fixed tab/enter emoji insertion in text field
- Fixed bugs with current chat highlight on chat list when in tablet mode
- Fixed shape and color of group overflow avatar
- Fixed refresh action overlapping with back button on findmy (Samsung)
- Fixed not being able to save edits to a scheduled message in some cases
- Fixed colors in send effect picker
- Fixed some bugs when going into the message view from search
- Fixed weirdness with deleting chats
- Fixed importing VCF not working
- Fixed rare lateinitializationerror for DB store
- Fixed attachment showing "Unknown" rather than the sender when viewing fullscreen
- Fixed URL preview getting cut off if preview image is too large
- Fixed bug where attachments wouldn't populate in view after opening chat via a notification
- Fixed attachment send timing out during the send
- Fixed rendering bugs when going in and out of tablet mode (rotating phone, disabling tablet mode, etc)

#### Improvements
- Allow for tab / shift+tab to move cursor between text fields
- Made connection indicator global
- Disabled swipe left / right on findmy page
- Detect right click on send button
- Added enter to send when editing a message
- Disabled fingerprint auth on Android 9 and under (to prevent crashes)
- Improved consistency of settings tiles
- Improved typing indicator going away animation
- Improved send animation (Material / Samsung)
- Removed video overlay on replied to widget
- Improved display of unread message counter when over 100

#### Removed Features (Temporarily)
- Confetti effect removed due to a crash on Flutter 3.7

#### For Developers
- Updated targetSdkVersion & compileSdkVersion to 33 (Android 13)
- Updated Java & Dart dependencies
- Updated to Flutter 3.7 / Dart 2.19

## 1.11.0

- Full rewrite of the **entire** app
  - Backend completely redone to reduce potential for bugs and increase maintainability
  - Frontend completely redone to improve performance drastically and make the app prettier & more fun to use 
  - Some stats:
    - 100,000+ lines of code modified
    - 500+ files changed
    - 100+ issues closed
    - 6 months / hundreds of hours in the making
- iMessage parity
  - Display mentions in **bold**
  - Display unsent messages
  - Display edited messages, along with their past edits
  - Display messages with attachments or other rich content in the correct order
  - Allow reacting to individual parts of a message
  - Improved URL previews
  - Display more information for iMessage apps (e.g. Shazam, Apple Pay, YouTube, OpenTable, etc)
  - Unsend sent messages (Ventura Private API)
  - Edit sent messages (Ventura Private API)
  - Send handwritten messages
- View FindMy Devices
- Scheduled messages
- Notification for incoming FaceTimes
- Option to use localhost address for low latency when on server WiFi network
- Choose an app font from nearly 1,400 custom fonts
- Way, way, way too many other changes to count. Bug fixes, performance improvements, new features - you name it, the app got it.

### Removed Features

* Swipe actions on conversation tiles in iOS theme - use long-press for same functionality instead
* Auto-play message effects - not reliable and seamless enough for prime-time
* Reduced number of options in redacted mode

### What's Next?

* 3 letters - take a guess ;) - how else could we follow up an update as big as this one?

## 1.10.1

### Fixes & Optimizations

* Upgraded flutter to v3.3.0.
   - This update should fix the keyboard lag issue some users were experiencing.
* Fixes issue uploading attachments on BlueBubbles Web.
* Fixes issue where a temporary chat mute would not apply properly.
* Fixes issue loading texts from macOS Ventura.
   - This fixes the "Unknown Group Event" issue with macOS Ventura.
* Fixes issue where new messages wouldn't show in an open chat until re-entering the chat.
* App can no longer be flipped upside-down, unless enabled in the Settings.
* Fixes issue where message previews for reactions would always show "You", rather than the real sender.
* Fixes big emoji issue where font size would be extremely large, on some devices.
* Fixes grey advanced theming page when the music theme was enabled.

## 1.10 (Bordeaux)

### The Big Stuff

- Full rewrite of theming system to make the app as pretty as possible
- Bug fixes
- Performance improvements

### The Nitty Gritty

#### New Features
- Theming system rewrite
  - UI Components are now more consistent towards their respective skins
  - Theme colors now apply in much more places throughout the UI
  - Many more options to modify theme colors
  - Simplified customization parameters
  - Buffed Material You - it now reaches much deeper into the UI to truly transform the app towards your system theme
  - Over 85 new default themes - there's something for everyone now!
  - Added visual feedback when tapping items in settings
  - Added transparency settings on Windows Desktop
  - Dialog design has been unified across the entire app
  - Revamped conversation details design
  - Revamped fullscreen media viewer design
  - Gradient backgrounds are now supported on default themes as well
  - Old themes will be completely deactivated, but they are still viewable from the advanced theming menu
- Added wearable actions to notifications (Pebble / Fitbit / etc smartwatches)
- Added support for modifying API Timeout duration
- macOS Ventura support
- Custom emoji font on Web
- Desktop
  - Made notification actions reorderable by dragging

#### Bug Fixes
- Fixed off-center UI components in various places
- Fixed broken audio sending
- Fixed broken audio player
- Fixed app requiring Firebase on setup
  - Firebase remains required when using Ngrok / Cloudflare
- Fixed zoomed in contact photos on notifications and share sheet
- Fixed app crashing after attaching large files
- Fixed "removed" reactions not actually getting removed from the UI
- Fixed stickers not loading in
- Fixed Giphy not working on Web
- Fixed taking photos / videos from camera button in the app would sometimes be unresponsive
- Fixed some issues removing people from chats
   - This may not reflect in the UI immediately still, but a restart of the app will reflect it
- Desktop
  - Fixed link previews
  - Fixed issues with window bounds going off screen
  - Fixed / improved wonky UI elements
  - Removed ability to disable tablet mode
  - Fixed error on convo tile right click
  - Fixed globalkey errors with details popup
  - Fixed appdata migration

#### Improvements
- Asynchronous incremental sync (better performance when loading the app from background)
- Share shortcuts are now set as conversations to interact better with Android system
- Improved contact photo matching (Desktop / Web)
- Don't auto-save interactive message attachments
- Force cloudflare URLs to https
- Request storage permissions when "save sync log" is enabled
- Improved customize theme error snackbar info
- Added detection for large files (> 100mb)
- Laid the groundwork for attributedBody support (mentions) for next update
- Improved Android 12+ splash screen
- Improved performance of loading chat messages

#### For Developers
- Updated targetSdkVersion & compileSdkVersion to 32 (Android 12L)
- Updated gradle plugin
- Updated Java & Dart dependencies
