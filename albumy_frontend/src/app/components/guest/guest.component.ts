import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { EventService } from '../../services/event.service';
import { AuthService } from '../../services/auth.service';
import { RealtimeService } from '../../services/realtime.service';
import { UploadQueueService, QueueItem } from '../../services/upload-queue.service';
import { IconComponent } from '../../shared/icon/icon.component';
import { LightboxComponent } from '../../shared/lightbox/lightbox.component';
import { ConfirmModalComponent } from '../../shared/confirm-modal/confirm-modal.component';
import { photoSrc as photoSrcUtil, downloadName } from '../../utils/media';
import { compressImageFile } from '../../utils/image';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';
import { FormatDatePipe } from '../../shared/pipes/format-date.pipe';
import { FormatTimePipe } from '../../shared/pipes/format-time.pipe';
import { InitialsPipe } from '../../shared/pipes/initials.pipe';
import { MediaUrlPipe } from '../../shared/pipes/media-url.pipe';
import { IsVideoPipe } from '../../shared/pipes/is-video.pipe';
import { HeroBackgroundPipe } from '../../shared/pipes/hero-background.pipe';

@Component({
  selector: 'app-guest',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent, LightboxComponent, ConfirmModalComponent, TimeAgoPipe, FormatDatePipe, FormatTimePipe, InitialsPipe, MediaUrlPipe, IsVideoPipe, HeroBackgroundPipe],
  templateUrl: './guest.component.html',
  styleUrls: ['./guest.component.css']
})
export class GuestComponent implements OnInit, OnDestroy {
  eventCode = '';
  eventInfo: any = null;
  uploaderName = '';
  guestToken = '';
  nameAvailable: boolean | null = null;
  nameChecking = false;
  showUploadSection = false;
  photos: any[] = [];
  errorMessage = '';
  successMessage = '';
  loading = true;
  isLoggedIn = false;
  currentUser = '';
  lightboxIndex = -1;
  queueItems: QueueItem[] = [];
  queueOnline = true;
  autoUpload = true;
  processingSelection = false;
  photosLoading = false;
  guestHasMore = false;
  guestLoadingMore = false;
  queueExpanded = true;
  previewItem: QueueItem | null = null;
  pendingDelete: any = null;
  deleteSubmitting = false;

  private objectUrls = new Map<string, string>();
  private deletingIds = new Set<number>();
  private fetching = false;
  private refetchQueued = false;
  private pollTimer: any = null;
  private firstLoadDone = false;
  private lastRealtimeEvent = 0;

  private queueSub?: Subscription;
  private onlineSub?: Subscription;
  private autoUploadSub?: Subscription;
  private realtimeSub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private eventService: EventService,
    private authService: AuthService,
    private realtime: RealtimeService,
    private queue: UploadQueueService
  ) {}

  ngOnInit(): void {
    this.eventCode = this.route.snapshot.paramMap.get('code') || '';
    this.isLoggedIn = this.authService.isLoggedIn();

    if (this.isLoggedIn) {
      this.currentUser = this.authService.getUsername() || '';
    }

    this.queueSub = this.queue.items$.subscribe((items) => {
      this.queueItems = items.filter((i) => i.eventCode === this.eventCode);
      this.pruneObjectUrls(new Set(this.queueItems.map((i) => i.id)));
      const missing = this.queueItems
        .filter((i) => i.status === 'done' && i.result?.id && !this.photos.some((p) => p.id === i.result.id))
        .map((i) => i.result);
      if (missing.length > 0) {
        this.photos = this.mergePhotos(this.photos);
      }
      this.ensurePolling();
    });
    this.onlineSub = this.queue.online$.subscribe((online) => {
      this.queueOnline = online;
    });
    this.autoUploadSub = this.queue.autoUpload$.subscribe((on) => {
      this.autoUpload = on;
    });
    void this.queue.init();

    if (this.eventCode) {
      this.loadEventInfo();
      this.checkStoredIdentity();
    }
  }

  ngOnDestroy(): void {
    this.queueSub?.unsubscribe();
    this.onlineSub?.unsubscribe();
    this.autoUploadSub?.unsubscribe();
    this.realtimeSub?.unsubscribe();
    if (this.eventInfo?.id) {
      this.realtime.offEvent(this.eventInfo.id);
    }
    this.stopPolling();
    for (const url of this.objectUrls.values()) {
      URL.revokeObjectURL(url);
    }
    this.objectUrls.clear();
  }

  private loadEventInfo(): void {
    this.eventService.getEventPublicInfo(this.eventCode).subscribe({
      next: (data) => {
        this.eventInfo = data;
        this.loading = false;
        this.watchRealtime();
      },
      error: () => {
        this.errorMessage = 'Event not found — the link may be incorrect or expired.';
        this.loading = false;
      }
    });
  }

  private watchRealtime(): void {
    if (!this.eventInfo?.id) {
      return;
    }
    this.realtime.connect();
    this.realtimeSub = this.realtime.onEvent(this.eventInfo.id, {
      'X-Realtime-Token': this.eventInfo.realtimeToken
    }).subscribe((msg) => {
      this.lastRealtimeEvent = Date.now();
      if (msg.type === 'PHOTO_REMOVED') {
        const removedId = msg.data?.id;
        if (removedId != null) {
          this.photos = this.photos.filter((p) => p.id !== removedId);
        }
        return;
      }
      if (msg.type === 'PHOTO_READY' || msg.type === 'PHOTO_ADDED' || msg.type === 'ACTIVITY') {
        this.refreshMyPhotos();
      }
    });
  }

  private checkStoredIdentity(): void {
    const token = localStorage.getItem(`guest_token_${this.eventCode}`);
    const name = localStorage.getItem(`guest_name_${this.eventCode}`);

    if (token) {
      this.guestToken = token;
      if (name) {
        this.uploaderName = name;
      } else {
        this.uploaderName = this.currentUser || '';
      }
      this.nameAvailable = true;
      this.startUploadSection();
      return;
    }

    if (this.isLoggedIn && this.currentUser) {
      this.uploaderName = this.currentUser;
      this.claimAndEnter(this.currentUser);
      return;
    }

    if (name) {
      this.uploaderName = name;
      this.claimAndEnter(name);
    }
  }

  checkNameAvailability(): void {
    if (!this.uploaderName || this.uploaderName.trim().length < 2) {
      this.nameAvailable = null;
      return;
    }
    this.nameChecking = true;
    this.eventService.isNameAvailable(this.eventCode, this.uploaderName.trim()).subscribe({
      next: (available) => {
        this.nameAvailable = available;
        this.nameChecking = false;
      },
      error: () => {
        this.nameAvailable = false;
        this.nameChecking = false;
      }
    });
  }

  submitName(): void {
    if (!this.nameAvailable) {
      this.errorMessage = 'Please choose a different name.';
      return;
    }
    this.errorMessage = '';
    this.claimAndEnter(this.uploaderName.trim());
  }

  private claimAndEnter(name: string): void {
    this.eventService.claimGuest(this.eventCode, name).subscribe({
      next: (res) => {
        this.uploaderName = name;
        this.guestToken = res.guestToken;
        localStorage.setItem(`guest_token_${this.eventCode}`, res.guestToken);
        localStorage.setItem(`guest_name_${this.eventCode}`, name);
        this.showUploadSection = true;
        this.startUploadSection();
      },
      error: () => {
        this.errorMessage = 'Could not join this event right now.';
      }
    });
  }

  changeName(): void {
    if (this.isLoggedIn) {
      return;
    }
    localStorage.removeItem(`guest_token_${this.eventCode}`);
    localStorage.removeItem(`guest_name_${this.eventCode}`);
    this.uploaderName = '';
    this.guestToken = '';
    this.showUploadSection = false;
    this.photos = [];
    this.nameAvailable = null;
    this.firstLoadDone = false;
    this.stopPolling();
    this.queueItems.forEach((i) => this.queue.remove(i.id));
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      void this.handleFiles(Array.from(input.files));
    }
    input.value = '';
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer?.files) {
      void this.handleFiles(Array.from(event.dataTransfer.files));
    }
  }

  private async handleFiles(files: File[]): Promise<void> {
    if (!this.guestToken || files.length === 0) {
      return;
    }
    this.processingSelection = true;
    const prepared: { blob: Blob; fileName: string; mimeType: string }[] = [];
    try {
      for (const file of files) {
        if (file.type.startsWith('image/') && file.type !== 'image/gif') {
          const compressed = await compressImageFile(file);
          if (compressed && compressed.size < file.size) {
            const base = file.name.replace(/\.[^.]+$/, '');
            prepared.push({ blob: compressed, fileName: `${base}.jpg`, mimeType: 'image/jpeg' });
            continue;
          }
        }
        prepared.push({ blob: file, fileName: file.name, mimeType: file.type || 'application/octet-stream' });
      }
    } finally {
      this.processingSelection = false;
    }
    this.queue.enqueue(prepared, this.eventCode, this.guestToken);
    this.successMessage = `${prepared.length} ${prepared.length === 1 ? 'file' : 'files'} added${
      this.autoUpload ? ' — uploading now.' : ' — saved on this device, they only upload when you send them.'
    }`;
    this.autoDismiss();
  }

  queueProgress(item: QueueItem): number {
    return item.progress;
  }

  queueIcon(item: QueueItem): 'clock' | 'check' | 'alert' | 'upload' {
    if (item.status === 'done') {
      return 'check';
    }
    if (item.status === 'failed') {
      return 'alert';
    }
    if (item.status === 'uploading') {
      return 'upload';
    }
    return 'clock';
  }

  retryItem(item: QueueItem): void {
    this.queue.retry(item);
  }

  removeItem(id: string): void {
    this.queue.remove(id);
  }

  private startUploadSection(): void {
    this.showUploadSection = true;
    this.photosLoading = !this.firstLoadDone;
    this.refreshMyPhotos();
    this.ensurePolling();
  }

  private refreshMyPhotos(append = false): void {
    if (this.fetching) {
      this.refetchQueued = true;
      return;
    }
    if (!this.guestToken) {
      return;
    }
    this.fetching = true;
    const beforeId = append && this.photos.length > 0 ? this.photos[this.photos.length - 1].id : undefined;
    this.eventService.getPhotosByUploader(this.eventCode, this.uploaderName, this.guestToken, beforeId).subscribe({
      next: (list) => {
        this.fetching = false;
        this.guestLoadingMore = false;
        const photos = Array.isArray(list?.photos) ? list.photos : [];
        if (Array.isArray(list?.photos) || Array.isArray(list)) {
          if (append) {
            this.photos = this.mergePhotos(photos);
          } else if (this.photosChanged(photos)) {
            this.photos = this.mergePhotos(photos);
          }
        }
        this.guestHasMore = !!list?.hasMore;
        if (this.refetchQueued) {
          this.refetchQueued = false;
          this.refreshMyPhotos();
        }
        if (!this.firstLoadDone) {
          this.firstLoadDone = true;
          this.photosLoading = false;
        }
        this.ensurePolling();
      },
      error: () => {
        this.fetching = false;
        this.refetchQueued = false;
        this.guestLoadingMore = false;
        this.photosLoading = false;
        this.errorMessage = 'Failed to load your uploads.';
        this.autoDismiss();
      }
    });
  }

  loadMoreGuestPhotos(): void {
    if (!this.guestHasMore || this.guestLoadingMore || this.fetching) {
      return;
    }
    this.guestLoadingMore = true;
    this.refreshMyPhotos(true);
  }

  private pruneObjectUrls(activeIds: Set<string>): void {
    for (const key of Array.from(this.objectUrls.keys())) {
      if (!activeIds.has(key)) {
        const url = this.objectUrls.get(key);
        if (url) {
          URL.revokeObjectURL(url);
        }
        this.objectUrls.delete(key);
      }
    }
  }

  private mergePhotos(list: any[]): any[] {
    const byId = new Map<number, any>();
    for (const photo of this.photos) {
      if (photo?.id != null) {
        byId.set(photo.id, photo);
      }
    }
    for (const photo of list) {
      if (photo?.id != null) {
        byId.set(photo.id, photo);
      }
    }
    for (const item of this.queueItems) {
      if (item.status === 'done' && item.result?.id && !byId.has(item.result.id)) {
        byId.set(item.result.id, item.result);
      }
    }
    return Array.from(byId.values()).sort((a, b) => {
      const t = (photo: any) => (photo.uploadedAt ? new Date(photo.uploadedAt).getTime() : 0);
      return t(b) - t(a);
    });
  }

  /** True when the polled first page differs from what is already on screen (id prefix compare). */
  private photosChanged(fresh: any[]): boolean {
    if (!Array.isArray(fresh) || fresh.length === 0) {
      return true;
    }
    const n = Math.min(this.photos.length, fresh.length);
    if (n !== fresh.length) {
      return true;
    }
    for (let i = 0; i < n; i++) {
      if (this.photos[i]?.id !== fresh[i]?.id) {
        return true;
      }
    }
    return false;
  }

  private ensurePolling(): void {
    if (this.pollTimer || !this.showUploadSection || !this.guestToken) {
      return;
    }
    this.pollTimer = setInterval(() => {
      if (Date.now() - this.lastRealtimeEvent < 10_000) {
        return; // realtime is live, polling would only re-fetch the same first page
      }
      this.refreshMyPhotos();
    }, 4000);
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  downloadPhoto(photo: any): void {
    const link = document.createElement('a');
    link.href = photoSrcUtil(photo, 'full');
    link.download = downloadName(photo);
    link.rel = 'noopener';
    link.click();
  }

  openLightbox(index: number): void {
    this.lightboxIndex = index;
  }

  closeLightbox(): void {
    this.lightboxIndex = -1;
  }

  formatFileSize(bytes: number): string {
    if (bytes >= 1024 * 1024) {
      return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }
    if (bytes >= 1024) {
      return (bytes / 1024).toFixed(0) + ' KB';
    }
    return bytes + ' B';
  }

  get pendingCount(): number {
    return this.queueItems.filter((i) => i.status !== 'done').length;
  }

  get hasUploads(): boolean {
    return this.queueItems.length > 0;
  }

  toggleQueue(): void {
    this.queueExpanded = !this.queueExpanded;
  }

  setAutoUpload(enabled: boolean): void {
    this.queue.setAutoUpload(enabled);
  }

  uploadItem(item: QueueItem): void {
    this.queue.uploadNow(item.id);
  }

  uploadAllFiles(): void {
    this.queue.uploadAll();
  }

  queueThumb(item: QueueItem): string {
    let url = this.objectUrls.get(item.id);
    if (!url && item.file) {
      url = URL.createObjectURL(item.file);
      this.objectUrls.set(item.id, url);
    }
    return url || '';
  }

  isVideoItem(item: QueueItem): boolean {
    return item.mimeType.startsWith('video/');
  }

  openPreview(item: QueueItem): void {
    this.previewItem = item;
  }

  closePreview(): void {
    this.previewItem = null;
  }

  removePhoto(photo: any): void {
    if (!this.guestToken || this.deletingIds.has(photo.id)) {
      return;
    }
    this.errorMessage = '';
    this.pendingDelete = photo;
  }

  cancelDeletePhoto(): void {
    if (!this.deleteSubmitting) {
      this.pendingDelete = null;
    }
  }

  confirmDeletePhoto(): void {
    const photo = this.pendingDelete;
    if (!photo || !this.guestToken || this.deletingIds.has(photo.id)) {
      return;
    }
    this.deletingIds.add(photo.id);
    this.deleteSubmitting = true;
    this.eventService.deleteGuestPhoto(this.eventCode, photo.id, this.guestToken).subscribe({
      next: () => {
        this.photos = this.photos.filter((x) => x.id !== photo.id);
        this.deletingIds.delete(photo.id);
        this.pendingDelete = null;
        this.deleteSubmitting = false;
      },
      error: () => {
        this.deletingIds.delete(photo.id);
        this.pendingDelete = null;
        this.deleteSubmitting = false;
        this.errorMessage = 'Could not remove the photo. Please try again.';
        this.autoDismiss();
      }
    });
  }

  queueTotalProgress(): number {
    const active = this.queueItems.filter((i) => i.status !== 'done');
    if (active.length === 0) {
      return 100;
    }
    const sum = active.reduce((acc, i) => acc + i.progress, 0);
    return Math.round(sum / active.length);
  }

  get activeUploads(): number {
    return this.queueItems.filter((i) => i.status === 'queued' || i.status === 'uploading').length;
  }

  private autoDismiss(): void {
    setTimeout(() => {
      this.successMessage = '';
    }, 3000);
  }
}