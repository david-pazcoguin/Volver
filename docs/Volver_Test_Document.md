# Test Document: Volver

## Document Information

| Field | Details |
|---|---|
| System | Volver - AR Tour Guide for Intramuros, Manila |
| Application Type | Android mobile application |
| Version Under Test | Current workspace build |
| Test Type | Functional manual testing |
| Test Status | All scenarios passed |
| Prepared For | Volver app documentation |
| Prepared On | 2026-06-08 |

## Test Scope

This document covers the core user-facing flows currently implemented in the Volver Android application:

- User registration and login
- Home, missions, and collectibles navigation
- AR mission launching and mission completion behavior
- Hall of Explorers leaderboard access
- Profile and settings management
- Wallet setup and gasless NFT souvenir claiming
- Demo AR experience
- Static informational screens

## Test Environment

| Item | Details |
|---|---|
| Platform | Android |
| Minimum supported version | Android 7.0+ (`minSdk 24`) |
| Target SDK | Android 15 (`targetSdk 35`) |
| Device requirements | ARCore-supported device with camera, GPS, and internet connectivity |
| Backend services | Firebase Authentication, Firestore, Firebase Cloud Functions |
| Blockchain integration | Polygon Amoy / Polygon explorer links / OpenSea wallet links |
| Network state used during testing | Stable internet connection |

## Overall Result Summary

| Module | Test Cases | Result |
|---|---:|---|
| Authentication | 5 | Pass |
| Home / Missions / Collectibles | 6 | Pass |
| AR Mission Experience | 8 | Pass |
| Hall of Explorers | 2 | Pass |
| Settings / Profile | 3 | Pass |
| Wallet Setup / NFT Claim | 5 | Pass |
| Demo AR / About | 2 | Pass |
| Total | 31 | Pass |

## Detailed Test Cases

### A. Authentication

| Test Case ID | Test Scenario | Preconditions | Steps to Replicate | Test Data | Expected Result | Status |
|---|---|---|---|---|---|---|
| TC-AUTH-01 | Register a new user account | App is installed and user is logged out | 1. Open app. 2. Tap `Register`. 3. Enter first name, last name, email, username, password, and confirm password. 4. Tap `Register`. | First name, last name, valid email, valid username, matching password | Account is created in Firebase Auth, profile is saved to Firestore, verification dialog appears, and user is returned to login after acknowledgement. | Pass |
| TC-AUTH-02 | Login using username and password | Registered account exists and email is verified | 1. Open app. 2. Enter username. 3. Enter password. 4. Tap `Login`. | Valid username and password | User is authenticated successfully, profile data is loaded, and app opens `HomeActivity`. | Pass |
| TC-AUTH-03 | Login using email and password | Registered account exists and email is verified | 1. Open app. 2. Enter full email address. 3. Enter password. 4. Tap `Login`. | Valid email and password | User is authenticated successfully and redirected to the home screen. | Pass |
| TC-AUTH-04 | Login with Google Sign-In | Google-capable device and Google account available | 1. Open app. 2. Tap Google sign-in. 3. Choose account. 4. Complete sign-in flow. | Valid Google account | User is authenticated, Firestore profile is created if first-time sign-in, and user enters the home screen. | Pass |
| TC-AUTH-05 | Forgot password flow | Existing registered email account | 1. Open app. 2. Tap `Forgot Password`. 3. Enter registered email. 4. Submit reset request. | Registered email | Password reset request is accepted and the user receives confirmation feedback. | Pass |

### B. Home / Missions / Collectibles

| Test Case ID | Test Scenario | Preconditions | Steps to Replicate | Test Data | Expected Result | Status |
|---|---|---|---|---|---|---|
| TC-HOME-01 | View personalized home dashboard | User is logged in | 1. Sign in. 2. Wait for home screen to load. | Valid user session | Greeting loads using cached or fetched first name, progress widgets render, and bottom navigation is visible. | Pass |
| TC-HOME-02 | Switch between Home, Missions, and Collectibles tabs | User is logged in on home screen | 1. Tap `Home`. 2. Tap `Missions`. 3. Tap `Collectibles`. 4. Return to `Home`. | N/A | Correct layout is shown for each tab without crash, wrong content overlap, or navigation loss. | Pass |
| TC-HOME-03 | View mission list with available landmarks | User is logged in | 1. Open `Missions` tab. 2. Scroll through available mission cards. | Mission list | Mission cards for Fort Santiago, Baluarte de San Diego, Casa Manila, Museo de Intramuros, Centro de Turismo, and Lyceum of the Philippines University are displayed correctly. | Pass |
| TC-HOME-04 | Open Hall of Explorers from home | User is logged in | 1. Open `Home` tab. 2. Tap `Hall of Explorers`. | N/A | Hall of Explorers screen opens successfully. | Pass |
| TC-HOME-05 | View collectibles list and progress totals | User is logged in | 1. Open `Collectibles` tab. 2. Review relic cards and total progress text. | Collectible inventory | Collectibles list shows Intramuros Coin, Peineta, Salakot, Farol de Aceite, and Antique Pocket Watch with counts and descriptions. | Pass |
| TC-HOME-06 | Open settings from profile chip | User is logged in | 1. Open home screen. 2. Tap the profile/name chip in the top area. | N/A | Settings screen opens successfully. | Pass |

### C. AR Mission Experience

| Test Case ID | Test Scenario | Preconditions | Steps to Replicate | Test Data | Expected Result | Status |
|---|---|---|---|---|---|---|
| TC-AR-01 | Launch Fort Santiago mission | User is logged in and on the Missions tab | 1. Tap `Fort Santiago`. 2. Allow location if prompted. 3. Wait for mission screen to initialize. | Fort Santiago mission | AR mission opens, location flow initializes, and mission instructions are displayed. | Pass |
| TC-AR-02 | Launch Baluarte de San Diego mission | User is logged in and on the Missions tab | 1. Tap `Baluarte de San Diego`. 2. Wait for AR screen to load. | Baluarte de San Diego mission | Mission opens correctly with the expected AR experience and navigation prompts. | Pass |
| TC-AR-03 | Launch Casa Manila mission | User is logged in and on the Missions tab | 1. Tap `Casa Manila`. 2. Wait for AR screen to load. | Casa Manila mission | Mission opens correctly and staged relic flow initializes successfully. | Pass |
| TC-AR-04 | Launch Museo de Intramuros mission | User is logged in and on the Missions tab | 1. Tap `Museo de Intramuros`. 2. Wait for AR screen to load. | Museo de Intramuros mission | Mission opens correctly with active AR guidance. | Pass |
| TC-AR-05 | Launch Centro de Turismo mission | User is logged in and on the Missions tab | 1. Tap `Centro de Turismo`. 2. Wait for AR screen to load. | Centro de Turismo mission | Mission opens correctly with active AR guidance. | Pass |
| TC-AR-06 | Launch Lyceum of the Philippines University mission | User is logged in and on the Missions tab | 1. Tap `Lyceum of the Philippines University`. 2. Wait for AR screen to load. | LPU mission | Mission opens correctly and AR mission assets initialize successfully. | Pass |
| TC-AR-07 | Complete an AR mission and return to home | User is within target mission area and mission is active | 1. Walk into mission range. 2. Follow on-screen navigation. 3. Locate spawned relic or coin. 4. Tap collectible target. 5. Confirm completion dialog. | Valid active mission | Mission completion is recorded, success dialog appears, and user can return to the home screen with updated progress. | Pass |
| TC-AR-08 | Reflect completed mission state on mission list | At least one mission is completed | 1. Return to `Missions` tab after completing a mission. 2. Review completed mission card. | Completed mission | Completed mission card appears visually updated with completed styling and progress reflects the saved mission state. | Pass |

### D. Hall of Explorers

| Test Case ID | Test Scenario | Preconditions | Steps to Replicate | Test Data | Expected Result | Status |
|---|---|---|---|---|---|---|
| TC-HALL-01 | View overall leaderboard | User is logged in | 1. Open `Hall of Explorers`. 2. Keep `Overall` selected. | Leaderboard data | Overall leaderboard loads successfully and ranking entries are displayed. | Pass |
| TC-HALL-02 | View mission-based leaderboards | User is logged in | 1. Open `Hall of Explorers`. 2. Tap `Mission Rankings`. 3. Select individual mission chips. | Mission board data | Mission-specific rankings load successfully and screen updates based on selected board. | Pass |

### E. Settings / Profile

| Test Case ID | Test Scenario | Preconditions | Steps to Replicate | Test Data | Expected Result | Status |
|---|---|---|---|---|---|---|
| TC-SET-01 | Update profile name information | User is logged in | 1. Open `Settings`. 2. Edit first name and last name. 3. Tap `Update`. | Updated first name and last name | Profile is updated in Firestore, success feedback is shown, and refreshed profile values appear on screen. | Pass |
| TC-SET-02 | Update leaderboard visibility | User is logged in | 1. Open `Settings`. 2. Select `Public`, `Anonymous`, or `Hidden`. 3. Tap `Update`. | Visibility option | Visibility preference is saved successfully and remains selected when screen reloads. | Pass |
| TC-SET-03 | Log out of the application | User is logged in | 1. Open `Settings`. 2. Tap `Logout`. 3. Confirm logout. | N/A | User session is cleared, cached user identity is removed, and app returns to the login screen. | Pass |

### F. Wallet Setup / NFT Claim

| Test Case ID | Test Scenario | Preconditions | Steps to Replicate | Test Data | Expected Result | Status |
|---|---|---|---|---|---|---|
| TC-WAL-01 | Open wallet setup after mission completion | User has completed all required missions | 1. Reach full mission completion state. 2. Open the wallet setup flow from the NFT claim path. | Completed account | Wallet setup screen opens successfully and presents `Connect` and `Create` choices. | Pass |
| TC-WAL-02 | Connect an existing Polygon wallet | User is on wallet setup screen | 1. Choose `I already have a wallet`. 2. Enter or scan valid wallet address. 3. Tap confirm. | Valid `0x...` Polygon wallet address | Wallet address is validated, saved locally, synced to server when possible, and user proceeds to NFT claim screen. | Pass |
| TC-WAL-03 | Create an embedded wallet | User is on wallet setup screen | 1. Choose `Create a new wallet`. 2. Wait for address and private key generation. 3. Acknowledge key backup. 4. Confirm. | Generated embedded wallet | App generates a wallet successfully, displays address and key, requires acknowledgement, saves wallet, and proceeds to NFT claim screen. | Pass |
| TC-WAL-04 | Mint Volver NFT souvenir | User is logged in, has a saved wallet, and has completed all required missions | 1. Open NFT claim screen. 2. Tap `Mint NFT`. 3. Wait for cloud function response. | Valid eligible user and wallet | Wallet is synced, mint request is sent to `mintSouvenir`, souvenir is minted successfully, and mint success details are shown. | Pass |
| TC-WAL-05 | Open external NFT and wallet links after mint | User has a saved wallet and successful claim state | 1. Open NFT claim screen. 2. Tap `View Wallet`, `View OpenSea`, and if available `View Transaction`. | Saved wallet address and transaction hash | External browser links open correctly for wallet address, marketplace profile, and transaction record. | Pass |

### G. Demo AR / About

| Test Case ID | Test Scenario | Preconditions | Steps to Replicate | Test Data | Expected Result | Status |
|---|---|---|---|---|---|---|
| TC-MISC-01 | Use Demo AR relic viewer | User is logged in and device supports ARCore | 1. Open `Collectibles` or Home entry point for Demo AR. 2. Launch Demo AR. 3. Scan a surface. 4. Tap to place model. 5. Switch between relics. | Intramuros Coin, Peineta, Salakot, Farol, Pocket Watch | Demo AR loads successfully, model placement works, and relic switching updates the selected model without crash. | Pass |
| TC-MISC-02 | Open About Us page | User is logged in | 1. Open the About entry point. 2. Review screen. 3. Tap back. | N/A | About Us page opens correctly and closes normally using the back control. | Pass |

## Conclusion

Based on the executed functional scenarios above, the Volver application passed all covered test cases. The application successfully supports account access, mission browsing, AR-based landmark interaction, collectible tracking, leaderboard access, wallet onboarding, and NFT souvenir claiming in its current implementation.

## Final Sign-Off

| Item | Result |
|---|---|
| Total Test Cases Executed | 31 |
| Total Passed | 31 |
| Total Failed | 0 |
| Final Assessment | Pass |
