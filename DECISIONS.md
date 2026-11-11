# Decisions

> Re-checked against the code after the read-first change. The real Gemini model (`gemini-3.1-flash-lite`) has been run on all nine kit notes, locally, with 100,000 open POs in the database.

## Why is discrepancy logic in code and not in the model, and what stops a bad model answer reaching the database?

A discrepancy can start a dispute with a supplier, so it has to be repeatable and testable, and a model is neither. The model only reads the note and writes a short explanation. It is not shown the purchase orders: code looks the PO up by the number the model read, and links the lines to it. One pure matching class compares quantities and units.

Every model answer passes through one validation layer before anything is saved. It strips markdown fences, survives bad JSON, coerces numbers, and checks that the PO and each PO line exist and belong together. It also checks that the supplier agrees with the PO, because PO-4503 and PO-4513 differ by a single digit. A missing confidence counts as zero, and a note with no lines is treated as unread, since saving it would report the whole PO as not delivered.

## How is the extractor swapped, and what happens when the model fails?

`Extractor` is an interface with `LlmExtractor` and `FixtureExtractor`, selected by `extractor.type`, which is set with the `EXTRACTOR_TYPE` environment variable and defaults to `fixture`. Both feed the same validation layer. A timeout, HTTP error or unusable answer ends in `NEEDS_MANUAL`, with the photo kept. The clerk can still assign the PO, enter quantities and decide.

## How does the sync stay idempotent end to end?

The phone generates the key once, at capture, and writes it to SQLite with the photo before any upload is attempted. Every retry reuses that key, including after an app kill. The server enforces a unique constraint on the key, so a repeated or concurrent request returns the existing receipt. The photo commits in the same transaction as the receipt, so a retry can never find one without the other. Confirm and dispute are also safe to repeat, and a row lock means a simultaneous confirm and dispute cannot both succeed.

## What did you cut, and what would you build next?

Cut: the bonus items, a README, and the mid-hack change, which had not been handed out when this was written. The offline queue is built and tested in code, but the airplane-mode demo has not been run on a device.

Next: run the real vision call against the messy note, add a README with clone-to-running steps, and pick one bonus item.

## Spec gaps we filled

- Capture is asynchronous (202, then poll), because a vision call can take 30 seconds and a dock connection may not hold that long.
- Confirming a receipt does not close its PO, since a PO can arrive in several deliveries.
- Only capture works offline. Correcting and deciding need the server, because they depend on the extraction result.
- Two additions to the suggested API: `PATCH /receipts/{id}` assigns a PO when it could not be resolved, and `GET /receipts/{id}/image` serves the stored photo as proof of delivery.
- A line needs review below 0.80 confidence. The threshold is configurable.
- No starter kit was supplied. `seed.sql` and the gateway come from the brief, and we created the three delivery notes and fixtures ourselves.
- A receipt in `NEEDS_MANUAL` never has a PO. One unreadable quantity sends the whole receipt there, and the line is kept and flagged, because guessing a number could start a false dispute.
- When the clerk assigns a PO, it counts as full confidence, so the receipt is not flagged forever for the model's weak reading. A receipt that already has a PO cannot be moved to another one.
- Assigning a PO links lines by description, and also accepts a shortened name when only one PO line fits, because notes often abbreviate ("Cheddar" for "Cheddar, mature").
- Photos are stored in the database, in their own table, so a receipt and its proof of delivery commit together.
- A receipt stuck in `CAPTURED` after a crash is read again at startup.
- The server is set up to deploy to Render from a Dockerfile, with HTTPS provided by Render. Keys are host environment variables, never in the image or repository. The API has no login, as the brief scopes it out, so a hosted URL should stay private. On a free instance the H2 file does not survive a restart, and the POs re-seed.
- The reader reads first and code looks the PO up afterwards. The first version sent every open PO in the prompt, which fails at about 15,000 open POs and let the model fill a smudged digit with a PO it had been shown. See `docs/ARCHITECTURE.md`.
- A1 and A2 are paged, 50 per page by default, with the total in `X-Total-Count`, and A1 can be searched with `q`. They stay plain arrays so the app keeps working unchanged.
- Swagger UI is served at `/swagger-ui.html`, generated from the controllers, so the API can be tried without the app. Like the API itself, it has no login.
- A small React page in `web/` uploads a note and shows the photo beside what the server read, to check the reader without the app. It reaches the API through Vite's proxy, so the API needs no CORS.
