import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { EventService } from '../../services/event.service';
import { RealtimeService } from '../../services/realtime.service';
import { IconComponent } from '../../shared/icon/icon.component';
import { LightboxComponent } from '../../shared/lightbox/lightbox.component';
import { fileUrl } from '../../config/api.config';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';

@Component({
  selector: 'app-full-album',
  standalone: true,
  imports: [CommonModule, IconComponent, LightboxComponent, TimeAgoPipe],
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
  private resetTimer?: any;

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
    if (this.resetTimer) {
      clearTimeout(this.resetTimer);
    }
    this.realtimeSub?.unsubscribe();
  }

  photoSrc(photo: any, variant?: 'thumb' | 'med' | 'full'): string {
    if (photo.video || /\.(mp4|mov|webm|m4v|3gp)$/i.test(photo.fileName || photo.fileUrl || '')) {
      return fileUrl(variant === 'thumb' ? photo.posterUrl || photo.webUrl || photo.fileUrl : photo.webUrl || photo.fileUrl);
    }
    const url =
      variant === 'thumb' ? photo.thumbUrl :
      variant === 'med' ? photo.medUrl :
      variant === 'full' ? photo.fullUrl :
      photo.fileUrl;
    return fileUrl(url || photo.fileUrl);
  }

  isVideo(photo: any): boolean {
    return photo.video === true || /\.(mp4|mov|webm|m4v|3gp)$/i.test(photo.fileName || photo.fileUrl || '');
  }

  heroBackground(): string {
    if (!this.event?.coverUrl) {
      return '';
    }
    return `linear-gradient(180deg, rgba(17,24,39,.82) 0%, rgba(17,24,39,.5) 45%, rgba(17,24,39,.9) 100%), url('${fileUrl(this.event.coverUrl)}') center / cover no-repeat`;
  }

  loadFullAlbum(): void {
    this.photosLoading = true;
    this.eventService.getFullAlbum(this.fullAlbumToken).subscribe({
      next: (data) => {
        this.event = data;
        this.photos = data.photos || [];
        this.hasMore = (data.photos?.length || 0) === 60;
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
        this.hasMore = (data.photos?.length || 0) === 60;
        this.loadingMore = false;
      },
      error: () => {
        this.loadingMore = false;
      }
    });
  }

  private watchRealtime(): void {
    if (!this.event?.id) {
      return;
    }
    this.realtime.connect();
    this.realtimeSub = this.realtime.onEvent(this.event.id).subscribe((msg) => {
      this.live = true;
      if (msg.type === 'PHOTO_ADDED' || msg.type === 'PHOTO_READY') {
        if (msg.data?.id && !this.photos.some((p) => p.id === msg.data.id)) {
          this.photos = [msg.data, ...this.photos];
        }
      } else if (msg.type === 'PHOTO_REMOVED') {
        this.photos = this.photos.filter((p) => p.id !== msg.data?.id);
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

  private downloadBatch(photos: any[]): void {
    if (photos.length === 0 || this.batchVisible) {
      return;
    }
    if (this.resetTimer) {
      clearTimeout(this.resetTimer);
      this.resetTimer = undefined;
    }
    this.batchTotal = photos.length;
    this.batchDone = 0;
    this.batchComplete = false;
    this.batchVisible = true;

    const run = (index: number): void => {
      if (index >= photos.length) {
        this.batchComplete = true;
        this.batchDone = photos.length;
        this.resetTimer = setTimeout(() => {
          this.batchVisible = false;
          this.batchTotal = 0;
          this.batchDone = 0;
          this.batchComplete = false;
        }, 2200);
        return;
      }
      this.triggerDownload(photos[index]);
      this.batchDone = index + 1;
      setTimeout(() => run(index + 1), 350);
    };
    run(0);
  }

  private triggerDownload(photo: any): void {
    const url = photo.fileUrl || photo.fullUrl || photo.webUrl || photo.fileName;
    if (!url) {
      return;
    }
    const link = document.createElement('a');
    link.href = fileUrl(url);
    link.download = photo.originalName || photo.fileName;
    link.rel = 'noopener';
    link.click();
  }

  openLightbox(index: number): void {
    this.lightboxIndex = index;
  }

  closeLightbox(): void {
    this.lightboxIndex = -1;
  }

  stepLightbox(direction: number): void {
    const next = this.lightboxIndex + direction;
    if (next >= 0 && next < this.photos.length) {
      this.lightboxIndex = next;
    }
  }

  formatDate(date: string): string {
    return new Date(date + 'T00:00:00').toLocaleDateString(undefined, {
      weekday: 'short',
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  formatTime(time: string): string {
    const [h, m] = time.split(':').map(Number);
    return new Date(2000, 0, 1, h, m).toLocaleTimeString(undefined, {
      hour: 'numeric',
      minute: '2-digit'
    });
  }
}