import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { EventService } from './event.service';

let idbPromise: Promise<typeof import('idb-keyval')> | null = null;

function idb(): Promise<typeof import('idb-keyval')> {
  if (!idbPromise) {
    idbPromise = import('idb-keyval');
  }
  return idbPromise;
}

function get<T>(key: string): Promise<T | undefined> {
  return idb().then((mod) => mod.get<unknown>(key).then((value) => value as T | undefined));
}

function set(key: string, value: unknown): Promise<void> {
  return idb().then((mod) => mod.set(key, value));
}

function del(key: string): Promise<void> {
  return idb().then((mod) => mod.del(key));
}

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
// Blob content is stored once, keyed by item id, so progress updates only rewrite the
// small metadata list (storing the whole multi-GB Blob on every chunk would thrash IndexedDB).
const fileKey = (id: string) => `albumy-upload-file:${id}`;

@Injectable({
  providedIn: 'root'
})
export class UploadQueueService {
  items$ = new BehaviorSubject<QueueItem[]>([]);
  online$ = new BehaviorSubject<boolean>(true);
  autoUpload$ = new BehaviorSubject<boolean>(true);

  activeCount = 0;
  private inFlight = new Set<string>();
  private cancelled = new Set<string>();
  private paused = false;

  constructor(private eventService: EventService) {}

  async init(): Promise<void> {
    const [stored, autoUpload] = await Promise.all([
      get<QueueItem[]>(STORE_KEY),
      get<boolean>(AUTO_UPLOAD_KEY)
    ]);
    const items: QueueItem[] = [];
    for (const raw of stored || []) {
      const item = raw as QueueItem;
      if (item.status === 'done') {
        void del(fileKey(item.id));
        continue;
      }
      if (item.status === 'uploading') {
        item.status = 'queued';
      }
      if (item.file instanceof Blob) {
        // Legacy rows stored the blob inline; migrate it to the keyed blob store.
        void set(fileKey(item.id), item.file);
      } else {
        const blob = await this.loadFile(item.id);
        if (!blob) {
          console.warn('Dropping queued upload whose local copy is gone', item.id);
          void del(fileKey(item.id));
          continue;
        }
        item.file = blob;
      }
      items.push(item);
    }
    if ((stored || []).length !== items.length || stored?.some((s) => s.file instanceof Blob)) {
      this.flushPersist();
    }
    this.autoUpload$.next(autoUpload !== false);
    this.items$.next(items);
    this.online$.next(navigator.onLine);

    window.addEventListener('online', () => {
      this.pauseActive();
      this.online$.next(true);
      this.flushPersist();
      this.kick();
    });
    window.addEventListener('offline', () => {
      this.pauseActive();
      this.online$.next(false);
      this.flushPersist();
    });

    this.kick();
  }

  /** Turns automatic upload on/off. When off, new files are saved on the device
   *  (resuming on reload) and must be sent manually with uploadNow/uploadAll. */
  setAutoUpload(enabled: boolean): void {
    this.autoUpload$.next(enabled);
    this.paused = !enabled;
    if (!enabled) {
      this.pauseActive();
    }
    void set(AUTO_UPLOAD_KEY, enabled);
    this.flushPersist();
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
      target.error = undefined;
    }
    this.items$.next([...items]);
    this.flushPersist();
    if (this.online$.getValue()) {
      void this.processItem(target, true);
    }
  }

  uploadAll(): void {
    const items = this.items$.getValue();
    let changed = false;
    for (const i of items) {
      if (i.status !== 'done' && !this.inFlight.has(i.id)) {
        i.status = 'queued';
        i.error = undefined;
        changed = true;
      }
    }
    if (changed) {
      this.items$.next([...items]);
      this.flushPersist();
    }
    this.kick(true);
  }

  enqueue(files: { blob: Blob; fileName: string; mimeType: string }[], eventCode: string, guestToken: string): void {
    if (!guestToken || files.length === 0) {
      return;
    }
    const items = this.items$.getValue();
    const added: QueueItem[] = [];
    for (const f of files) {
      if (f.blob.size === 0) {
        continue;
      }
      added.push({
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
    items.push(...added);
    this.items$.next([...items]);
    for (const item of added) {
      void set(fileKey(item.id), item.file);
    }
    this.flushPersist();
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
    this.flushPersist();
    this.kick(true);
  }

  remove(id: string): void {
    this.inFlight.delete(id);
    this.cancelled.add(id);
    void del(fileKey(id));
    const items = this.items$.getValue().filter((i) => i.id !== id);
    this.items$.next([...items]);
    this.flushPersist();
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
        void this.processItem(item, force);
      }
    }
  }

  private async processItem(item: QueueItem, force = false): Promise<void> {
    this.inFlight.add(item.id);
    this.activeCount++;
    this.update(item, { status: 'uploading', error: undefined });

    const totalChunks = Math.max(1, Math.ceil(item.size / CHUNK));

    try {
      let uploadId = item.uploadId as string | undefined;
      if (!uploadId) {
        const res = await this.withRetry(item, () =>
          this.eventService
            .initChunkedUpload(item.eventCode, item.guestToken, item.fileName, item.mimeType, item.size, totalChunks)
            .toPromise()
        );
        uploadId = res.uploadId as string;
        this.update(item, { uploadId });
      }

      let received = new Set<number>();
      try {
        received = new Set<number>((await this.eventService.getReceivedChunks(uploadId).toPromise()) || []);
      } catch {
        const res = await this.withRetry(item, () =>
          this.eventService
            .initChunkedUpload(item.eventCode, item.guestToken, item.fileName, item.mimeType, item.size, totalChunks)
            .toPromise()
        );
        uploadId = res.uploadId as string;
        this.update(item, { uploadId });
      }

      for (let index = 0; index < totalChunks; index++) {
        if (this.cancelled.has(item.id)) {
          return;
        }
        if ((!force && this.paused) || !this.online$.getValue()) {
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

      if (this.cancelled.has(item.id)) {
        return;
      }
      const photo = await this.withRetry(item, () => this.eventService.completeChunkedUpload(uploadId).toPromise());
      this.update(item, { status: 'done', progress: 100, result: photo });
      setTimeout(() => this.remove(item.id), 5000);
    } catch (err) {
      if (this.isNetworkError(err)) {
        this.detach(item);
      } else {
        this.update(item, { status: 'failed', error: this.readError(err) });
      }
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
    this.update(item, { status: 'queued' });
    this.inFlight.delete(item.id);
  }

  private pauseActive(): void {
    const items = this.items$.getValue();
    let changed = false;
    for (const i of items) {
      if (i.status === 'uploading') {
        i.status = 'queued';
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

  private persistTimer: ReturnType<typeof setTimeout> | null = null;

  private persist(): void {
    if (this.persistTimer) {
      return;
    }
    this.persistTimer = setTimeout(() => {
      this.persistTimer = null;
      this.flushPersist();
    }, 500);
  }

  private flushPersist(): void {
    if (this.persistTimer) {
      clearTimeout(this.persistTimer);
      this.persistTimer = null;
    }
    // Metadata only — Blob content lives under fileKey(id), written once at enqueue.
    void set(STORE_KEY, this.items$.getValue().map((item) => this.stripBlob(item)));
  }

  private stripBlob(item: QueueItem): Partial<QueueItem> {
    const meta: any = { ...item };
    delete meta.file;
    return meta;
  }

  private async loadFile(id: string): Promise<Blob | undefined> {
    try {
      return await get<Blob>(fileKey(id));
    } catch {
      return undefined;
    }
  }

  private readError(err: any): string {
    return err?.error?.message || err?.message || 'Upload failed';
  }

  private isNetworkError(err: any): boolean {
    if (!navigator.onLine) {
      return true;
    }
    const name = err?.name || '';
    const msg = (err?.message || '').toString().toLowerCase();
    return name === 'TypeError'
      || msg.includes('network')
      || msg.includes('fetch')
      || msg.includes('load failed');
  }

  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}