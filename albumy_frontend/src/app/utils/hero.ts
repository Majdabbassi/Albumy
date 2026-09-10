import { fileUrl } from '../config/api.config';

function escapeCssUrl(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "\\'")
    .replace(/[\u0000-\u001f]/g, '');
}

export function heroBackground(coverUrl: string | null | undefined): string {
  if (!coverUrl) {
    return '';
  }
  const resolved = escapeCssUrl(fileUrl(coverUrl));
  if (!resolved) {
    return '';
  }
  return `linear-gradient(180deg, rgba(17,24,39,.82) 0%, rgba(17,24,39,.5) 45%, rgba(17,24,39,.9) 100%), url('${resolved}') center / cover no-repeat`;
}