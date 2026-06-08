# Context — Firebase (Auth, Firestore, Cloud Functions)

## Firestore Data Model

```
users/{uid}                         ← User profile document
├── username        : string        ← 3–30 chars, alphanumeric + underscore
├── firstName       : string
├── lastName        : string
├── email           : string        ← Immutable after creation
├── createdAt       : timestamp     ← Immutable after creation
├── walletAddress   : string        ← Polygon wallet address (optional)
├── allComplete     : boolean       ← True when all 5 missions done
├── souvenirMinted  : boolean       ← Server-set on successful mintSouvenir call
├── souvenirTxHash  : string        ← Server-set — mint transaction hash
├── souvenirTokenId : string        ← Server-set — ERC-721 token ID from event log
├── souvenirMintedAt: timestamp     ← Server-set — when mint completed
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
- Mission creates require `completed == true`, `missionId is string`, `completedAt is timestamp`, and `missionId` must be one of the 5 known landmarks
- Client updates restricted to: `username`, `firstName`, `lastName`, `walletAddress`, `allComplete`
- `email` and `createdAt` excluded from update allowlist (immutable)
- Souvenir mint fields (`souvenirMinted`, `souvenirTxHash`, `souvenirTokenId`, `souvenirMintedAt`) are written exclusively server-side by `mintSouvenir` via the admin SDK — they are deliberately not in the client allowlist, so a client cannot self-declare a mint

## Security Caveat — Client-Updatable `allComplete`

The current rules still allow the client to update `allComplete`. A client could set it manually without completing missions.

**Mitigation**: `mintSouvenir` performs a triple check — it re-verifies `allComplete == true`, queries the `missions` subcollection count, and confirms `souvenirMinted != true` before any on-chain call. A cheating client will fail the subcollection count check. Hardening `allComplete` to server-only is tracked as a future improvement.

### Recommended Future Hardening
- Move `allComplete` to server-managed via a Cloud Function trigger on mission document creation
- Validate `missionId` against a fixed allow-list in rules (already done)
- Add App Check + rate limiting if abuse is detected

## Key Files

| File | Path | Purpose |
|------|------|---------|
| FirebaseConfig.java | `app/src/main/java/com/wheic/arapp/FirebaseConfig.java` | Singleton Firebase instances + collection/field name constants |
| MissionCompletionHelper.java | `app/src/main/java/com/wheic/arapp/MissionCompletionHelper.java` | Firestore CRUD for missions + wallet address |
| firestore.rules | `firestore.rules` | Firestore security rules |
| index.js | `functions/index.js` | Cloud Functions: `mintSouvenir` (active), `whitelistWallet` (legacy stub — returns `unavailable`) |
| firebase.json | `firebase.json` | Firebase project configuration |

## Cloud Function: mintSouvenir

1. Requires Firebase Auth (`context.auth` check)
2. UID mismatch check (`data.uid !== context.auth.uid`)
3. Wallet address validated via `ethers.isAddress()` on both the payload value and the Firestore-stored value
4. Uses the server-side stored `walletAddress` as the mint destination (client-supplied value is only used to cross-check)
5. Triple gate before on-chain call: `allComplete == true` AND mission subcollection count ≥ 5 AND `souvenirMinted != true`
6. Calls `adminMintTo(storedWallet)` via ethers.js using the owner wallet (server-side signer)
7. Parses `SouvenirMinted` event to extract token ID
8. Updates Firestore with `souvenirMinted`, `souvenirTxHash`, `souvenirTokenId`, `souvenirMintedAt`
9. Returns `{ success, txHash, tokenId }` to the client

Secrets: `polygon.owner_key`, `polygon.contract_address`, `polygon.rpc_url`

## Cloud Function: whitelistWallet (legacy)

Retained as an exported callable that throws `unavailable` with a migration message. Safe to delete once no deployed client version still invokes it.

## Authentication Pattern

- Firebase Auth email/password
- Username pattern: `username@volver.app`
- Validation regex: `^[a-zA-Z0-9_]{3,30}$`
- Password minimum: 6 characters
- Session stored in SharedPreferences (key: `"username"`, pref name: `"Volver"`)
- First name cached in SecurePrefs on successful registration so HomeActivity shows the personalized greeting immediately
- Cleared on logout + `FirebaseAuth.signOut()`

## Deployment Commands

```powershell
# Security rules
firebase deploy --only firestore:rules

# Cloud Functions
cd functions ; npm install ; cd ..
firebase deploy --only functions

# Both
firebase deploy --only firestore:rules,functions

# Set secrets (required before first mintSouvenir call)
firebase functions:config:set `
  polygon.owner_key="0xKEY" `
  polygon.contract_address="0xd8b934580fcE35a11B58C6D73aDeE468a2833fa8" `
  polygon.rpc_url="https://rpc-amoy.polygon.technology"
```
