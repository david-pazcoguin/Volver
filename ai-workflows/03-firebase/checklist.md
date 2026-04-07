# Checklist — Firebase Tasks

## Modifying Firestore Security Rules

1. Edit `firestore.rules`
2. Test locally with Firebase Emulator (if set up) or deploy to a staging project
3. Deploy: `firebase deploy --only firestore:rules`
4. Verify in Firebase Console → Firestore Database → Rules tab

## Adding a New Firestore Field

1. Add the field constant in `FirebaseConfig.java`
2. Update `firestore.rules` — add to the update allowlist if user-editable
3. Add type validation in the create rule if it's a required field
4. Update `MissionCompletionHelper.java` if it's mission-related
5. Deploy rules: `firebase deploy --only firestore:rules`

## Modifying the Cloud Function

1. Edit `functions/index.js`
2. Install dependencies: `cd functions && npm install`
3. Deploy: `firebase deploy --only functions`
4. Check logs: `firebase functions:log`
5. Verify config is set: `firebase functions:config:get`

## Debugging Auth Issues

1. Check that `google-services.json` is in `app/` (not committed to git)
2. Verify Email/Password provider is enabled in Firebase Console → Authentication → Sign-in method
3. Check logcat for Firebase Auth errors: `adb logcat -s "FirebaseAuth:*"`

## Debugging Firestore Issues

1. Check Firebase Console → Firestore → Data tab for the user's document
2. Verify `users/{uid}/missions/` has the expected documents
3. Check security rules aren't blocking: Firebase Console → Firestore → Rules → Monitor tab
4. Check logs: `adb logcat | Select-String "Firestore"`
