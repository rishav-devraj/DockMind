# Plan: mobile agent

Owns `/mobile`. Read `CONTRACT.md` first and follow it exactly, including the commit format. The real backend is being built at the same time, so build against a mock and do not depend on it.

## Stack

Expo (latest stable SDK), TypeScript strict, Android and iOS. Use only libraries that run in Expo Go during development: `expo-camera` or `expo-image-picker`, `expo-image-manipulator`, `expo-file-system`, `expo-sqlite`, `@react-native-community/netinfo`. Install with `npx expo install` so versions match the SDK. An emulator is available: `Medium_Phone_API_37.0`.

## Architecture

- `src/api/types.ts`: types mirroring `CONTRACT.md`.
- `src/api/client.ts`: one `ApiClient` interface with `HttpApiClient` and `MockApiClient`, chosen by `EXPO_PUBLIC_API_MODE=mock|http`. Default `mock`. `EXPO_PUBLIC_API_URL` defaults to `http://10.0.2.2:8080`.
- The mock must behave like the contract: 202 then polling from `CAPTURED` to `EXTRACTED`, same key returns the same receipt, a slow call, a `NEEDS_MANUAL` case, a low-confidence case, 409s. Use the contract's example data and the seed POs.
- Screens never call `fetch` directly. Switching to the real API must be an environment variable change only.

## Steps (commit after each)

1. **Scaffold.** Expo TypeScript project, navigation, API client and mock. `npx tsc --noEmit` clean.
2. **Open POs.** List with pull to refresh, loading, empty and error states. Cache the last list so it still shows offline.
3. **Capture.** Camera with gallery fallback. Downscale to about 1600 px on the long edge and compress to JPEG before storing.
4. **Queue.** Do this before the review screen, since capture depends on it. Every capture, online or offline, follows one path: copy the photo to app storage, generate the idempotency key, write a SQLite row, then attempt upload. States: `WAITING`, `UPLOADING`, `SYNCED`, `FAILED`.
   - Drains in order, on reconnect and on app start, one at a time, with backoff.
   - A row left in `UPLOADING` after an app kill goes back to `WAITING`, keeping the same key.
   - Network errors, 5xx and timeouts retry. 400 and 413 go to `FAILED` with a readable reason and a manual retry button.
   - Pure queue logic with injected storage and uploader so it can be unit-tested with Jest.
5. **Queue screen.** Visible pending and failed items, plus a clear offline banner across the app.
6. **Review.** Poll after upload. Ordered vs received per line, colored discrepancy chip, per-line confidence, `aiNote`. Show the slow-call state ("Still reading the note, this can take 30 seconds") and failure in plain words.
7. **Correct.** Edit a quantity through A5. Always display the server's recomputed `discrepancy` and `delta`, with no discrepancy logic in the app beyond the mock.
8. **Decide.** Confirm and Dispute with a required reason. When `needsReview` is true, the clerk must tick each flagged line before Confirm enables. When `poNumber` is null, the clerk must pick a PO through A8 first. Treat a repeated tap safely.
9. **Dock usability.** Large tap targets, high contrast, plain error text. Set `usesCleartextTraffic` through `expo-build-properties` in `app.json` for the later release build.

## Delivery gate (stop here, before API integration)

All of these must be true and observed by running them, not assumed:

- `npx tsc --noEmit` is clean.
- Jest queue tests pass: order kept, same key on retry, recovery from a kill while `UPLOADING`, `FAILED` handling.
- `npx expo export --platform android` succeeds.
- Try the emulator: boot it, run the app in mock mode, take `adb` screenshots of PO list, queue and review. If it cannot be done from this environment, say exactly why.
- Every screen works in mock mode, and the only change needed for the real API is `EXPO_PUBLIC_API_MODE=http`.

Do not build the release APK yet. Do not edit `/backend`, `/kit`, `CONTRACT.md`, `DECISIONS.md`, or `AI-LOG.md`.

## Final report

List each API A1 to A9 with its status (`[mocked]` is expected), test results with real output, what was not verified (airplane mode on a device at minimum, if not done), any contract problem found, two honest examples of AI mistakes you hit and fixed for `AI-LOG.md`, and the exact commands to run the app.

Design for change: a requirement will arrive later that touches the data model, matching, API and app. Render from server fields and keep types in one file.
