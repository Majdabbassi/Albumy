# Albumy - Event Photo-Sharing App

A full-stack event photo-sharing application where organizers can create events, guests can upload photos with unique display names, and organizers can manage the full gallery.

## Features

- **Admin**: Generates invite links for organizer registration
- **Organizer**: Creates and manages events, views full photo galleries, downloads all photos as ZIP
- **Guest**: No account required - picks a unique display name per event, uploads photos, views only their own uploads
- **Full Album**: Public link to view all photos in an event (read-only)

## Tech Stack

### Backend
- Spring Boot 4.1.0
- Spring Security (JWT authentication)
- Spring Data JPA
- PostgreSQL
- Local disk file storage

### Frontend
- Angular 20
- angularx-qrcode (QR code generation)
- Standalone components

## Prerequisites

- Java 21
- Node.js 20+
- Maven
- Docker (for PostgreSQL)

## Setup Instructions

### 1. Start PostgreSQL

Run PostgreSQL using Docker Compose:

```bash
docker-compose up -d
```

This will start PostgreSQL on port 5432 with:
- Database: `albumy`
- Username: `albumy`
- Password: `albumy`

### 2. Start the Backend

Navigate to the backend directory:

```bash
cd albumy_backend
```

Run the Spring Boot application:

```bash
./mvnw spring-boot:run
```

Or on Windows:

```bash
mvnw.cmd spring-boot:run
```

The backend will start on `http://localhost:8080`

**Default Admin Credentials:**
- Username: `admin`
- Password: `admin123`

### 3. Start the Frontend

Navigate to the frontend directory:

```bash
cd albumy_frontend
```

Install dependencies:

```bash
npm install
```

Start the Angular development server:

```bash
npm start
```

The frontend will start on `http://localhost:4200`

## Usage Flow

### 1. Generate Invite Link (Admin)
1. Login as admin (`admin` / `admin123`)
2. Click "Generate Invite Link" on the dashboard
3. Copy the registration URL

### 2. Register Organizer
1. Open the invite link (e.g., `http://localhost:4200/register?invite=TOKEN`)
2. Fill in registration details (username, password, display name, email optional)
3. Submit to create organizer account

### 3. Create Event (Organizer)
1. Login as organizer
2. Click "+ Create Event" on dashboard
3. Enter event name, date, and time
4. Event code and full album token are auto-generated

### 4. Share with Guests (Organizer)
1. View event details
2. Share the guest URL or QR code with guests
3. Share the full album link for anyone to view all photos

### 5. Guest Upload
1. Guest opens guest URL (e.g., `http://localhost:4200/e/CODE`)
2. Picks a unique display name (validated against backend)
3. Uploads photos/videos
4. Views only their own uploads

### 6. Manage Photos (Organizer)
1. View event dashboard
2. See all photos from all guests
3. Delete individual photos
4. Download all photos as ZIP

## API Endpoints

### Auth & Invites
- `POST /admin/invites` - Generate invite link (Admin only, JWT protected)
- `GET /invites/{token}` - Validate invite token (public)
- `POST /auth/register` - Register organizer (public, requires valid invite)
- `POST /auth/login` - Login (public)

### Events (Organizer, JWT protected)
- `POST /events` - Create event
- `GET /events` - List organizer's events
- `GET /events/{id}` - Get event details with full photo list
- `DELETE /events/{id}` - Delete event
- `DELETE /events/photos/{photoId}` - Delete photo
- `GET /events/{id}/photos/zip` - Download all photos as ZIP

### Guest Access (public)
- `GET /events/code/{eventCode}` - Get event public info
- `GET /events/code/{eventCode}/name-available?name=X` - Check name availability
- `POST /events/code/{eventCode}/photos` - Upload photo
- `GET /events/code/{eventCode}/photos?uploaderName=X` - Get photos by uploader

### Full Album (public)
- `GET /events/full/{fullAlbumToken}` - Get full album (all photos)

### File Serving
- `GET /files/{fileName}` - Serve uploaded files

## Database Schema

### Users
- `id` - Primary key
- `username` - Unique login identifier
- `email` - Optional
- `passwordHash` - BCrypt encrypted
- `displayName` - Display name
- `createdAt` - Timestamp
- `role` - ADMIN or ORGANIZER

### Events
- `id` - Primary key
- `name` - Event name
- `date` - Event date
- `startTime` - Event start time
- `eventCode` - Unique 6-character code for guest access
- `fullAlbumToken` - Unique UUID for full album access
- `organizerId` - Foreign key to User
- `createdAt` - Timestamp

### Photos
- `id` - Primary key
- `eventId` - Foreign key to Event
- `uploaderName` - Guest's display name (unique per event)
- `fileName` - Stored file name
- `uploadedAt` - Timestamp

**Database Constraint:** `uploaderName` must be unique within each event (enforced at DB level)

### Invites
- `id` - Primary key
- `token` - Unique UUID
- `used` - Boolean (default false)
- `createdAt` - Timestamp
- `expiresAt` - Expiry timestamp (7 days from creation)

## Security

- JWT tokens for authentication (24-hour expiry)
- BCrypt password hashing
- Role-based access control (ADMIN, ORGANIZER)
- CORS configured for Angular dev server (localhost:4200)
- Invite tokens are single-use and expire after 7 days

## File Storage

- Uploaded files are stored in the `uploads/` directory in the backend
- Files are served via `/files/{fileName}` endpoint
- ZIP downloads are streamed to avoid memory issues

## Development

### Backend Structure
```
albumy_backend/src/main/java/com/mmea/albumy/
├── config/          # Security config, data initializer
├── controller/      # REST controllers
├── dto/            # Data transfer objects
├── model/          # JPA entities
├── repository/     # JPA repositories
├── security/       # JWT utilities, filters
└── AlbumyApplication.java
```

### Frontend Structure
```
albumy_frontend/src/app/
├── components/     # Angular components
├── services/       # API services
├── app.config.ts   # App configuration
├── app.routes.ts   # Route definitions
└── app.ts          # Root component
```

## Troubleshooting

### Backend won't start
- Ensure PostgreSQL is running: `docker-compose ps`
- Check database credentials in `application.properties`
- Verify port 8080 is not in use

### Frontend can't connect to backend
- Ensure backend is running on port 8080
- Check CORS configuration in `SecurityConfig.java`
- Verify API URL in Angular services (`http://localhost:8080`)

### Photos not uploading
- Check `uploads/` directory exists in backend
- Verify file permissions
- Check browser console for errors

### Name uniqueness not enforced
- Database constraint should handle this
- Check Photo entity `@UniqueConstraint` annotation
- Verify database schema includes the constraint

## License

This project is for demonstration purposes.
