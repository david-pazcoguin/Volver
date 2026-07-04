# Firestore Data Model

## User Document

```
users/{uid}
├── username        : string        ← 3–30 chars, alphanumeric + underscore
├── firstName       : string
├── lastName        : string
├── email           : string        ← Immutable after creation
├── createdAt       : timestamp     ← Immutable after creation
├── walletAddress   : string        ← Polygon wallet address (optional)
├── allComplete     : boolean       ← True when all missions done
├── souvenirMinted  : boolean       ← Server-set by mintSouvenir CF on success
├── souvenirTxHash  : string        ← Server-set — mint transaction hash
├── souvenirTokenId : string        ← Server-set — ERC-721 token ID (from event log)
├── souvenirMintedAt: timestamp     ← Server-set — when mint completed
│
└── missions/{missionId}            ← Mission completion subcollection
    ├── completed    : boolean      ← Always true (append-only)
    ├── completedAt  : timestamp    ← Server timestamp, validated in rules
    └── missionId    : string       ← Matches document ID
```

Mission IDs correspond to the landmarks defined in `HomeActivity.buildMissionList()` (e.g. `fort_santiago`, `baluarte_san_diego`, `casa_manila`, `museo_intramuros`, `centro_turismo`, plus the landmarks added since — see `MissionCompletionHelper.TOTAL_LANDMARKS` for the current count).

## Firestore Rules Summary

| Path | Read | Create | Update | Delete |
|------|------|--------|--------|--------|
| `users/{uid}` | Owner only | Owner + type validation | Owner + field allowlist | Denied |
| `users/{uid}/missions/{id}` | Owner only | Owner + schema validation | Denied | Denied |
| Everything else | Denied | Denied | Denied | Denied |

**Key rule behaviors:**

- `email` and `createdAt` are immutable (excluded from the update allowlist)
- Mission creates require `completed == true`, `missionId is string`, `completedAt is timestamp`, and `missionId` must be one of the known landmark IDs
- Client updates are restricted to: `username`, `firstName`, `lastName`, `walletAddress`, `allComplete`
- Souvenir fields (`souvenirMinted`, `souvenirTxHash`, `souvenirTokenId`, `souvenirMintedAt`) are written **server-side only** by `mintSouvenir` via the Admin SDK, which bypasses rules. They are deliberately absent from the client update allowlist.
