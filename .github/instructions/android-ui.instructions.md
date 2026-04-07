---
description: "Use when editing Android Activities, layouts, navigation, RecyclerView, form validation, or UI components in the app module."
---
# Android UI Instructions

Read `ai-workflows/05-android-ui/context.md` for navigation flow and patterns.

## Critical Constraints

- **One activity per file** — UI binding + navigation only, business logic in helpers
- **`FirebaseConfig`** for all Firebase instances and constants
- **`MissionCompletionHelper`** for all Firestore mission operations
- **`WalletManager` singleton** for all wallet state
- **Never expose** `e.getMessage()` in user-facing Toasts — use generic messages
- **RecyclerView**: `setHasFixedSize(true)`, `setHasStableIds(true)`, cache all items
- **TextWatcher**: only respond in `afterTextChanged`, share instances per activity
- **Naming**: Activities = PascalCase + `Activity`, layouts = snake_case + `_activity`, IDs = camelCase with type prefix
