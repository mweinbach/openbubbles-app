# services/network/firebase/ — Firebase Services

Firebase integration for push notifications and real-time database fallback.

## Files

| File | Purpose |
|------|---------|
| `cloud_messaging_service.dart` | Firebase Cloud Messaging (FCM) — receives push tokens, handles background message delivery when socket is disconnected |
| `firebase_database_service.dart` | Firebase Realtime Database — optional relay channel for receiving messages when direct socket is unavailable (e.g., behind NAT) |

## When These Run
FCM is only active on Android and iOS. Desktop uses the direct WebSocket connection and does not use Firebase.

Guard with `if (kIsWeb || kIsDesktop) return;` before any FCM calls.

## Token Flow
1. `cloud_messaging_service.dart` obtains the FCM token on init
2. Token is sent to the server via `HttpService.setFCMClient()`
3. Server pushes messages via FCM when the socket is disconnected

## Related
- HTTP endpoint for token registration: `lib/services/network/http_service.dart` → `setFCMClient()`
- FCM data entity: `lib/database/io/fcm_data.dart`
- Android background handler: `lib/services/backend/java_dart_interop/background_isolate.dart`
- Push provider abstraction: `lib/services/ui/unifiedpush.dart`
