# Plan: backend and AI agent

Owns `/backend` and `/kit`. Read `CONTRACT.md` first and follow it exactly, including the commit format. The backend is tested separately from the app, so an exact match to the contract matters more than anything else here.

## Environment

- Set `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` in your shell. There is no system Java or Maven.
- Spring Boot 3.x, Java 17 language level, Maven wrapper committed (`./mvnw`).
- H2 in file mode at `backend/data/` (gitignored), so receipts survive a restart with no database install. Load the seed only when the tables are empty.
- Listen on `0.0.0.0:8080`.
- `LLM_API_KEY` is not set yet. Do not wait for it and do not hard-code one.

## Steps (commit after each)

1. **Scaffold.** Spring Boot project, health check, `.gitignore`, boots with `./mvnw spring-boot:run`.
2. **Kit.** Create `/kit` to the brief's layout, since no starter kit was supplied:
   - `seed.sql`, copied exactly from Addendum B of the brief.
   - `delivery-notes/note-1-clean.png`: printed, PO-4501, all three lines match.
   - `delivery-notes/note-2-short.png`: printed, PO-4502, Salmon fillet 72 KG, Scallops 10 KG, no Tiger Prawns.
   - `delivery-notes/note-3-messy.png`: handwriting-style font, rotated, noisy, supplier Dairy Direct, PO number legible only as `PO-45?3`, Milk 1L 252 EA, Butter 500g 100 CASE, Cheddar 38 KG.
   - `fixtures/note-N.extracted.json` in the brief's LLM shape. For note 3, return `poNumber: "PO-4513"` with `poConfidence: 0.55`, to exercise the supplier cross-check.
   - Generate the images with a small Java2D program kept in `/kit/tools`, then view them to confirm they are legible.
3. **Domain and read APIs.** Entities, lifecycle, seed load, A1, A2, A3.
4. **Matching.** One pure class with no Spring or database dependency. Unit tests for all six discrepancy types, unit normalization, and clerk correction.
5. **Extractor interface and `FixtureExtractor`.** Select with config `extractor.type=fixture|llm`, default `fixture`. Fixture choice order: `X-Fixture` header, then the upload filename containing `note-N`, then config `extractor.fixture.default`. `garbage` returns unparseable text.
6. **Capture.** A4 with a database unique constraint on the idempotency key. Handle the concurrent race by catching the constraint violation and returning the existing receipt. Store the image, run extraction asynchronously, serve A9. On startup, re-run or fail any receipt stuck in `CAPTURED`.
7. **Validation layer.** One path for all extractor output, whichever extractor produced it: strip markdown fences, survive bad JSON, coerce strings to numbers, clamp confidence to 0..1, apply the PO resolution rules, clear invalid `poLineId`s. Any failure ends in `NEEDS_MANUAL`. Unit-test each failure.
8. **Correct and decide.** A5, A6, A7, A8, with the 409 rules and safe repeats.
9. **`LlmExtractor`.** Use `LlmGateway` from Addendum C of the brief. Prompt includes the photo and the open POs with lines, and asks for the JSON shape and a short `aiNote`. The model must not compute discrepancies. Test against a stubbed gateway returning good, fenced, and garbage output. The real call runs once a key exists.
10. **Curl script.** `backend/scripts/smoke.sh` runs the full flow on all three notes and the garbage case.

## Delivery gate (stop here, before API integration)

All of these must be true and observed by running them, not assumed:

- `./mvnw test` passes.
- The server boots from clean and `smoke.sh` passes against it.
- Same `Idempotency-Key` sent twice, and sent concurrently, yields one receipt.
- Note 1 is all `MATCH`. Note 2 gives `SHORT`, `UNKNOWN_ITEM`, `NOT_DELIVERED`. Note 3 ends in `NEEDS_MANUAL` with `poNumber: null`, and after A8 with PO-4503 gives `OVER`, `UNIT_MISMATCH`, `SHORT`.
- `garbage` ends in `NEEDS_MANUAL`, never a 500.

Do not edit `/mobile`, `CONTRACT.md`, `DECISIONS.md`, or `AI-LOG.md`.

## Final report

List each API A1 to A9 with its status, test and smoke results with real output, what was not verified (the real Gemini call at minimum), any contract problem found, two honest examples of AI mistakes you hit and fixed for `AI-LOG.md`, and the exact commands to run the server.

Design for change: a requirement will arrive later that touches the data model, matching, API and app. Keep matching in one class and don't hard-wire the set of fields into several layers.
