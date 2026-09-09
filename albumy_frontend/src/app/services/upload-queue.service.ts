import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { get, set } from 'idb-keyval';
import { EventService } from './event.service';

export type QueueStatus = 'queued' | 'uploading' | 'done' | 'failed';

export interface QueueItem {
  id: string;
  eventCode: string;
  guestToken: string;
  fileName: string;
  mimeType: string;
  size: number;
  file: Blob;
  status: QueueStatus;
  attempts: number;
  uploadId?: string;
  progress: number;
  error?: string;
  result?: any;
  createdAt: number;
}

const STORE_KEY = 'albumy-upload-queue';
const AUTO_UPLOAD_KEY = 'albumy-auto-upload';
const CONCURRENCY = 3;
const CHUNK = 5 * 1024 * 1024;

@Injectable({
  providedIn: 'root'
})
export class UploadQueueService {
  items$ = new BehaviorSubject<QueueItem[]>([]);
  online$ = new BehaviorSubject<boolean>(true);
  autoUpload$ = new BehaviorSubject<boolean>(true);

  activeCount = 0;
  private inFlight = new Set<string>();

  constructor(private eventService: EventService) {}

  async init(): Promise<void> {
    const [stored, autoUpload] = await Promise.all([
      get<QueueItem[]>(STORE_KEY),
      get<boolean>(AUTO_UPLOAD_KEY)
    ]);
    const items = stored || [];
    for (const item of items) {
      if (item.status === 'uploading') {
        item.status = 'queued';
        item.uploadId = undefined;
        item.progress = 0;
      }
    }
    this.autoUpload$.next(autoUpload !== false);
    this.items$.next(items);
    this.online$.next(navigator.onLine);

    window.addEventListener('online', () => {
      this.pauseActive();
      this.online$.next(true);
      this.persist();
      this.kick();
    });
    window.addEventListener('offline', () => {
      this.pauseActive();
      this.online$.next(false);
      this.persist();
    });

    this.kick();
  }

  /** Turns automatic upload on/off. When off, new files are saved on the device
   *  (resuming on reload) and must be sent manually with uploadNow/uploadAll. */
  setAutoUpload(enabled: boolean): void {
    this.autoUpload$.next(enabled);
    if (!enabled) {
      this.pauseActive();
    }
    void set(AUTO_UPLOAD_KEY, enabled);
    this.persist();
    this.kick();
  }

  uploadNow(id: string): void {
    const items = this.items$.getValue();
    const target = items.find((i) => i.id === id);
    if (!target || target.status === 'done' || this.inFlight.has(id) || this.activeCount >= CONCURRENCY) {
      return;
    }
    if (target.status !== 'uploading') {
      target.status = 'queued';
      target.progress = 0;
      target.uploadId = undefined;
      target.error = undefined;
    }
    this.items$.next([...items]);
    this.persist();
    if (this.online$.getValue()) {
      void this.processItem(target);
    }
  }

  uploadAll(): void {
    const items = this.items$.getValue();
    let changed = false;
    for (const i of items) {
      if (i.status !== 'done' && !this.inFlight.has(i.id)) {
        i.status = 'queued';
        i.progress = 0;
        i.uploadId = undefined;
        i.error = undefined;
        changed = true;
      }
    }
    if (changed) {
      this.items$.next([...items]);
      this.persist();
    }
    this.kick(true);
  }

  enqueue(files: { blob: Blob; fileName: string; mimeType: string }[], eventCode: string, guestToken: string): void {
    if (!guestToken || files.length === 0) {
      return;
    }
    const items = this.items$.getValue();
    for (const f of files) {
      if (f.blob.size === 0) {
        continue;
      }
      items.push({
        id: crypto.randomUUID(),
        eventCode,
        guestToken,
        fileName: f.fileName,
        mimeType: f.mimeType,
        size: f.blob.size,
        file: f.blob,
        status: 'queued',
        attempts: 0,
        progress: 0,
        createdAt: Date.now()
      });
    }
    this.items$.next([...items]);
    this.persist();
    this.kick();
  }

  retry(item: QueueItem): void {
    const items = this.items$.getValue();
    const target = items.find((i) => i.id === item.id);
    if (!target) {
      return;
    }
    target.status = 'queued';
    target.attempts = 0;
    target.progress = 0;
    target.error = undefined;
    target.uploadId = undefined;
    this.items$.next([...items]);
    this.persist();
    this.kick();
  }

  remove(id: string): void {
    this.inFlight.delete(id);
    const items = this.items$.getValue().filter((i) => i.id !== id);
    this.items$.next([...items]);
    this.persist();
  }

  clearDone(): void {
    const items = this.items$.getValue().filter((i) => i.status !== 'done');
    this.items$.next([...items]);
    this.persist();
  }

  private kick(force = false): void {
    if (!force && !this.autoUpload$.getValue()) {
      return;
    }
    if (!this.online$.getValue()) {
      return;
    }
    const items = this.items$.getValue();
    for (const item of items) {
      if (this.activeCount >= CONCURRENCY) {
        return;
      }
      if (item.status === 'queued' && !this.inFlight.has(item.id)) {
        void this.processItem(item);
      }
    }
  }

  private async processItem(item: QueueItem): Promise<void> {
    this.inFlight.add(item.id);
    this.activeCount++;
    this.update(item, { status: 'uploading', progress: 0, error: undefined });

    const totalChunks = Math.max(1, Math.ceil(item.size / CHUNK));

    try {
      const res = await this.withRetry(item, () =>
        this.eventService
          .initChunkedUpload(item.eventCode, item.guestToken, item.fileName, item.mimeType, item.size, totalChunks)
          .toPromise()
      );
      const uploadId = res.uploadId as string;
      this.update(item, { uploadId });

      const received = new Set<number>((await this.eventService.getReceivedChunks(uploadId).toPromise()) || []);

      for (let index = 0; index < totalChunks; index++) {
        if (!this.online$.getValue()) {
          this.detach(item);
          return;
        }
        if (received.has(index)) {
          continue;
        }
        const start = index * CHUNK;
        const data = item.file.slice(start, Math.min(start + CHUNK, item.size));
        await this.withRetry(item, () => this.eventService.uploadChunk(uploadId, index, data).toPromise());
        received.add(index);
        this.update(item, { progress: Math.round(((index + 1) / totalChunks) * 100) });
      }

      const photo = await this.withRetry(item, () => this.eventService.completeChunkedUpload(uploadId).toPromise());
      this.update(item, { status: 'done', progress: 100, result: photo });
      setTimeout(() => this.remove(item.id), 5000);
    } catch (err) {
      this.update(item, { status: 'failed', error: this.readError(err), uploadId: undefined });
    } finally {
      this.inFlight.delete(item.id);
      this.activeCount--;
      this.kick();
    }
  }

  /** Retries up to 3 times with exponential backoff. Throws when attempts are exhausted. */
  private async withRetry<T>(item: QueueItem, fn: () => Promise<T>): Promise<T> {
    for (let attempt = 1; ; attempt++) {
      try {
        return await fn();
      } catch (err) {
        if (attempt >= 3) {
          throw err;
        }
        this.update(item, { attempts: attempt });
        await this.delay(600 * attempt);
      }
    }
  }

  private detach(item: QueueItem): void {
    this.update(item, { status: 'queued', uploadId: undefined, progress: 0 });
    this.inFlight.delete(item.id);
    if (this.activeCount > 0) {
      this.activeCount--;
    }
  }

  private pauseActive(): void {
    const items = this.items$.getValue();
    let changed = false;
    for (const i of items) {
      if (i.status === 'uploading') {
        i.status = 'queued';
        i.uploadId = undefined;
        i.progress = 0;
        changed = true;
      }
    }
    if (changed) {
      this.items$.next([...items]);
    }
  }

  private update(item: QueueItem, patch: Partial<QueueItem>): void {
    const items = this.items$.getValue();
    const target = items.find((i) => i.id === item.id);
    if (!target) {
      return;
    }
    Object.assign(target, patch);
    this.items$.next([...items]);
    this.persist();
  }

  private persist(): void {
    void set(STORE_KEY, this.items$.getValue());
  }

  private readError(err: any): string {
    return err?.error?.message || err?.message || 'Upload failed';
  }

  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}