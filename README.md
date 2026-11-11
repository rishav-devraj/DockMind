# DockMind

An AI dock receiver for a cold store. A receiving clerk photographs a paper delivery note, the AI reads it, the backend compares it with the purchase order, and the clerk sees what is wrong before the truck leaves. One tap to confirm, one tap to dispute. Capture keeps working with no signal.

- **Mobile app** (Expo, React Native, TypeScript): capture, review, correct, confirm or dispute, with an offline upload queue.
- **Backend** (Spring Boot 3, Java 17): purchase orders, receipts, the confirm and dispute lifecycle, and the AI reader behind a swappable interface.
- **OCR bench** (`web/`, React and Vite): a one-page tool to check the AI reader without the app.

The rule the code follows: **the AI reads and explains, the code decides.** Discrepancies are computed in plain Java, never by the model.

## Repository layout

| Path | What it is |
|---|---|
| `backend/` | Spring Boot API, H2 file database, Dockerfile, smoke test script |
| `mobile/` | Expo app. Its own [README](mobile/README.md) covers the emulator setup |
| `web/` | OCR bench, React and Vite |
| `kit/` | Seed POs, nine test delivery notes, fixtures, and the tool that draws the notes |
| `docs/ARCHITECTURE.md` | Diagrams: pieces, sequence from photo to database, lifecycle, scale |
| `CONTRACT.md` | The API contract between backend and app. The source of truth for paths, payloads and errors |
| `DECISIONS.md` | Design answers: why the logic is in code, failure handling, idempotency, what was cut |
| `AI-LOG.md` | How AI tools were used, and where they were wrong |

## Quick start

You need JDK 17, Node.js, and Git. The mobile app also needs an Android emulator or a phone with Expo Go.

```bash
git clone <this-repo-url> DockMind
cd DockMind
```

### 1. Start the backend

```bash
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

It listens on `http://localhost:8080` with the fixture reader, so it needs no API key. The seed POs load on first start.

Check it:

```bash
curl http://localhost:8080/health          # {"status":"UP"}
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI document: http://localhost:8080/v3/api-docs

To use the real vision model, copy the example settings and add a Gemini key:

```bash
cp backend/.env.example backend/.env
# edit backend/.env: set LLM_API_KEY and EXTRACTOR_TYPE=llm
```

`backend/.env` is gitignored. Never commit a real key.

### 2. Run the mobile app

```bash
cd mobile
npm install
npx expo start --go --port 8081
```

By default the app talks to the hosted backend. To point it at a server on your PC, set `EXPO_PUBLIC_API_URL`. From the Android emulator, the host machine is `10.0.2.2`:

```bash
EXPO_PUBLIC_API_URL=http://10.0.2.2:8080 npx expo start --go --port 8081
```

Restart with `npx expo start -c` after changing that variable. For the emulator, the `adb reverse` steps are in [mobile/README.md](mobile/README.md).

### 3. Run the OCR bench (optional)

From the repository root:

```bash
npm run webpreview
```

This installs the web dependencies and starts Vite. The page uploads a note and shows the photo beside what the server read. Vite forwards `/api/local` to `localhost:8080` and `/api/hosted` to the hosted server, so the backend needs no CORS setup.

## How a receipt moves

```
photo -> POST /receipts (Idempotency-Key)            202 CAPTURED
      -> background read (fixture or Gemini)
      -> validate: PO exists and is open, supplier agrees
      -> link lines to PO lines by description
      -> match: MATCH | SHORT | OVER | UNIT_MISMATCH | UNKNOWN_ITEM | NOT_DELIVERED
      -> EXTRACTED, or NEEDS_MANUAL if anything is unclear
      -> clerk corrects a quantity, then confirms or disputes
```

A receipt can end in `NEEDS_MANUAL` for several reasons: the model failed, the output was garbage, a quantity is unreadable, or the PO cannot be resolved. In that state the photo is kept and the clerk can assign a PO, correct lines and decide. The full sequence and state diagrams are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

Idempotency is enforced in three places: the phone generates the key once at capture and reuses it on every retry, the database has a unique constraint on the key, and a race on that constraint returns the existing receipt.

## Configuration

Backend settings come from environment variables, or from `backend/.env` when running locally.

| Variable | Default | Purpose |
|---|---|---|
| `EXTRACTOR_TYPE` | `fixture` | `fixture` returns the kit's canned answers. `llm` reads the photo with Gemini |
| `LLM_API_KEY` | none | Gemini key, needed when `EXTRACTOR_TYPE=llm` |
| `LLM_MODEL` | `gemini-3.1-flash-lite` | Any vision-capable Gemini model |
| `PORT` | `8080` | Port to listen on. Hosts such as Render set this |
| `DOCKMIND_DATA` | `./data` | Folder for the H2 database files |
| `matching.review-threshold` | `0.80` | Confidence below this is flagged for review (set in `application.properties`) |

## Testing

Backend unit and API tests:

```bash
cd backend
./mvnw test
```

End-to-end check against a running server, using the fixture reader and the kit's three delivery notes:

```bash
backend/scripts/smoke.sh                       # defaults to http://localhost:8080
backend/scripts/smoke.sh https://<your-host>   # or any other base URL
```

Mobile:

```bash
cd mobile
npm test              # Jest: queue order, retry with the same key, recovery after a kill
npx tsc --noEmit      # typecheck
npx expo lint
```

Web:

```bash
cd web
npm run typecheck
npm run build
```

## Docker

The backend has a Dockerfile. Build from the repository root, because the image packages `kit/`:

```bash
docker build -f backend/Dockerfile -t dockmind-backend .
docker run -p 8080:8080 -e EXTRACTOR_TYPE=fixture dockmind-backend
```

Keys and settings are passed as environment variables, never baked into the image. The image is sized for a 512 MB container.

## Deploying the backend

The backend is set up to deploy to Render from `backend/Dockerfile`. Set the environment variables in the service's Environment tab, and Render provides HTTPS. Two limits on the free tier:

- The H2 file does not survive a restart, so the seed POs load again and receipts are lost.
- The API has no login, as the brief scoped out. Keep a hosted URL private.

## Known limits

- **Storage:** embedded H2 on one server, with photos stored as blobs. Postgres and object storage are the next step for several servers and durable data.
- **Background reads:** an in-memory pool of two threads, about 20 to 30 notes a minute. Work is not shared across servers.
- **Supplier search:** `q=` is a contains match. Fine at 100,000 POs, but full-text search would be the next step.
- **No authentication.** The clerk is hard-coded as `clerk-01`, as the brief asks.

More in [DECISIONS.md](DECISIONS.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## API

The full list, payloads and error codes are in [CONTRACT.md](CONTRACT.md). In short:

| # | Method and path | Purpose |
|---|---|---|
| A1 | `GET /purchase-orders?status=OPEN&q=` | Open POs with lines, paged and searchable |
| A2 | `GET /receipts?status=` | Receipts, newest first |
| A3 | `GET /receipts/{id}` | One receipt, polled after capture |
| A4 | `POST /receipts` | Upload a photo with `Idempotency-Key`. Returns 202 new, 200 on replay |
| A5 | `PATCH /receipts/{id}/lines/{lineId}` | Clerk corrects a quantity |
| A6 | `POST /receipts/{id}/confirm` | Confirm. Safe to repeat |
| A7 | `POST /receipts/{id}/dispute` | Dispute with a reason. Safe to repeat |
| A8 | `PATCH /receipts/{id}` | Clerk assigns a PO when it could not be resolved |
| A9 | `GET /receipts/{id}/image` | The stored photo, the proof of delivery |
