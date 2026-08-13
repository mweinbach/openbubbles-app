# helpers/network/ — Network Utility Functions

Pure helpers for network operations. No UI dependencies.

## File Routing

| File | What's inside |
|------|---------------|
| `network_helpers.dart` | `sanitizeServerAddress(url)` — validates and normalizes server URLs (detects ngrok/Cloudflare tunnels, ensures correct scheme); `getOrCreateUniqueId()` — persistent install ID; `getDeviceName()` — device name for server registration |
| `network_error_handler.dart` | `handleSendError(error, message)` — classifies `DioException`/HTTP errors into timeout vs. connection failure, updates `message.guid` with error prefix for UI display |
| `metadata_helper.dart` | `MetadataHelper.fetchMetadata(messageGuid, url)` — fetches and parses Open Graph/HTML metadata for URL previews; results cached in memory by message GUID |
| `network_tasks.dart` | `onConnect()` — called when network becomes available; triggers localhost detection, incremental sync, and socket reconnection |

## Key Usage Notes

**Server URL normalization** — always pass user-entered server addresses through `sanitizeServerAddress()` before storing or connecting. It handles missing schemes, trailing slashes, and known tunnel providers.

**Send error classification** — in error handlers for outgoing messages, use `handleSendError()` rather than inspecting `DioException` directly. It returns an updated `Message` with the correct error code set and the GUID prefixed with `"error-"` so the UI shows the failure state.

**URL preview metadata** — call `MetadataHelper.fetchMetadata(messageGuid, url)` to get link preview data. Results are cached; repeated calls with the same GUID return the cached value without a network hit.

**Network reconnect** — `onConnect()` in `network_tasks.dart` is the entry point wired to connectivity change events. Don't call sync or socket logic directly from connectivity listeners; call `onConnect()` instead.
