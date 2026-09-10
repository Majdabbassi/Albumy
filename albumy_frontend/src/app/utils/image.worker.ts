const MAX_DIMENSION = 2560;
const JPEG_QUALITY = 0.88;

self.addEventListener('message', (event: MessageEvent) => {
  const file = event.data as File;
  compress(file)
    .then((blob) => (self as unknown as Worker).postMessage(blob))
    .catch(() => (self as unknown as Worker).postMessage(null));
});

async function compress(file: File): Promise<Blob | null> {
  if (!file.type.startsWith('image/') || file.type === 'image/gif' || (file as any).image) {
    return null;
  }

  const bitmap = await createImageBitmap(file);
  try {
    if (bitmap.width <= MAX_DIMENSION && bitmap.height <= MAX_DIMENSION && file.size <= 1024 * 1024) {
      return null;
    }

    const scale = Math.min(1, MAX_DIMENSION / Math.max(bitmap.width, bitmap.height));
    const canvas = new OffscreenCanvas(Math.round(bitmap.width * scale), Math.round(bitmap.height * scale));
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return null;
    }
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return await canvas.convertToBlob({ type: 'image/jpeg', quality: JPEG_QUALITY });
  } finally {
    bitmap.close();
  }
}