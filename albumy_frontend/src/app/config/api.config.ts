import { environment } from '../../environments/environment';

const STORAGE_KEY = 'albumy_api_url';

/**
 * Resolves the backend base URL at runtime.
 *
 * Priority:
 *  1. A `localStorage` override set by tools/tests (key: `albumy_api_url`).
 *  2. The value injected at build time via `environment.apiUrl`.
 *  3. Same-origin `/api` prefix (used by the Angular dev-server proxy and the
 *     production Nginx container, so the app works from any device/host).
 */
export function resolveApiUrl(): string {
  const override = typeof localStorage !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null;
  if (override) {
    return override.replace(/\/+$/, '');
  }
  if (environment.apiUrl) {
    return environment.apiUrl.replace(/\/+$/, '');
  }
  return `${window.location.origin}/api`;
}

/** Prefixes a backend-relative path (e.g. `/files/uuid.png`) with the API base.
 *  Non-http(s) absolute URLs (javascript:, data:, ...) are rejected outright. */
export function fileUrl(path: string): string {
  if (!path) {
    return '';
  }
  const value = path.trim();
  if (/^https?:\/\//i.test(value)) {
    return value;
  }
  if (/^[a-z][a-z0-9+.-]*:/i.test(value)) {
    return '';
  }
  return `${resolveApiUrl()}${value}`;
}

/**
 * Resolves the WebSocket endpoint for STOMP live updates.
 * HTTP(S) API base -> ws(s):// same host, `/ws` path.
 */
export function resolveWsUrl(): string {
  const api = resolveApiUrl();
  if (api !== `${window.location.origin}/api`) {
    return api.replace(/^http/, 'ws').replace(/\/api$/, '') + '/ws';
  }
  return `${window.location.origin.replace(/^http/, 'ws')}/ws`;
}