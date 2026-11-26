# Candy Sweep

A Jetpack Compose match‑3 game about clearing a sticky candy board and sweeping
frosting for cloud‑synced high scores.

## What it does

- 8×8 board with six fruit types and special candies (striped, wrapped, rainbow).
- **Clean‑Up** mode: clear sticky frosting layers and complete the stage within a move budget.
- **Score Rush** mode: a 25‑move blitz.
- Per‑player cloud profile (arcade name, haptics setting) and personal high scores stored in Firestore.
- Combo/cascade animation, hints, shuffle, restart, and stage progression.

## Tech stack

- Kotlin 2.4.21 with Jetpack Compose
- Firebase Authentication (Google Sign‑In) + Cloud Firestore
- Gradle version catalog; AGP 9.1.1, Compose BOM 2024.09.00

## Build & run

1. Open as an Android project in Android Studio or Google AI Studio.
2. A signed build is required. Debug signing falls back to the universal Android
   debug keystore; release signing reads `KEYSTORE_PATH`, `STORE_PASSWORD`,
   `KEY_PASSWORD` from the environment. No signing material is committed.
3. Firebase config is **not** shipped with this scaffold. The app reads its
   Firebase project ID and Google web client ID from
   `app/src/main/res/values/strings.xml`:

   ```xml
   <string name="firestore_database_id"><your-firebase-project-id></string>
   <string name="default_web_client_id"><your-google-web-client-id></string>
   ```

   Copy `google-services.json` from the AI Studio project into `app/` (the repo
   contains only the template scaffold). The `google-services` plugin is
   configured with `missing.passthrough=true`, so a missing JSON only triggers a
   warning rather than failing the build.

### Local Firestore emulator

The project ships an emulator config in `firebase.json` (Firestore at
`127.0.0.1:8085`). Use it during development and run the
`firestore.test.js` unit tests (Node `node:test` + `@firebase/rules-unit-testing`)
against a running emulator.

## Repository layout

```
app/src/main/java/com/example/
  auth/          Google Sign‑In / auto‑sign‑in helpers
  data/          Repository, Firestore error handler, Firestore models
  game/          Match‑3 engine + board generation
  ui/{auth,game,profile,scores,theme}/  UI screens and Compose theme
  viewmodel/     CandySweepViewModel
```

## Testing

- `app/src/test`: JVM unit tests (JUnit + Robolectric).
- `firestore.test.js`: Firestore security‑rules unit tests (needs an emulator).

## License

MIT — see [LICENSE](LICENSE).
