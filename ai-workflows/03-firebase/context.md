# Context — Firebase (Auth, Firestore, Cloud Functions)

## Firestore Data Model

```
users/{uid}                         ← User profile document
├── username       : string         ← 3–30 chars, alphanumeric + underscore
├── firstName      : string
├── lastName       : string
├── email          : string         ← Immutable after creation
├── createdAt      : timestamp      ← Immutable after creation
├── walletAddress  : string         ← Polygon wallet address (optional)
├── allComplete    : boolean        ← True when all 5 missions done
├── whitelisted    : boolean        ← True after Cloud Function whitelists
├── whitelistedAt  : timestamp      ← When whitelisting occurred
│
└── missions/{missionId}            ← Mission completion subcollection
    ├── completed    : boolean      ← Always true (append-only)
    ├── completedAt  : timestamp    ← Server timestamp, validated in rules
    └── missionId    : string       ← Matches document ID

Mission IDs: fort_santiago, baluarte_san_diego, casa_manila,
             museo_intramuros, centro_turismo
```

## Security Rules Summary

| Path | Read | Create | Update | Delete |
|------|------|--------|--------|--------|
| `users/{uid}` | Owner only | Owner + type validation | Owner + field allowlist | Denied |
| `users/{uid}/missions/{id}` | Owner only | Owner + schema validation | Denied | Denied |
| Everything else | Denied | Denied | Denied | Denied |

**Key rule behaviors:**
- `request.auth.uid == uid` enforced on all operations
- Mission creates require `completed == true`, `missionId is string`, `completedAt is timestamp`
- Updates restricted to: `username`, `firstName`, `lastName`, `walletAddress`, `allComplete`, `whitelisted`, `whitelistedAt`
- `email` and `createdAt` excluded from update allowlist (immutable)

## Security Caveat — Client-Updatable Server-Trust Fields

The current rules allow the client to update `allComplete`, `whitelisted`, and `whitelistedAt`.

**Mitigation**: The `whitelistWallet` Cloud Function performs double verification — checks both the `allComplete` flag AND queries the actual mission subcollection count before whitelisting on-chain. A user who manually sets `allComplete = true` without completing missions will fail the Cloud Function's cross-check.

### Recommended Future Hardening
- Move `allComplete`, `whitelisted`, `whitelistedAt` to server-managed only
- Validate `missionId` against a fixed allow-list in rules
- Add rate limiting via Cloud Functions if abuse is detected

## Key Files

| File | Path | Purpose |
|------|------|---------|
| FirebaseConfig.java | `app/src/main/java/com/wheic/arapp/FirebaseConfig.java` | Singleton Firebase instances + collection/field name constants |
| MissionCompletionHelper.java | `app/src/main/java/com/wheic/arapp/MissionCompletionHelper.java` | Firestore CRUD for missions + wallet + whitelist requests |
| firestore.rules | `firestore.rules` | Firestore security rules |
| index.js | `functions/index.js` | Cloud Function: `whitelistWallet` callable |
| firebase.json | `firebase.json` | Firebase project configuration |

## Cloud Function: whitelistWallet

1. Requires Firebase Auth (`context.auth` check)
2. UID mismatch check (`data.uid !== context.auth.uid`)
3. Wallet address validated via `ethers.isAddress()`
4. Checks `allComplete` flag AND queries mission subcollection count
5. Calls smart contract `whitelistAddress()` using owner key from config
6. Secrets: `polygon.owner_key`, `polygon.contract_address`, `polygon.rpc_url`

## Authentication Pattern

- Firebase Auth email/password
- Username pattern: `username@volver.app`
- Validation regex: `^[a-zA-Z0-9_]{3,30}$`
- Password minimum: 6 characters
- Session stored in SharedPreferences (key: `"username"`, pref name: `"Volver"`)
- Cleared on logout + `FirebaseAuth.signOut()`

## Deployment Commands

```bash
# Security rules
firebase deploy --only firestore:rules

# Cloud Functions
cd functions && npm install && cd ..
firebase deploy --only functions

# Both
firebase deploy --only firestore:rules,functions

# Set secrets
firebase functions:config:set \
  polygon.owner_key="0xKEY" \
  polygon.contract_address="0xADDR" \
  polygon.rpc_url="https://rpc-amoy.polygon.technology"
```
