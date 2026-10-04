# Albumy frontend

Angular 20 app (standalone components) for Albumy: guest upload page, organizer dashboard, read-only album and the Capacitor Android wrapper.

See the [root README](../README.md) for the architecture, how to run the full stack with Docker, and the API.

Quick start for frontend-only development (needs the backend on `:8080`):

```bash
npm install
npm start        # http://localhost:4200, proxies /api and /ws to :8080
npm run build    # production build into dist/
```
