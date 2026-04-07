# Firebase Security Notes

This repository tracks Firestore rules in [firestore.rules](firestore.rules).

## Current Policy Summary

| Path | Read | Create | Update | Delete |
|------|------|--------|--------|--------|
| `users/{uid}` | Owner only | Owner + type validation | Owner + field allowlist | Denied |
| `users/{uid}/missions/{id}` | Owner only | Owner + schema validation | Denied | Denied |
| Everything else | Denied | Denied | Denied | Denied |

### User Profile Rules (`users/{uid}`)

- **Read**: Only the document owner (`request.auth.uid == uid`).
- **Create**: Owner only. All required fields validated by type.
- **Update**: Owner only. Allowed fields restricted via `request.resource.data.diff(resource.data).affectedKeys().hasOnly(...)`.
- **Immutable fields**: `email` and `createdAt` cannot be changed after creation.
- **Delete**: Denied for all users.

### Mission Rules (`users/{uid}/missions/{missionId}`)

- **Create**: Owner only. Required schema: `completed == true`, `missionId is string`, `completedAt is timestamp`.
- **Update/Delete**: Denied. Missions are append-only records.

## Security Caveat — Client-Updatable Server-Trust Fields

The current rules allow the client to update these fields on the user profile:

- `allComplete`
- `whitelisted`
- `whitelistedAt`

**Mitigation in place**: The `whitelistWallet` Cloud Function performs a double verification — it checks both the `allComplete` flag AND queries the actual mission subcollection count before whitelisting on-chain. A user who manually sets `allComplete = true` without completing missions will still fail the Cloud Function's cross-check.

### Recommended Future Hardening

- Move `allComplete`, `whitelisted`, and `whitelistedAt` to server-managed only (Cloud Functions write, client denied).
- Validate `missionId` against a fixed allow-list in rules (e.g., only the 5 known mission IDs).
- Add rate limiting via Cloud Functions if abuse is detected.

## Deployment Reminder

After editing `firestore.rules`, deploy to Firebase:

```bash
firebase deploy --only firestore:rules
```

Verify rules are active in **Firebase Console → Firestore Database → Rules** tab.

---

## Related Documentation

- [README.md](README.md) — Full project overview and security standards
- [BLOCKCHAIN_SETUP.md](BLOCKCHAIN_SETUP.md) — Smart contract deployment and Cloud Function setup
- [BUILD_AND_DEPLOY.md](BUILD_AND_DEPLOY.md) — Firebase deployment commands
