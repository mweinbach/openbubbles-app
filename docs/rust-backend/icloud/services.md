# iCloud services beyond messages

Part of the [Rust backend reference](../README.md). Related:
[history sync](history-sync.md), [state files](../foundations/state.md).

All live on `NativePushState` and require `icloud_services` (sign-in); several also
require the keychain clique. `NotReady` errors mean exactly that precondition.

## iCloud Passwords / vault

`syncPasswords()` (pull Passwords/Wi-Fi/credit-card zones + refresh groups, then push
Wi-Fi networks to the boot callback), `listPasswords(kind: Password|Code|Passkey|Wifi)`,
`revealPassword(id, kind)` (TOTP codes generate + expiry), `createPassword`,
`deletePassword` (removes credential + paired metadata; code-delete keeps the
password), `addPasswordTotp(site, username, setupString)`. Groups:
`listPasswordGroups`, `createPasswordGroup`, `renamePasswordGroup`,
`deletePasswordGroup` (delete-if-owner / leave-if-shared), invites:
`listPasswordGroupInvites`, `acceptPasswordGroupInvite`, `decline…`,
`invitePasswordGroupMember` (validates the handle can receive invitations; owner-only),
`removePasswordGroupMember`. Legacy insert hooks (`keychainPasswordInsert`,
`keychainPasskeyInsert`, `getSiteConfig`) remain on the state object for the
autofill/credential service surfaces.

## Shared Albums

`listSharedAlbums(refresh)`, `acceptSharedAlbum(id)` / `acceptSharedAlbumToken(token)`,
`setSharedAlbumSync(id, folder?)` (enable/stop local sync), `syncSharedAlbums()`,
`listSharedAlbumAssets(id)`.

## Find My

- Devices: `getDevices()` (cached; client created on first call), `refreshDevices()`.
- Friends: `getFollowing()` / `refreshFollowing()` via the fmfd daemon.
- Items: `getBeaconItems()` (syncs positions), `getCachedBeaconItems()`,
  `acceptBeaconShare(shareId)` (from `BeaconShared` pushes), `deleteBeaconShare`,
  `updateBeaconName`.
- There is no "play sound" — not implemented upstream.

## FaceTime

`ftSessions()` (active + known sessions for caller resolution), `getFtLink(usage)`,
`rotateIncomingLinks()`, `startFacetimeCall(uuid, handle, participants)` (validates
targets, reserves + rotates the link atomically, returns it), `createFacetime`,
`cancelFacetime(guid)`, `declineFacetime(guid)`, `approveLetMeIn(...)` (knock-to-join).
Incoming `UFtMessage` variants (Ring/Decline/JoinEvent/AddMembers/RemoveMembers/
LeaveEvent/LinkChanged/RespondedElsewhere/LetMeInRequest) arrive through the push loop
and are routed by `FaceTimeDispatch` before message ingest.

## StatusKit

`publishStatus(guid?)` publishes presence (null = active). `StatusUpdate` pushes carry
`user/mode/allowed`.

## Profiles and contact posters

`fetchProfile(profileJson)` resolves a `ShareProfile`/`UpdateProfile` message JSON to
the sender's shared name/avatar/poster (`UNicknameRecord`). `setProfile(name, first,
last, image?, poster?, existingJson?)` publishes this account's profile and returns the
new `ShareProfileMessage` JSON — persist it and `sendProfile(...)` it into
conversations. Posters parse/render through opaque objects:
`parsePoster(zipBytes)` → `UTranscriptPoster` (chat wallpaper: `watch()` background
bytes, `kind()`, `titleLuminance()`, `photoFiles(i)`), `parseCallPoster(UPosterRecord)`
→ `UCallPoster` (`textMetadata()`, `lowResImage()`, …); both save/restore as binary
plists (`save`/`restore*Save`).

## Contacts and misc

`getContactsHeaders()` mints short-lived CardDAV headers (family auth token + mme
token; never the password) for the contacts sync. `getQuotaInfo`-equivalent lives
behind `TokenProvider::get_storage_info` in Rust (not yet a UniFFI export).
