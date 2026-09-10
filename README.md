# Albumy — Event Photo-Sharing App

Full-stack photo sharing for events. Organizers create an event, share a single QR code or link, and every guest can instantly drop their photos and videos into a shared album — **no sign-up required**. Uploads are chunked and resumable, transcode in the background on a dedicated worker, and appear in everyone's gallery **live** over WebSockets. The complete gallery is also available as a read-only public link where visitors can save photos straight to their phone's gallery at full quality; organizers and admins can grab the whole album as a ZIP.

Built with **Spring Boot + Angular + Redis**, containerized with **Docker Compose**, and shipped as a native Android app via **Capacitor**.

---

## The problem

Weddings, parties, and conferences produce hundreds of photos spread across dozens of phones and group chats. Guests default to the organizer's phone for "all the photos", and the gallery ends up scattered, low-res, or lost. There is no quick, frictionless way for a crowd to pool their media into one shared, high-quality album.

## The solution

Albumy gives every event a dedicated, no-account guest page:

- Guests scan a QR code (or open a link / a `albumy://` deep link) and pick a pseudo — nothing else.
- They drop in photos and videos (even large files); uploads are **split into 5 MB chunks, retry on flaky networks, survive a page refresh, and pause/resume when offline**.
- Photos are processed in the background — responsive image variants plus H.264 video + poster frame — and the gallery updates **live** for everyone watching.
- The organizer opens a curated dashboard with the full gallery — every upload tagged with who sent it — plus QR/share/download tooling, and can delete or download everything as a ZIP.

---

## Key features

| Area | Features |
|---|---|
| **Guest onboarding** | No account — unique pseudo per event (server-verified), remembered in `localStorage`; guest token issued at claim, sent as `X-Guest-Token` |
| **Robust uploads** | 5 MB chunked uploads (up to 2 GB per file), resume-from-server (only missing chunks are resent), 3× retry with backoff, IndexedDB-persisted queue that survives reloads, offline pause + auto-resume, 3 uploads in flight |
| **Camera & size control** | Dedicated camera capture button; **Fast** mode compresses to ≤1920 px JPEG before upload, **Original** sends untouched files |
| **Media pipeline** | Dedicated Redis-backed worker transcodes in the background: `_thumb`/`_med`/`_full` JPEG tiers (320/960/1920), H.264 `_web.mp4` + `_poster.jpg` for video, EXIF capture-date/orientation; up to 3 attempts then marked `ERROR` |
| **Live updates** | Uploads and transcriptions push `PHOTO_ADDED` / `PHOTO_READY` / `PHOTO_REMOVED` / `ACTIVITY` messages: Redis pub/sub → STOMP WebSocket → **instant updates** in guest list, full album, organizer gallery (live pill + activity feed) and dashboard (toast + cover refresh) |
| **Outreach** | Per-event guest URL + QR code (downloadable PNG / printable) with Web-Share + clipboard copy, and a separate read-only **full album** link w/ deep links (`albumy://open?code=...`); visitors save any photo — single, **Download all**, or selected — at original quality, no ZIP |
| **Roles & access** | `ADMIN`, `ORGANIZER`, anonymous guest, read-only visitor; single-use 7-day invite links for organizer registration |
| **Organizer** | Per-event gallery with uploader tags, paginated (cursor `beforeId`/`limit`), delete photos/events, **edit event details**, **set a cover photo**, download the whole album as a ZIP; dashboard shows cover thumbnails + live toast |
| **UI/UX** | Modern violet/pink design system, fully responsive (mobile-first), QR codes, lightbox galleries, upload progress tray, batch-download progress bar, relative timestamps (…ago), empty/loading/error states, SVG icon set |
| **Deployment** | Docker Compose stack: MySQL + Redis + Spring Boot (web) + Spring Boot (worker) + Nginx/Angular + phpMyAdmin, persistent volumes |
| **Mobile** | Native Android wrapper via Capacitor 8 (APK buildable without any signing setup) |

---

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 4.1, Spring Security (JWT), Spring Data JPA (Hibernate), Spring WebSocket (STOMP) + Spring Data Redis |
| Frontend | Angular 20 (standalone components), @stomp/stompjs, idb-keyval, angularx-qrcode 20 |
| Database | MySQL 8.4 (`mysql:8.4`) |
| Cache/messaging | Redis 7 (`redis:7-alpine`, append-only persistence) |
| Media worker | Same Spring Boot jar, `worker` profile (web server off), ImageIO tiers + ffmpeg |
| Web server | Nginx 1.27 (SPA, gzip, `/api` reverse proxy, `/ws` upgrade, 210 MB body cap) |
| Mobile | Capacitor 8 (@capacitor/core, cli, android, app) |
| Auth | JWT (24 h), BCrypt password hashing, single-use 7-day invites |
| Storage | Local disk (`uploads/`) via a Docker volume |

---

## Architecture

```
                  ┌──────────────────────────────────────────────────────────┐
                  │                      docker compose                       │
                  │                     ┌───────────────┐      ┌──────────────┐ │
        :6379     │                     │     Redis     │◀────▶│    worker    │ │
        ┌───┐     │  ┌───────────────┐  │  job queue·pub │ jobs │  media jobs  │ │
        └───┘     │  │               │  └───────▲───────┘      └──────▲───────┘ │
  :8081  ┌────────▼──▼─────┐   /api/  │         │ events                │        │
 Browser │    frontend     │─────────│────────▶│────────│─────────│    │        │
   QR    │  Nginx+Angular   │  /ws    │  backend│         │            │        │ :3306
  (guest)└─────────────────┘◀────◀───│───STOMP──┘         │            │  ┌─────▼─────┐
        ┌───────────────┐            └────────────────────┴────────────┘  │   MySQL   │
        │  Android app   │ ◀-- http://10.0.2.2:8080 (or LAN IP)          │   8.4     │
        └───────────────┘                       │                         └───────────┘
                                 /app/uploads (shared volume)
```

How the pieces talk:

- **Browser → frontend**: always same-origin. `/api/...` is proxied to the backend (dev server `proxy.conf.json`; Nginx in production), `/ws` is upgraded for STOMP. Direct backend URL is `http://localhost:8080` (no `/api` prefix).
- **Chunked upload**: `POST /uploads` creates an upload session in Redis (24 h TTL); chunks are `PUT` as raw bytes; `GET /uploads/{id}/chunks` returns what's already stored for a clean resume; `POST /uploads/{id}/complete` assembles the file, deduplicates by SHA-256, and drops a job on the Redis `media:jobs` queue.
- **Worker**: the same jar with `SPRING_PROFILES_ACTIVE=worker` (web server off, `DataInitializer`/`WebSocketConfig`/`RealtimeBridgeConfig` excluded). It `BRPOP`s jobs and runs the media pipeline (ImageIO + metadata-extractor + ffmpeg), writing derivatives into the **same `/app/uploads` volume** so the web backend can serve them immediately.
- **Realtime**: any new/ready/removed photo publishes JSON on the Redis channel `albumy:events`; the web backend bridges it to STOMP topic `/topic/events/{eventId}/photos` (plus `activity` and `stats`); clients subscribe per event and update UI in place.
- **Android app**: built with `--configuration capacitor`, base URL `http://10.0.2.2:8080/api` (emulator → host). On a physical device point `environment.capacitor.ts` at your LAN IP. Deep links (`albumy://open?code=...`) navigate straight to the event.
- Hibernate `ddl-auto=update` creates the schema on first boot (no Flyway); `DataInitializer` seeds demo data when missing and re-seeds photos for the demo event if its gallery is empty.

---

## Repository structure

```
albumy/
├── docker-compose.yml            # full stack: mysql, redis, backend, worker, frontend, phpmyadmin
├── .env.example                  # template for .env (JWT_SECRET required, rest optional)
├── albumy_backend/               # Spring Boot application (web + worker in one jar)
│   ├── Dockerfile                # Maven build → JRE 21 runtime (ffmpeg/curl, non-root user)
│   └── src/main/
│       ├── java/com/mmea/albumy/
│       │   ├── config/           # DataInitializer (seed), WebSocketConfig, RealtimeBridgeConfig
│       │   ├── controller/       # REST controllers (auth, events, guests, uploads, files...)
│       │   ├── dto/              # request/response DTOs
│       │   ├── exception/        # ApiException + global JSON error handler
│       │   ├── model/            # JPA entities (User, Invite, Event, Guest, Photo)
│       │   ├── repository/       # Spring Data repositories (incl. cursor + dedupe queries)
│       │   ├── security/         # JWT filter/util, UserDetailsService, SecurityConfig
│       │   └── service[impl]/    # services + media queue/processor/pipeline/real-time
│       └── resources/application*.properties
└── albumy_frontend/              # Angular 20 application
    ├── Dockerfile                # node build → nginx serve
    ├── nginx.conf                # SPA fallback + /api reverse proxy + /ws upgrade
    ├── proxy.conf.json           # dev-server /api + /ws → localhost:8080
    ├── capacitor.config.json     # Capacitor app/webDir config
    ├── android/                  # generated Android project (Capacitor)
    └── src/
        ├── environments/         # dev/prod/capacitor API base URLs
        ├── styles.css            # global design system + responsive breakpoints
        └── app/
            ├── components/       # login, register, dashboard, event-detail, guest, full-album
            ├── services/         # auth, event, upload-queue, realtime, deep-link
            ├── shared/icon/      # reusable SVG icon component
            ├── config/api.config.ts
            └── utils/image.ts    # client-side compression (Fast uploads)
```

---

## Run with Docker (recommended)

Prerequisite: **Docker Desktop** (or Docker Engine + Compose). That's all — Java, Node and Maven are not needed for this path.

```bash
git clone <your-repo-url> && cd albumy
cp .env.example .env
# generate a JWT signing key and put it in .env (see .env.example):
openssl rand -hex 32
docker compose up -d --build
```

First build compiles the backend (Maven in Docker, includes the worker) and the frontend (npm in Docker), then starts:

| Service | URL | Notes |
|---|---|---|
| Frontend | http://localhost:8081 | the app |
| Backend API | http://localhost:8080 | `/api/...` proxied by Nginx |
| phpMyAdmin | http://localhost:8082 | MySQL UI (loopback-only; user `albumy`, password from `.env`) |
| Redis | localhost:6379 | internal (loopback-only + password-protected) |
| MySQL | localhost:3306 | internal (loopback-only) |

Demo data is created on first boot: an admin, an organizer, one `Demo Wedding` event with 7 photos, and a pending invite link.

Stop the stack: `docker compose down` (keeps data).  
Wipe everything including data volumes: `docker compose down -v`.

## Local development (without Docker)

```bash
# 1) MySQL 8 at localhost:3306 with a `albumy` database/user
#    (or point DB_URL/DB_USER/DB_PASSWORD at your instance).

# 2) Backend on :8080 (plus Redis at localhost:6379)
cd albumy_backend
set JWT_SECRET=<paste-your-generated-secret>   # Windows  (export JWT_SECRET=... on Linux/macOS)
mvnw.cmd spring-boot:run        # Windows        (./mvnw spring-boot:run on Linux/macOS)

# 3) Media worker (optional; without it photos stay PROCESSING)
#    Run a second instance with the worker profile, or docker compose up -d worker
set JWT_SECRET=<same-secret-as-above>
SPRING_PROFILES_ACTIVE=worker mvnw.cmd spring-boot:run

# 4) Frontend on :4200 (proxies /api and /ws to :8080 automatically)
cd ../albumy_frontend
npm install
npm start
```

## Configuration (`.env`)

Copy `.env.example` to `.env`. Every variable has a sensible default. Docker expands these for the containers; the backend reads them at runtime via Spring.

| Variable | Used by | Default |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | compose (MySQL) | `albumy_root` |
| `MYSQL_DATABASE` | compose (MySQL) | `albumy` |
| `MYSQL_USER` / `MYSQL_PASSWORD` | MySQL + backend datasource + phpMyAdmin | `albumy` / `albumy` |
| `REDIS_PASSWORD` | Redis broker (requirepass) | `albumy_redis` (loopback-only port) |
| `JWT_SECRET` | backend JWT signing key | **required** — generate with `openssl rand -hex 32`; the app refuses to boot with the placeholder (this repo is public) |
| `FRONTEND_URL` | backend (invite registration links) | `http://localhost:8081` |
| `CORS_ALLOWED_ORIGINS` | backend CORS (comma-separated) | web origins + `https://localhost`, `capacitor://localhost` |
| `UPLOAD_DIR` | backend file storage | `uploads` (inside container: `/app/uploads` volume) |

Backend-only settings live in `albumy_backend/src/main/resources/application.properties`: JWT expiration (`86400000` ms), invite validity (`7` days), chunk size (`5 MB`), max assembled file (`2 GB`), upload-session TTL (`24 h`), Redis keys (`media:jobs`, `albumy:events`), worker threads (`media.jobs.threads=2`), image tiers (`320,960,1920`). Every value has a working default **except `JWT_SECRET`**, which is enforced at startup.

## Demo accounts (fictional)

| Role | Username | Password |
|---|---|---|
| Admin | `demo_admin` | `demo_admin_password` |
| Organizer | `demo_organizer` | `demo_organizer_password` |

Seeded demo data (only when absent): the `Demo Wedding` event (code **`U1Z5WJ`**) with 7 placeholder photos under the pseudos `Amelie`, `Karim`, `Sam`, plus one unused invite link.

## Usage flow

1. **Admin creates an invite** — log in as `demo_admin`, dashboard → "Generate Invite Link".
2. **Organizer registers** — open the invite URL (`http://localhost:8081/register?invite=TOKEN`). Registration without a valid invite is rejected; the invite is single-use and expires after 7 days.
3. **Organizer creates an event** — name, date, start time. A 6-char guest code and a full-album token are generated. Details and the cover photo can be edited later from the event page.
4. **Guests join** — open `/e/CODE`, pick a unique pseudo (server-checked per event), get remembered automatically, and open the drop zone. No account needed.
5. **Guest uploads** — drop files or take a photo with the camera; choose **Fast** (compressed) or **Original**. Uploads queue, chunk up, survive reloads/offline, and appear in the gallery — the organizer's page and the public album update live.
6. **Outreach & sharing** — the organizer event page shows the guest link + QR (download PNG / print) and the public full-album link; everything can be shared via the Web Share API or copied. Visitors to the full album save any photo with one tap, or pick Select / **Download all** — each file downloads individually at its original quality (nothing is zipped on the public album).
7. **Organizer manages the album** — event detail lists every photo with its uploader; deletes individual photos or the whole event; downloads the entire album as a ZIP. Admins can download any event's ZIP.

## Android app (Capacitor)

```bash
cd albumy_frontend

# Build the web app for the emulator and copy it into the native project
npm run cap:sync            # ng build --configuration capacitor && cap sync android

# Open in Android Studio and run / build
npm run cap:open            # syncs, opens Android Studio
```

Or build an APK from the command line:

```bash
npm run cap:sync
cd android
./gradlew assembleDebug     # app-debug.apk under android/app/build/outputs/apk/debug/
```

Details:

- The `capacitor` Angular configuration swaps in the `environment.capacitor.ts` base URL: `http://10.0.2.2:8080/api` — `10.0.2.2` is the Android emulator's alias for the host machine.
- The backend allows the Capacitor origins (`https://localhost`, `capacitor://localhost`) via `CORS_ALLOWED_ORIGINS`, and the manifest enables cleartext traffic for this dev setup.
- Deep links: the manifest registers the `albumy://open` scheme; opening `albumy://open?code=EVENTCODE` (optionally `&token=FULLTOKEN`) navigates straight to the guest page or full album.
- On a **physical device**, edit `src/environments/environment.capacitor.ts` to your machine's LAN IP (e.g. `http://192.168.1.20:8080/api`), re-run `npm run cap:sync`, and ensure the device and computer share the network.

## API overview

All responses are JSON with `{ "message": ... }` on error. Authenticated endpoints accept `Authorization: Bearer <JWT>`. Guests use the `X-Guest-Token` header obtained from `/claim`. Prefix everything with `http://localhost:8080` (or `/api` through the frontend).

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/auth/register` | public | Register organizer (valid `inviteToken` required) |
| POST | `/auth/login` | public | Login → JWT |
| GET | `/invites/{token}` | public | Validate invite (valid/used/expired) |
| POST | `/admin/invites` | ADMIN | Create single-use invite |
| GET | `/admin/invites` | ADMIN | List invites with status |
| POST | `/events` | ORGANIZER | Create event |
| GET | `/events` | authenticated | List events (ADMIN: all) |
| GET | `/events/{id}?beforeId=&limit=` | owner/ADMIN | Event detail, paginated photos, live-tail support |
| PUT | `/events/{id}` | owner/ADMIN | Update event (name, date, start time) |
| POST | `/events/{id}/cover` | owner/ADMIN | Set/replace the event cover photo |
| DELETE | `/events/{id}` | owner/ADMIN | Delete event + files |
| DELETE | `/events/photos/{photoId}` | owner/ADMIN | Delete one photo (publishes PHOTO_REMOVED) |
| GET | `/events/{id}/photos/zip` | owner/ADMIN | Stream all photos as a ZIP |
| GET | `/events/code/{eventCode}` | public | Guest event info |
| GET | `/events/code/{eventCode}/name-available?name=X` | public | Check pseudo availability |
| POST | `/events/code/{eventCode}/claim` | public | Claim pseudo → `guestToken` |
| POST | `/events/code/{eventCode}/photos` | public | Legacy multipart upload (kept for compat) |
| GET | `/events/code/{eventCode}/photos?beforeId=&limit=` | public | One pseudo's uploads (X-Guest-Token or uploaderName) |
| POST | `/uploads` | public | **Init chunked upload** → `{ uploadId, chunkSize }` |
| PUT | `/uploads/{uploadId}/chunks/{index}` | public | Upload one raw chunk (octet-stream) |
| GET | `/uploads/{uploadId}/chunks` | public | Received chunk indexes (resume) |
| POST | `/uploads/{uploadId}/complete` | public | Assemble, dedupe, enqueue media job → `PhotoResponse` |
| GET | `/events/full/{fullAlbumToken}?beforeId=&limit=` | public | Full album (read-only, separate token) |
| GET | `/files/{fileName}` | public | Stream an uploaded file/variant |
| WS  | `/ws` | public | STOMP endpoint → `/topic/events/{id}/photos\|activity\|stats` |

`PhotoResponse` payload (used by the REST list and pushed over WebSocket): `id`, `uploaderName`, `fileName`, `fileUrl`, `thumbUrl`, `medUrl`, `fullUrl`, `posterUrl`, `webUrl`, `video`, `duration`, `width`, `height`, `size`, `status`, `captureDate`, `uploadedAt`.

Event responses include `coverUrl` (`/files/...`, null until a cover is set), which also drives the guest and full-album page heroes.

## Data model

- **users** — username (unique), email, passwordHash (BCrypt), displayName, role (`ADMIN`/`ORGANIZER`), createdAt
- **invites** — token (UUID, unique), used, createdAt, expiresAt (7 days)
- **events** — name, date, startTime, eventCode (unique 6-char), fullAlbumToken (unique UUID), coverFileName (nullable), organizerId, createdAt
- **guests** — eventId + name, **unique per event** (one pseudo = one guest identity, many photos)
- **photos** — eventId, guestId, fileName (`UUID.ext`, collision-proof), `fileNameThumb`/`fileNameMed`/`fileNameFull`/`fileNameWeb`/`fileNamePoster`, mimeType, size, width, height, duration, sha256, status (`PROCESSING`/`READY`/`ERROR`), errorCount, captureDate, uploadedAt

`eventCode` and `fullAlbumToken` are globally unique; guests are unique per event; codes come from `SecureRandom` (non-sequential, non-guessable).

## Security notes

- Passwords BCrypt-hashed; never stored in clear text
- Invite & full-album tokens are UUIDs; guest codes use `SecureRandom`
- Registration without a valid invite is rejected server-side
- Guests only read their own uploads (guarded by `X-Guest-Token`); delete/ZIP operations check ownership (owner or ADMIN)
- Minimal public surface: only auth, invite validation, guest/full-album routes, upload/chunk endpoints, `/files`, `/ws` and health are unauthenticated; everything else requires a JWT with the right role
- Backend runs as a non-root user inside the container
- `JWT_SECRET` is **required** and enforced at startup: the backend refuses to boot with a missing, short, or publicly-known placeholder key (the repo is public). Set it via `.env` — `openssl rand -hex 32`
- Redis, MySQL and phpMyAdmin are published **loopback-only** (`127.0.0.1`), and Redis additionally requires a password — nothing DB/broker-side is reachable from the LAN; only the app ports (`:8080`, `:8081`) are open for phone testing
- Public endpoints are rate-limited (sliding window, `app.rate-limit.*` in `application.properties`): login/register, event-code probing (`/events/code/*`), name-available, claim, and upload init/complete. Login is keyed both per client IP and per username, so a distributed brute force can't hammer one account
- STOMP `/ws` subscriptions are **authenticated**: subscribing to `/topic/events/{id}/…` requires either the organizer's/admin's JWT, or a short-lived, HMAC-signed **realtime ticket** that is returned only with the event's guest info or full-album response. An anonymous socket can no longer stream a photo feed just by guessing the numeric event id
- Registration: passwords must be 8–64 chars with at least one letter and one number, and the invite is burned via an atomic conditional update **after** the account saves — concurrent registrations can't double-use an invite, and a failed save never consumes it
- **JWT is stored in `localStorage`** (deliberate: the Capacitor app needs the bearer token across native HTTP requests, and HttpOnly cookies can't be set cross-origin for it). The realistic theft vector—stored SVG/HTML that runs in the app—is closed: only safe image/video types are accepted (`MediaFileTypes`), `/files` serves with `nosniff` + `Content-Security-Policy: sandbox`, and Angular's interpolation never renders raw HTML
- DB passwords and demo accounts are dev-only — change them in any non-demo deployment

## Storage & performance

- Files persist in the `albumy_uploads` volume (survives `docker compose down`; wiped only by `down -v`); original + up to 5 derivatives per upload
- Chunked uploads write to a `tmp/` under the upload dir and are moved into place on `complete`; assembled files can reach 2 GB without loading into memory
- ZIP downloads are streamed via a temp file with a chunked buffer — hundreds of photos don't exhaust server memory
- Galleries paginate with cursor keys (`beforeId`), so "Load more" stays cheap as galleries grow

## Troubleshooting

| Symptom | Fix |
|---|---|
| `docker compose up` leaves backend restarting | MySQL or Redis wasn't healthy yet; backend waits for both. `docker compose logs backend` for the real error. |
| Photos stuck `PROCESSING` | The worker isn't running (`docker compose ps` → `albumy-worker`). Restart it: `docker compose up -d worker`. |
| Old tables / migration left-overs | `docker compose down -v && docker compose up -d --build` (wipes MySQL + uploads, re-seeds demo data). |
| Frontend can't reach the API | Nginx proxies `/api` → backend at `:8080`; check `docker compose ps` shows `albumy-backend` up. |
| Upload rejected | >2 GB, or a disallowed file type; the server returns a JSON `message` surfaced in the queue tray. |
| Android app can't load the API | Emulator: target `10.0.2.2:8080`. Physical device: change `environment.capacitor.ts` to your LAN IP and re-sync. |
| Live updates not appearing | Check the STOMP connection (browser network tab → `ws://…/ws`). The `/ws` location in `nginx.conf` must match exactly. |
| Backend build slow in Docker | Maven dependency cache is not persisted; subsequent runs reuse layers until the build context changes. |

## License

For demonstration purposes.