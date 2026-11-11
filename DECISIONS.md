# Decisions

> Draft written before the build. Each answer states the design, and must be re-checked against the code before submission.

## Why is discrepancy logic in code and not in the model, and what stops a bad model answer reaching the database?

A discrepancy can start a dispute with a supplier, so it has to be repeatable and testable, and a model is neither. The model only reads the note and writes a short explanation. One pure matching class compares quantities and units.

Every model answer passes through one validation layer before anything is saved. It strips markdown fences, survives bad JSON, coerces numbers, and checks that the PO and each PO line exist and belong together. It also checks that the supplier agrees with the PO, because PO-4503 and PO-4513 differ by a single digit.

## How is the extractor swapped, and what happens when the model fails?

`Extractor` is an interface with `LlmExtractor` and `FixtureExtractor`, selected by `extractor.type`. Both feed the same validation layer. A timeout, HTTP error or unusable answer ends in `NEEDS_MANUAL`, with the photo kept. The clerk can still assign the PO, enter quantities and decide.

## How does the sync stay idempotent end to end?

The phone generates the key once, at capture, and writes it to SQLite with the photo before any upload is attempted. Every retry reuses that key, including after an app kill. The server enforces a unique constraint on the key, so a repeated or concurrent request returns the existing receipt. Confirm and dispute are also safe to repeat.

## What did you cut, and what would you build next?

To be completed at submission.

## Spec gaps we filled

- Capture is asynchronous (202, then poll), because a vision call can take 30 seconds and a dock connection may not hold that long.
- Confirming a receipt does not close its PO, since a PO can arrive in several deliveries.
- Only capture works offline. Correcting and deciding need the server, because they depend on the extraction result.
- Two additions to the suggested API: `PATCH /receipts/{id}` assigns a PO when it could not be resolved, and `GET /receipts/{id}/image` serves the stored photo as proof of delivery.
- A line needs review below 0.80 confidence. The threshold is configurable.
- No starter kit was supplied. `seed.sql` and the gateway come from the brief, and we created the three delivery notes and fixtures ourselves.
