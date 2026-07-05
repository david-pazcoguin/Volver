# Context — Android UI

## Navigation Flow

```
LoginActivity → RegisterActivity (new users)
      ↓
HomeActivity ←→ SettingActivity → AccountSettingActivity
      ↓                              AboutUsActivity
    (tap mission)
      ↓
ARActivity (AR camera + model placement + TTS)
      ↓
    (all 5 complete)
      ↓
WalletSetupActivity → NFTClaimActivity
```

## Activities

| Class | Purpose | Key Dependencies |
|-------|---------|-----------------|
| `LoginActivity` | App entry; Firebase email/password auth | FirebaseAuth, SharedPreferences |
| `RegisterActivity` | Account creation + Firestore profile | FirebaseAuth, FirebaseFirestore |
| `HomeActivity` | Mission list, progress tracking, NFT banner | ARAdapter, MissionCompletionHelper, WalletManager |
| `ARActivity` | AR session, geospatial proximity, coin/relic spawns and collection | ARCore, Sceneform, FusedLocationProvider |
| `SettingActivity` | User settings, logout | FirebaseAuth, FirebaseFirestore |
| `AccountSettingActivity` | Edit name and password | FirebaseAuth, FirebaseFirestore |
| `AboutUsActivity` | Static info screen | — |
| `WalletSetupActivity` | Multi-step wallet setup (connect or create) | WalletManager, MissionCompletionHelper, ZXing |
| `NFTClaimActivity` | NFT minting UI | PolygonService, WalletManager |

## Data Classes & Adapters

| Class | Purpose |
|-------|---------|
| `ARHelper` | Mission data transfer object: name, coordinates, character info, model filename |
| `ARAdapter` | RecyclerView adapter with stable IDs, mission images, click handling |
| `FirebaseConfig` | Centralized Firebase instances + collection/field name constants |

## Key Patterns

### Session Management
- SharedPreferences name: `"Volver"`, key: `"username"`
- Set on login, cleared on logout + `FirebaseAuth.signOut()`

### RecyclerView Optimization
```java
recyclerView.setHasFixedSize(true);
recyclerView.setItemViewCacheSize(arHelpers.size());  // Cache all 5
recyclerView.setHasStableIds(true);                   // Efficient animations
```

### Form Validation
- Shared `TextWatcher` instances per activity
- Only respond in `afterTextChanged` (not all 3 callbacks)
- Username regex: `^[a-zA-Z0-9_]{3,30}$`
- Password minimum: 6 characters
- Name fields: max 50 characters

### Secure UI
- `FLAG_SECURE` on WalletSetupActivity and NFTClaimActivity
- Clipboard auto-clear after 30 seconds
- Button debounce (2s) on sensitive operations

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Activities | PascalCase + `Activity` | `HomeActivity` |
| Layout files | snake_case + `_activity` or `_layout` | `home_activity.xml` |
| View IDs | camelCase with type prefix | `tvMissionName`, `txtPassword` |
| Constants | UPPER_SNAKE_CASE | `ACTIVATION_RADIUS_METERS` |

## Resources
- **14 layout files** in `app/src/main/res/layout/`
- **4 custom fonts**: Montserrat, Aclonica, BalooiBhai, Cabin
- **5 mission images** in `app/src/main/res/drawable/`
- **Material Components 1.13.0** for styling
