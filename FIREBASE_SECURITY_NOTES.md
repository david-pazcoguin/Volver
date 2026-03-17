# Firebase Security Notes

This repository tracks Firestore rules in [firestore.rules](firestore.rules).

## Current policy summary

- A signed-in user can read and create their own profile at /users/{uid}.
- A signed-in user can update their own profile with a restricted set of fields.
- A signed-in user can create mission docs under /users/{uid}/missions/{missionId}.
- Mission docs cannot be updated or deleted.
- Everything else is denied.

## Important security caveat

The current rules allow the client to update server-trust fields on the user profile:

- allComplete
- whitelisted
- whitelistedAt

If your app uses these fields to unlock minting or rewards, users can set them directly from a modified client.

## Recommended hardening

- Treat allComplete, whitelisted, and whitelistedAt as server-managed only.
- Set and update those fields from trusted backend code (Cloud Functions or server), not from the app.
- Optionally validate missionId against a fixed allow-list in rules.
- Keep immutable fields immutable after creation (for example email and createdAt).

## Deployment reminder

After editing rules in this repo, deploy the same rules to Firebase Console or Firebase CLI.
