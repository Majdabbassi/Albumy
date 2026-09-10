const MAX_DIMENSION = 2560;
const JPEG_QUALITY = 0.88;

/** Downsizes an image only when needed, high-quality JPEG (max 2560px, quality 0.88).
 *  Returns null when compression isn't applicable or wouldn't reduce size.
 *  Runs off the main thread when OffscreenCanvas is available. */
export function compressImageFile(file: File): Promise<Blob | null> {
  if (
    typeof OffscreenCanvas !== 'undefined' &&
    typeof Worker !== 'undefined' &&
    typeof createImageBitmap === 'function'
  ) {
    return compressWithWorker(file);
  }
  return compressOnMainThread(file);
}

function compressWithWorker(file: File): Promise<Blob | null> {
  return new Promise((resolve) => {
    const worker = new Worker(new URL('./image.worker.ts', import.meta.url), { type: 'module' });
    worker.onmessage = (event: MessageEvent<Blob | null>) => {
      worker.terminate();
      resolve(event.data);
    };
    worker.onerror = () => {
      worker.terminate();
      resolve(null);
    };
    worker.postMessage(file);
  });
}

function compressOnMainThread(file: File): Promise<Blob | null> {
  return new Promise((resolve) => {
    if (!file.type.startsWith('image/') || file.type === 'image/gif' || (file as any).image) {
      resolve(null);
      return;
    }

    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      const { width, height } = img;
      if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
        if (file.size <= 1024 * 1024) {
          resolve(null);
          return;
        }
      }

      const scale = Math.min(1, MAX_DIMENSION / Math.max(width, height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(width * scale);
      canvas.height = Math.round(height * scale);
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        resolve(null);
        return;
      }
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      canvas.toBlob(
        (blob) => resolve(blob),
        'image/jpeg',
        JPEG_QUALITY
      );
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      resolve(null);
    };
    img.src = url;
  });
}