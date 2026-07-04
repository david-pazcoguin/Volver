# Security Standards

## Network & Transport

- **HTTPS-only** — `network_security_config.xml` blocks all cleartext HTTP traffic
- **No `usesCleartextTraffic`** in the manifest
- **`allowBackup="false"`** — prevents ADB backup of app data

## Authentication & Session

- Firebase Auth with email/password (server-managed sessions)
- Username validation: regex `^[a-zA-Z0-9_]{3,30}$` enforced on both Login and Register
- Password minimum: 6 characters (Firebase Auth minimum); name fields capped at 50 characters

## Cloud Function (`mintSouvenir`)

- Requires Firebase Auth (`context.auth` check) with a UID mismatch check (`data.uid !== context.auth.uid`)
- Wallet address validated via `ethers.isAddress()` on both the submitted value and the Firestore-stored value
- Uses the **server-side stored wallet**, not the client-supplied one — the client cannot redirect the mint to another address
- Triple verification before any on-chain call: `allComplete == true` AND mission subcollection count meets the required total AND `souvenirMinted != true`
- Owner private key stored in Firebase Functions config/secrets — never in source
- Error responses sanitized — internal exception details only go to server logs

## Firestore Rules

- Owner-only access (UID-matched)
- Type validation on create (string lengths, required fields)
- Field-level allowlist on update via `diff().affectedKeys().hasOnly()`
- Immutable fields: `email`, `createdAt`
- Append-only missions (no updates or deletes)

See [DATA_MODEL.md](DATA_MODEL.md) for the full rules summary.

## Client Hardening

- **Button debounce** (2 s cooldown) on wallet confirm and NFT mint buttons
- **Web3j HTTP timeouts**: 15 s connect / 30 s read+write to prevent hung transactions
- Embedded wallet keys encrypted with AES-256-GCM via Android Keystore; BouncyCastle provider registered at runtime
- Handler leaks prevented: clipboard runnable held as a class field and canceled in `onDestroy()`
- Error messages never expose exception details to users

## Build & Release

- **R8 minification** enabled for release builds (`minifyEnabled true`, `shrinkResources true`)
- **ProGuard rules** strip `Log.d()` and `Log.v()` in release
- Sensitive config injected via `BuildConfig` fields from `gradle.properties` / `local.properties`
- `google-services.json` and `local.properties` are gitignored

## Smart Contract

- `onlyOwner` modifier on `adminMintTo()` and `setTokenUri()` — only the server wallet can mint or change metadata
- Server-side verification gates every mint (see Cloud Function section above)
- Minimal attack surface: no public mint, no whitelist mapping, no claim function
