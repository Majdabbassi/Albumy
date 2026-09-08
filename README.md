# Albumy - Event Photo-Sharing App

A full-stack event photo-sharing application. Organizers create events and collect every photo taken by their guests — without the guests needing an account. The complete gallery is viewable via a read-only public link and downloadable as a ZIP.

## What is implemented

- **Admin** — generates single-use, time-limited invite links that organizers need to register
- **Organizer** — registers only through an admin invite, creates events, sees the full gallery per event (with who uploaded what), deletes photos/events, downloads everything as a ZIP
- **Guest** — no account required: picks a unique pseudo per event (checked on the server), uploads photos/videos, sees only their own uploads
- **Visitor** — the full album read-only via a separate public link (distinct token from the guest code, no upload)

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 4.1.0, Spring Security (JWT), Spring Data JPA |
| Frontend | Angular 20 (standalone components), angularx-qrcode |
| Database | PostgreSQL 16 |
| Storage | Local disk (`uploads/`) |
| Auth | JWT (24h), BCrypt password hashing |

## Prerequisites

- Java 21
- Node.js 20+
- Maven (the included `mvnw.cmd` wrapper downloads it)
- Docker (for PostgreSQL)

## Configuration (`.env`)

Copy `.env.example` to `.env` and adjust if needed. The defaults are chosen so the stack runs out of the box on a fresh clone — no missing variables.

All values are read via environment variables:

| Variable | Used by | Default |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | docker-compose | `albumy` / `albumy` / `albumy` |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Spring Boot datasource | `jdbc:postgresql://localhost:5432/albumy` / `albumy` / `albumy` |
| `JWT_SECRET` | JWT signing key | dev-only value (change in production) |
| `UPLOAD_DIR` | File storage location | `uploads` |
| `FRONTEND_URL` | Base URL used to build invite registration links | `http://localhost:4200` |

Other settings live in `albumy_backend/src/main/resources/application.properties`: JWT expiration (`86400000` ms), invite validity (`invite.expiration.days=7`), and the per-file upload limit (`spring.servlet.multipart.max-file-size=25MB`, request cap `110MB`).

## Setup / run

### 1. Start PostgreSQL

```bash
docker compose up -d        # or: docker-compose up -d
```

### 2. Start the backend

```bash
cd albumy_backend
mvnw.cmd spring-boot:run    # Windows | ./mvnw spring-boot:run on Linux/macOS
```

Backend listens on `http://localhost:8080`. On first boot it creates the schema and the demo seed data.

### 3. Start the frontend

```bash
cd albumy_frontend
npm install
npm start
```

Frontend listens on `http://localhost:4200`.

## Demo accounts (fictional)

Created on first boot — clearly fictional, demo-only credentials:

| Role | Username | Password |
|---|---|---|
| Admin | `demo_admin` | `demo_admin_password` |
| Organizer | `demo_organizer` | `demo_organizer_password` |

Demo data seeded at startup (only if absent):
- `Demo Wedding` event owned by the demo organizer, with 7 placeholder photos uploaded under the fictional guest pseudos `Amelie`, `Karim`, `Sam`
- One pending, unused invite link (visible in the admin dashboard)

## Usage flow

1. **Admin generates an invite** — log in as `demo_admin`, open the dashboard, click "Generate Invite Link", copy the URL.
2. **Organizer registers** — open the invite URL (e.g. `http://localhost:4200/register?invite=TOKEN`). The page validates the invite; registration without a valid invite returns 400. The invite is single-use and expires after 7 days.
3. **Organizer creates an event** — name, date, start time. A 6-character guest code and a full-album token are generated automatically.
4. **Share with guests** — the event detail page shows the guest URL (with QR code) and the public full-album link. Guests and visitors need no account.
5. **Guest uploads** — opens `/e/CODE`, picks a unique pseudo (server-checked per event), uploads photos/videos (max 25 MB each), sees only their own uploads.
6. **Organizer manages the gallery** — event detail shows every photo and its uploader; individual photos and whole events can be deleted; all photos download as a single ZIP. Admins can download any event's ZIP too.

Note: the demo organizer account is seeded directly (an organizer is normally created by consuming an invite). The invite flow itself is fully functional as described in step 2.

## API endpoints

All responses are JSON with `{ "message": ... }` on error. Authenticated endpoints accept `Authorization: Bearer <JWT>`.

### Auth & invites
| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/auth/register` | public | Register an organizer; `inviteToken` in body is required and is consumed on success |
| POST | `/auth/login` | public | Login, returns JWT |
| GET | `/invites/{token}` | public | Validate an invite (valid/used/expired) |
| POST | `/admin/invites` | ADMIN | Create a single-use invite, returns token + registration URL |
| GET | `/admin/invites` | ADMIN | List invites with status |

### Events
| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/events` | ORGANIZER | Create event |
| GET | `/events` | Authenticated | List own events (ADMIN: all events) |
| GET | `/events/{id}` | Owner/ADMIN | Event detail with full photo list |
| DELETE | `/events/{id}` | Owner/ADMIN | Delete event and its files |
| DELETE | `/events/photos/{photoId}` | Owner/ADMIN | Delete one photo |
| GET | `/events/{id}/photos/zip` | Owner/ADMIN | Download all photos as a streamed ZIP |

### Guest (public, no account)
| Method | Path | Purpose |
|---|---|---|
| GET | `/events/code/{eventCode}` | Event info (name, date, start time) |
| GET | `/events/code/{eventCode}/name-available?name=X` | Check pseudo availability for this event |
| POST | `/events/code/{eventCode}/photos` | Upload (multipart `file`; `uploaderName` required). Accepts `image/*`, `video/mp4`, `video/quicktime`, `video/webm`; max 25 MB |
| GET | `/events/code/{eventCode}/photos?uploaderName=X` | List photos for one pseudo only |

### Full album (public, read-only)
| Method | Path | Purpose |
|---|---|---|
| GET | `/events/full/{fullAlbumToken}` | Full gallery (all uploads). Token differs from the guest code |

### File serving
| Method | Path | Purpose |
|---|---|---|
| GET | `/files/{fileName}` | Stream an uploaded file |

## Data model

- **users** — username (unique), email, passwordHash (BCrypt), displayName, role (`ADMIN` / `ORGANIZER`), createdAt
- **invites** — token (UUID, unique), used, createdAt, expiresAt (7 days)
- **events** — name, date, startTime, eventCode (unique 6-char), fullAlbumToken (unique UUID), organizerId, createdAt
- **guests** — eventId + name, **unique per event** (one pseudo = one guest identity, many photos)
- **photos** — eventId, guestId, fileName (`UUID.ext`, collision-proof), uploadedAt

Constraints of note: `eventCode` and `fullAlbumToken` are globally unique; `(event_id, name)` on guests is unique so a pseudo can only be claimed once per event. Codes are generated with `SecureRandom` (not sequential, not guessable).

## Security notes

- Passwords are BCrypt-hashed, never stored in clear text
- Invite tokens and full-album tokens are UUIDs; guest codes use `SecureRandom`
- Registering without a valid invite is rejected server-side
- A guest can only read their own uploads; delete/ZIP operations check ownership (owner or ADMIN)
- Uploads are limited to 25 MB and to image/video content types
- `JWT_SECRET` must be overridden with a strong value in any non-demo deployment

## Storage

- Files live in `uploads/` (configurable via `UPLOAD_DIR`)
- ZIP downloads are streamed to a temp file with a chunked buffer — hundreds of photos do not exhaust server memory

## Development

Backend layout (`albumy_backend/src/main/java/com/mmea/albumy/`):

```
config/      # Security-independent config, demo seed (DataInitializer)
controller/  # REST controllers
dto/         # Request/response DTOs
exception/   # ApiException + global JSON error handler
model/       # JPA entities (User, Invite, Event, Guest, Photo)
repository/  # Spring Data repositories
security/    # JWT filter/util, UserDetailsService, SecurityConfig
service/     # Service interfaces
serviceimpl/ # Implementations
```

Frontend layout (`albumy_frontend/src/app/`):

```
components/  # login, register, dashboard, event-detail, guest, full-album
services/    # auth.service.ts, event.service.ts
app.routes.ts
```

Run the backend test suite:

```bash
cd albumy_backend
mvnw.cmd test    # requires PostgreSQL running
```

## Troubleshooting

- **Backend won't start / can't reach DB** — confirm `docker compose ps` shows the Postgres container up, check credentials in `.env` match `application.properties`.
- **Wrong StateSchema / stale tables** — the schema evolves on new runs. For a fully fresh database: `docker compose down -v` then `docker compose up -d` (this wipes the Postgres volume and re-seeds demo data).
- **Frontend can't reach the API** — the frontend calls `http://localhost:8080`; CORS allows `http://localhost:4200` and `http://localhost:4201`.
- **Upload rejected** — files over 25 MB or non-image/video types get a clear error message; the browser console shows the server's JSON `message`.

## License

For demonstration purposes.