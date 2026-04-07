---
description: "Use when editing Firebase Auth, Firestore rules, Cloud Functions, or mission tracking: firestore.rules, functions/index.js, FirebaseConfig, MissionCompletionHelper."
applyTo: ["functions/**", "firestore.rules", "firebase.json"]
---
# Firebase Instructions

Read `ai-workflows/03-firebase/context.md` for full data model and security details.

## Critical Constraints

- **Owner-only access** — all Firestore reads/writes require `request.auth.uid == uid`
- **Missions are append-only** — no update or delete permitted
- **Immutable fields**: `email`, `createdAt` — excluded from update allowlist
- **`allComplete` is client-updatable** — Cloud Function double-checks mission count
- **Use `FirebaseConfig.java` constants** — never hardcode collection/field names
- **Use `MissionCompletionHelper`** — Activities should not call Firestore directly for missions
- **Deploy rules after changes**: `firebase deploy --only firestore:rules`
- **Secrets in config**: `firebase functions:config:set polygon.*` — never in source
