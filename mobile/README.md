# DockMind mobile

The Expo app a receiving clerk uses at the dock: photograph a delivery note, review it against the purchase order, correct quantities, then confirm or dispute. Capture keeps working with no signal.

The app currently runs against an in-app mock of the API. The real backend is not wired in yet.

## Run it

Needs Node, the Android SDK with `adb`, and the `Medium_Phone_API_37.0` emulator.

```bash
npm install
npx expo start --go --port 8081
```

With the emulator booted and Expo Go installed, open the app from a second terminal:

```bash
adb reverse tcp:8081 tcp:8081
adb shell am start -a android.intent.action.VIEW -d "exp://127.0.0.1:8081" host.exp.exponent
```

Other commands:

```bash
npm test             # Jest
npx tsc --noEmit     # typecheck
npx expo lint        # lint
npx expo export --platform android
```

### Mock or real API

| Variable | Default | Meaning |
|---|---|---|
| `EXPO_PUBLIC_API_MODE` | `mock` | `mock` or `http`. This is the only change needed to use the real API. |
| `EXPO_PUBLIC_API_URL` | `http://10.0.2.2:8080` | Backend address. `10.0.2.2` is the host machine as seen from the Android emulator. |

In development the Capture screen shows a **Test note** picker that selects the fixture (`X-Fixture`):

| Option | Behaviour in the mock |
|---|---|
| Clean | Everything matches |
| Short | Salmon 8 kg short, scallops not ordered |
| Messy, slow | 15 second read, low confidence, wrong unit, one line not delivered |
| Unreadable | Ends in `NEEDS_MANUAL`, the clerk picks the PO |

## How it is built

- **`src/app/`**: screens, using Expo Router. Open POs, Capture, Upload queue, Review, Receipts.
- **`src/api/`**: `types.ts` mirrors `CONTRACT.md`. `client.ts` defines `ApiClient`, implemented by `http.ts` and `mock.ts`. Screens never call `fetch`.
- **`src/queue/`**: the offline queue. `queue.ts` is pure logic with injected storage, uploader and clock, so it can be unit tested. `sqliteStore.ts` and `runtime.ts` connect it to the device.
- **`src/capture/`**: picks the photo, shrinks it to about 1600 px, saves it as JPEG.
- **`src/review/`**: receipt polling, line cards, correction, confirm and dispute. `decision.ts` holds the rule for when Confirm is allowed.

Every capture follows one path: save the photo to app storage, generate the idempotency key, write a SQLite row, then try the upload. The queue uploads in order, one at a time, with backoff. A row left `UPLOADING` by an app kill returns to `WAITING` with the same key.

The app does no matching. It shows the `discrepancy` and `delta` the server sends. The only matching code is in the mock, which stands in for the backend.

## Status

All nine APIs are `[mocked]`. None has been run against the real backend.

| API | Status | Seen working on the emulator |
|---|---|---|
| A1 list POs | `[mocked]` | Yes |
| A2 list receipts | `[mocked]` | Yes |
| A3 get receipt, polling | `[mocked]` | Yes |
| A4 upload | `[mocked]` | Yes |
| A5 correct a line | `[mocked]` | Yes, 72 to 80 changed Short to Match |
| A6 confirm | `[mocked]` | Yes |
| A7 dispute | `[mocked]` | No, unit test only |
| A8 assign PO | `[mocked]` | No, unit test only |
| A9 photo | `[mocked]` | No, the button shows but the photo was not opened |

### Delivery gate

| Check | Result |
|---|---|
| `npx tsc --noEmit` | Clean |
| Jest | 28 tests pass in 4 suites, including queue order, same key on retry, recovery from a kill, and `FAILED` handling |
| `npx expo lint` | Clean |
| `npx expo-doctor` | 20 of 20 checks pass |
| `npx expo export --platform android` | Succeeds |
| Emulator in mock mode | Partly done, see below |

### Not verified yet

- **Offline capture and sync on a device.** The "No signal" banner and the offline error state appeared in airplane mode, but a full capture, reconnect and sync did not complete. The queue logic itself is covered by unit tests. The brief's demo requires this, so test it next.
- **Killing the app during an upload on a device.** Unit test only.
- **On screen:** the slow-read message, the review ticks on a low-confidence note, the `NEEDS_MANUAL` flow, dispute, and the camera.
- **iPhone.** Expo Go on iPhone did not connect. The cause was not found.
- **The "Back to purchase orders" button.** It appeared to land on Receipts once, but someone was using the emulator during that test, so this may not be a bug. It now calls `router.dismissAll()` and needs a re-check.
- **The real API.** `http.ts` has unit tests with a fake `fetch` only.

## Problems we hit

**Setup**

| Problem | Cause | Fix |
|---|---|---|
| `AGENTS.md` requires Expo Router, but only React Navigation was installed | The packages were installed before the routing choice | Installed Expo Router and followed `AGENTS.md` |
| `npx expo lint` could not install ESLint (`ERESOLVE`) | Expo Router pulled in `react-dom` 19.3.1, which needs React 19.3.1, while SDK 58 pins React 19.3.0 | `npx expo install react-dom` pinned it to 19.3.0, with no `--legacy-peer-deps` |
| `tsc` could not find `describe` or `expect` | TypeScript 6 no longer picks up ambient types automatically | Added `"types": ["jest", "node"]` to `tsconfig.json` |
| Lint flagged `setState` in effects | Data loaders could set state synchronously inside `useEffect` | State now changes only in promise callbacks, which also guards against updates after unmount |

**Running on a device**

| Problem | Cause | Fix |
|---|---|---|
| `expo start --android` stuck on "Fetching Expo Go" | The APK is 226 MB and one connection only got about 110 KB/s | Downloaded it over 16 parallel range requests at about 1.4 MB/s, then `adb install` |
| Emulator stuck `offline` in `adb`, restarting `adb` did not help | The emulator hung when booted from saved state after an earlier exit | Cold boot with `-no-snapshot-load` |
| iPhone could not connect over Wi-Fi | The PC's Wi-Fi is set to Public and Windows Firewall has no rule for Node | Tried a tunnel instead |
| `--tunnel` failed with "Install @expo/ngrok and try again" straight after installing it | Expo installed it globally and could not find it, likely because this machine has more than one Node setup | `npm install --no-save @expo/ngrok@^4.1.0` inside `mobile`. A later `npm install` may remove it |
| Tunnel still did not work on the iPhone | Not found | Moved to the emulator |

**Testing tips**

- In Git Bash, set `MSYS_NO_PATHCONV=1` before `adb` commands that use `/sdcard` paths, or the path gets rewritten.
- With `CI=1`, Metro does not pick up file changes. Restart it after editing code.
- After `am force-stop`, wait a moment before launching Expo Go again, or the launch can be lost.
- Do not script the emulator while someone is using it. Taps collide and the results mean nothing.

## AI mistakes caught during the build

Candidates for `AI-LOG.md`:

1. **Committed code that failed the typecheck.** The commit command ran after `tsc` without depending on its result, so the Open POs commit went in with a type error. It was fixed in a follow-up commit, and later commits only ran after a clean `tsc`.
2. **Wrote a React pattern the linter rejected.** The data loaders were called from `useEffect` in a way that could set state synchronously. Typecheck and tests both passed, and only lint caught it.
3. **Gave phone instructions before checking the network.** It said to use the same Wi-Fi, then found the PC's network was Public with no firewall rule for Node, which would probably have blocked the phone.
