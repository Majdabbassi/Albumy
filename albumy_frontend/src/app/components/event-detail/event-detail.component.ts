import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { EventService } from '../../services/event.service';
import { RealtimeService, RealtimeMessage } from '../../services/realtime.service';
import { IconComponent } from '../../shared/icon/icon.component';
import { LightboxComponent } from '../../shared/lightbox/lightbox.component';
import { ConfirmModalComponent } from '../../shared/confirm-modal/confirm-modal.component';
import { QRCodeComponent } from 'angularx-qrcode';
import { TimeAgoPipe } from '../../shared/pipes/time-ago.pipe';
import { FormatDatePipe } from '../../shared/pipes/format-date.pipe';
import { FormatTimePipe } from '../../shared/pipes/format-time.pipe';
import { MediaUrlPipe } from '../../shared/pipes/media-url.pipe';
import { IsVideoPipe } from '../../shared/pipes/is-video.pipe';

interface Activity {
  message: string;
  time: Date;
}

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent, QRCodeComponent, LightboxComponent, ConfirmModalComponent, TimeAgoPipe, FormatDatePipe, FormatTimePipe, MediaUrlPipe, IsVideoPipe],
  templateUrl: './event-detail.component.html',
  styleUrls: ['./event-detail.component.css']
})
export class EventDetailComponent implements OnInit, OnDestroy {
  event: any = null;
  photos: any[] = [];
  eventId = 0;
  errorMessage = '';
  successMessage = '';
  fullAlbumUrl = '';
  guestUrl = '';
  loading = true;
  downloadingZip = false;
  lightboxIndex = -1;
  live = false;
  photosLoading = false;
  activityFeed: Activity[] = [];
  beforeId: number | null = null;
  hasMore = false;
  loadingMore = false;
  initialLimit = 60;
  pendingDeletePhotoId = 0;
  pendingDeleteEvent = false;

  private realtimeSub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private eventService: EventService,
    private realtime: RealtimeService
  ) {}

  ngOnInit(): void {
    const token = this.authService.getToken();
    if (!token) {
      this.router.navigate(['/login']);
      return;
    }

    this.eventId = Number(this.route.snapshot.paramMap.get('id'));
    if (this.eventId) {
      this.loadEvent();
    }
  }

  ngOnDestroy(): void {
    this.realtimeSub?.unsubscribe();
    if (this.eventId) {
      this.realtime.offEvent(this.eventId);
    }
  }

  loadEvent(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.beforeId = null;
    this.hasMore = false;
    this.photosLoading = true;

    this.eventService.getEvent(this.eventId, token, undefined, this.initialLimit).subscribe({
      next: (data) => {
        this.event = data;
        this.photos = data.photos || [];
        this.fullAlbumUrl = `${window.location.origin}/e/${data.eventCode}/full/${data.fullAlbumToken}`;
        this.guestUrl = `${window.location.origin}/e/${data.eventCode}`;
        this.hasMore = (data.photoCount || 0) > this.photos.length;
        this.loading = false;
        this.photosLoading = false;
        this.watchRealtime();
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to load event details';
        this.loading = false;
        this.photosLoading = false;
      }
    });
  }

  loadMore(): void {
    if (!this.hasMore || this.loadingMore || this.photos.length === 0) {
      return;
    }
    const token = this.authService.getToken();
    if (!token) return;

    this.loadingMore = true;
    const beforeId = this.photos[this.photos.length - 1].id;
    this.eventService.getEvent(this.eventId, token, beforeId, this.initialLimit).subscribe({
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
    this.realtime.connect();
    this.realtimeSub = this.realtime.onEvent(this.eventId, {
      Authorization: `Bearer ${this.authService.getToken()}`
    }).subscribe((msg) => {
      this.live = true;
      this.handleRealtime(msg);
    });
  }

  private handleRealtime(msg: RealtimeMessage): void {
    if (msg.type === 'PHOTO_ADDED') {
      if (msg.data?.id) {
        this.upsertPhoto(msg.data);
        this.pushActivity(`${msg.data.uploaderName || 'A guest'} added a photo`);
      }
      return;
    }
    if (msg.type === 'PHOTO_READY') {
      if (msg.data?.id) {
        this.upsertPhoto(msg.data);
      }
      return;
    }
    if (msg.type === 'PHOTO_REMOVED') {
      this.photos = this.photos.filter((p) => p.id !== msg.data?.id);
      if (this.event && msg.data?.id) {
        this.event.photoCount = Math.max(0, (this.event.photoCount || 0) - 1);
      }
      this.pushActivity('A photo was removed');
      return;
    }
    if (msg.type === 'ACTIVITY' && msg.message) {
      this.pushActivity(msg.message);
    }
  }

  private upsertPhoto(photo: any): void {
    const existing = this.photos.find((p) => p.id === photo.id);
    if (existing) {
      Object.assign(existing, photo);
      this.photos = [...this.photos];
    } else {
      this.photos = [photo, ...this.photos];
      if (this.event) {
        this.event.photoCount = (this.event.photoCount || 0) + 1;
      }
    }
  }

  private pushActivity(message: string): void {
    this.activityFeed = [
      { message, time: new Date() },
      ...this.activityFeed
    ].slice(0, 8);
  }

  deletePhoto(photoId: number): void {
    this.pendingDeletePhotoId = photoId;
  }

  confirmDeletePhoto(): void {
    const photoId = this.pendingDeletePhotoId;
    if (!photoId) {
      return;
    }

    const token = this.authService.getToken();
    if (!token) return;

    this.eventService.deletePhoto(photoId, token).subscribe({
      next: () => {
        this.photos = this.photos.filter((p) => p.id !== photoId);
        if (this.event) {
          this.event.photoCount = Math.max(0, (this.event.photoCount || 0) - 1);
        }
        this.pendingDeletePhotoId = 0;
        this.successMessage = 'Photo deleted.';
        this.autoDismiss();
      },
      error: () => {
        this.pendingDeletePhotoId = 0;
        this.errorMessage = 'Failed to delete photo';
      }
    });
  }

  cancelDeletePhoto(): void {
    this.pendingDeletePhotoId = 0;
  }

  deleteEvent(): void {
    this.pendingDeleteEvent = true;
  }

  confirmDeleteEvent(): void {
    this.pendingDeleteEvent = false;

    const token = this.authService.getToken();
    if (!token) return;

    this.eventService.deleteEvent(this.eventId, token).subscribe({
      next: () => {
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.errorMessage = 'Failed to delete event';
      }
    });
  }

  cancelDeleteEvent(): void {
    this.pendingDeleteEvent = false;
  }

  downloadAllPhotos(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.downloadingZip = true;
    this.eventService.downloadEventPhotosAsZip(this.eventId, token).subscribe({
      next: (blob) => {
        this.downloadingZip = false;
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${this.safeName(this.event.name)}-photos.zip`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.downloadingZip = false;
        this.errorMessage = 'Failed to download photos';
      }
    });
  }

  shareGuest(): void {
    this.share({
      title: `${this.event?.name || 'Albumy'} — share your photos`,
      text: `Share your photos of this event:`,
      url: this.guestUrl
    }, 'Guest link');
  }

  shareFullAlbum(): void {
    this.share({
      title: this.event?.name || 'Albumy',
      text: 'Check out our event photos:',
      url: this.fullAlbumUrl
    }, 'Full album link');
  }

  private share(payload: { title: string; text: string; url: string }, fallbackLabel: string): void {
    const nav = navigator as any;
    if (typeof nav.share === 'function') {
      nav.share(payload)
        .then(() => {
          // shared natively
        })
        .catch((err: any) => {
          if (err?.name === 'AbortError') {
            return;
          }
          this.copyText(payload.url, fallbackLabel);
        });
    } else {
      this.copyText(payload.url, fallbackLabel);
    }
  }

  downloadQr(): void {
    const canvas = this.findQrCanvas();
    if (!canvas) {
      this.errorMessage = 'QR code is not ready yet.';
      return;
    }
    canvas.toBlob((blob: Blob | null) => {
      if (!blob) {
        this.errorMessage = 'Could not generate QR image.';
        return;
      }
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${this.safeName(this.event.name)}-guest-qr.png`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }, 'image/png');
  }

  printQr(): void {
    const canvas = this.findQrCanvas();
    if (!canvas) {
      this.errorMessage = 'QR code is not ready yet.';
      return;
    }
    const url = this.event?.name || 'event';
    const win = window.open('', '_blank', 'width=480,height=640');
    if (!win) {
      return;
    }
    win.document.write(
      `<html><head><title>Guest QR — ${escapeHtml(url)}</title>` +
      `<style>body{font-family:sans-serif;text-align:center;padding:32px;}` +
      `h1{font-size:20px;margin:0 0 20px;}img{width:320px;height:320px;image-rendering:pixelated;}</style>` +
      `</head><body><h1>${escapeHtml(url)}</h1>` +
      `<img src="${canvas.toDataURL('image/png')}" alt="Guest QR code">` +
      `<p>Scan to open the album and add your photos.</p>` +
      `<script>window.onload=function(){window.focus();window.print();}<\/script></body></html>`
    );
    win.document.close();
  }

  private findQrCanvas(): HTMLCanvasElement | null {
    return document.querySelector<HTMLCanvasElement>('.qr-code canvas');
  }

  copyText(text: string, label: string): void {
    navigator.clipboard.writeText(text).then(
      () => {
        this.successMessage = `${label} copied to clipboard!`;
        this.autoDismiss();
      },
      () => {
        this.errorMessage = 'Could not copy to clipboard';
      }
    );
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }

  openLightbox(index: number): void {
    this.lightboxIndex = index;
  }

  closeLightbox(): void {
    this.lightboxIndex = -1;
  }

  private safeName(name: string): string {
    return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'event';
  }

  private autoDismiss(): void {
    setTimeout(() => {
      this.successMessage = '';
    }, 3000);
  }
}

// eslint-disable-next-line @typescript-eslint/naming-convention
function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}