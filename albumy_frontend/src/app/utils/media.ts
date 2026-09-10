import { fileUrl } from '../config/api.config';

const VIDEO_EXT = /\.(mp4|mov|webm|m4v|3gp)$/i;

export function isVideo(photo: any): boolean {
  if (!photo) {
    return false;
  }
  return (
    photo.video === true ||
    photo.isVideo === true ||
    VIDEO_EXT.test(photo.fileName || photo.fileUrl || '')
  );
}

export function photoSrc(photo: any, variant?: 'thumb' | 'med' | 'full'): string {
  if (!photo) {
    return '';
  }
  if (isVideo(photo)) {
    return fileUrl(
      variant === 'thumb'
        ? photo.posterUrl || photo.webUrl || photo.fileUrl
        : photo.webUrl || photo.fileUrl
    );
  }
  const url =
    variant === 'thumb' ? photo.thumbUrl :
    variant === 'med' ? photo.medUrl :
    variant === 'full' ? photo.fullUrl :
    photo.fileUrl;
  return fileUrl(url || photo.fileUrl);
}

/** Human-friendly name for downloaded files (original name when available, not the stored UUID). */
export function downloadName(photo: any): string {
  return photo?.originalName || photo?.fileName || 'photo';
}