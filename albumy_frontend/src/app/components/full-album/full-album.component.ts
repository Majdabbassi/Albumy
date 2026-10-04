import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { EventService } from '../../services/event.service';
import { RealtimeService } from '../../services/realtime.service';
import { IconComponent } from '../../shared/icon/icon.component';
import { LightboxComponent } from '../../shared/lightbox/lightbox.component';
import { fileUrl } from '../../config/api.config';
import { downloadName } from '../../utils/media';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';
import { FormatDatePipe } from '../../shared/pipes/format-date.pipe';
import { FormatTimePipe } from '../../shared/pipes/format-time.pipe';
import { MediaUrlPipe } from '../../shared/pipes/media-url.pipe';
import { IsVideoPipe } from '../../shared/pipes/is-video.pipe';
import { HeroBackgroundPipe } from '../../shared/pipes/hero-background.pipe';

@Component({
  selector: 'app-full-album',
  standalone: true,
  imports: [CommonModule, IconComponent, LightboxComponent, TimeAgoPipe, FormatDatePipe, FormatTimePipe, MediaUrlPipe, IsVideoPipe, HeroBackgroundPipe],
  templateUrl: './full-album.component.html',
  styleUrls: ['./full-album.component.css']
})
export class FullAlbumComponent implements OnInit, OnDestroy {
  eventCode = '';
  fullAlbumToken = '';
  event: any = null;
  photos: any[] = [];
  errorMessage = '';
  loading = true;
  lightboxIndex = -1;
  live = false;
  photosLoading = false;
  hasMore = false;
  loadingMore = false;
  selectMode = false;
  selected = new Set<number>();
  batchTotal = 0;
  batchDone = 0;
  batchComplete = false;
  batchVisible = false;
  private realtimeSub?: Subscription;
  private connectionSubs: Subscription[] = [];
  private timers: any[] = [];

  constructor(
    private route: ActivatedRoute,
    private eventService: EventService,
    private realtime: RealtimeService
  ) {}

  ngOnInit(): void {
    this.eventCode = this.route.snapshot.paramMap.get('code') || '';
    this.fullAlbumToken = this.route.snapshot.paramMap.get('token') || '';

    if (this.eventCode && this.fullAlbumToken) {
      this.loadFullAlbum();
    } else {
      this.errorMessage = 'Invalid album link';
      this.loading = false;
    }
  }

  ngOnDestroy(): void {
    this.cancelBatchTimers();
    this.realtimeSub?.unsubscribe();
    this.connectionSubs.forEach((s) => s.unsubscribe());
    if (this.event?.id) {
      this.realtime.offEvent(this.event.id);
    }
  }

  private cancelBatchTimers(): void {
    for (const t of this.timers) {
      clearTimeout(t);
    }
    this.timers = [];
  }

  loadFullAlbum(): void {
    this.photosLoading = true;
    this.eventService.getFullAlbum(this.fullAlbumToken).subscribe({
      next: (data) => {
        this.event = data;
        this.photos = data.photos || [];
        this.hasMore = (data.photoCount || 0) > this.photos.length;
        this.loading = false;
        this.photosLoading = false;
        this.watchRealtime();
      },
      error: () => {
        this.errorMessage = 'Album not found or link has expired';
        this.loading = false;
        this.photosLoading = false;
      }
    });
  }

  loadMore(): void {
    if (!this.hasMore || this.loadingMore || this.photos.length === 0) {
      return;
    }
    this.loadingMore = true;
    const beforeId = this.photos[this.photos.length - 1].id;
    this.eventService.getFullAlbum(this.fullAlbumToken, beforeId, 60).subscribe({
      next: (data) => {
        const existing = new Set(this.photos.map((p) => p.id));
        const fresh = (data.photos || []).filter((p: any) => !existing.has(p.id));
        this.photos = [...this.photos, ...fresh];
        this.hasMore = (data.photoCount || 0) > this.photos.length;
        this.loadingMore = false;
      },
      error: () => {
        this.loadingMore = false;
      }
    });
  }

  private watchRealtime(): void {
    if (!this.event?.id || this.realtimeSub) {
      return; // nothing to watch, or already wired (loadFullAlbum reruns after a reconnect)
    }
    this.realtime.connect();
    this.connectionSubs = [
      this.realtime.connected$.subscribe((connected) => (this.live = connected)),
      this.realtime.reconnected$.subscribe(() => this.loadFullAlbum())
    ];
    this.realtimeSub = this.realtime.onEvent(this.event.id, {
      'X-Realtime-Token': this.event.realtimeToken
    }).subscribe((msg) => {
      if (msg.type === 'PHOTO_ADDED' || msg.type === 'PHOTO_READY') {
        if (!msg.data?.id) {
          return;
        }
        if (!this.photos.some((p) => p.id === msg.data.id)) {
          this.photos = [msg.data, ...this.photos];
        } else {
          // Replace (don't mutate) so pure pipes recompute, keeping known values over nulls.
          const fresh = Object.fromEntries(Object.entries(msg.data).filter(([, v]) => v != null));
          this.photos = this.photos.map((p) => (p.id === msg.data.id ? { ...p, ...fresh } : p));
        }
      } else if (msg.type === 'PHOTO_REMOVED') {
        const removedId = msg.data?.id;
        this.photos = this.photos.filter((p) => p.id !== removedId);
        if (removedId != null) {
          this.selected.delete(removedId);
        }
      }
    });
  }

  downloadPhoto(photo: any): void {
    this.triggerDownload(photo);
  }

  get selectedCount(): number {
    return this.selected.size;
  }

  get allSelected(): boolean {
    return this.photos.length > 0 && this.selected.size === this.photos.length;
  }

  get batchProgress(): number {
    return this.batchTotal === 0 ? 0 : Math.round((this.batchDone / this.batchTotal) * 100);
  }

  enterSelectMode(): void {
    this.selectMode = true;
    this.selected.clear();
  }

  exitSelectMode(): void {
    this.selectMode = false;
    this.selected.clear();
  }

  onMediaClick(photo: any, index: number): void {
    if (this.selectMode) {
      this.toggleSelected(photo);
      return;
    }
    this.openLightbox(index);
  }

  toggleSelected(photo: any): void {
    if (this.selected.has(photo.id)) {
      this.selected.delete(photo.id);
    } else {
      this.selected.add(photo.id);
    }
  }

  isSelected(photo: any): boolean {
    return this.selected.has(photo.id);
  }

  selectAll(): void {
    for (const photo of this.photos) {
      this.selected.add(photo.id);
    }
  }

  clearSelection(): void {
    this.selected.clear();
  }

  downloadAll(): void {
    this.downloadBatch(this.photos);
  }

  downloadSelected(): void {
    if (this.selected.size === 0) {
      return;
    }
    this.downloadBatch(this.photos.filter((p) => this.selected.has(p.id)));
  }

  private async downloadBatch(photos: any[]): Promise<void> {
    if (photos.length === 0 || this.batchVisible) {
      return;
    }
    this.cancelBatchTimers();
    this.batchTotal = photos.length;
    this.batchDone = 0;
    this.batchComplete = false;
    this.batchVisible = true;

    const run = async (index: number): Promise<void> => {
      if (index >= photos.length) {
        this.batchComplete = true;
        this.batchDone = photos.length;
        this.timers.push(setTimeout(() => {
          this.batchVisible = false;
          this.batchTotal = 0;
          this.batchDone = 0;
          this.batchComplete = false;
        }, 2200));
        return;
      }
      await this.triggerDownload(photos[index]);
      this.batchDone = index + 1;
      await this.delay(350);
      await run(index + 1);
    };
    void run(0);
  }

  private async triggerDownload(photo: any, useBlob = true): Promise<void> {
    const url = photo.fileUrl || photo.fullUrl || photo.webUrl || photo.fileName;
    if (!url) {
      return;
    }
    const href = fileUrl(url);
    if (useBlob) {
      try {
        const res = await fetch(href, { credentials: 'include' });
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        const blob = await res.blob();
        const objectUrl = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = objectUrl;
        link.download = downloadName(photo);
        link.rel = 'noopener';
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
        return;
      } catch {
        // fall back to a direct navigation download below
      }
    }
    const link = document.createElement('a');
    link.href = href;
    link.download = downloadName(photo);
    link.rel = 'noopener';
    link.click();
  }

  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  openLightbox(index: number): void {
    this.lightboxIndex = index;
  }

  closeLightbox(): void {
    this.lightboxIndex = -1;
  }
}