import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { EventService } from '../../services/event.service';
import { QRCodeModule } from 'angularx-qrcode';

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, QRCodeModule],
  templateUrl: './event-detail.component.html',
  styleUrls: ['./event-detail.component.css']
})
export class EventDetailComponent implements OnInit {
  event: any = null;
  photos: any[] = [];
  eventId: number = 0;
  errorMessage = '';
  successMessage = '';
  fullAlbumUrl = '';
  guestUrl = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private eventService: EventService
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

  loadEvent(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.eventService.getEvent(this.eventId, token).subscribe({
      next: (data) => {
        this.event = data;
        this.photos = data.photos || [];
        this.fullAlbumUrl = `${window.location.origin}/e/${data.eventCode}/full/${data.fullAlbumToken}`;
        this.guestUrl = `${window.location.origin}/e/${data.eventCode}`;
      },
      error: () => {
        this.errorMessage = 'Failed to load event details';
      }
    });
  }

  deletePhoto(photoId: number): void {
    if (!confirm('Are you sure you want to delete this photo?')) {
      return;
    }

    const token = this.authService.getToken();
    if (!token) return;

    this.eventService.deletePhoto(photoId, token).subscribe({
      next: () => {
        this.loadEvent();
        this.successMessage = 'Photo deleted successfully';
        setTimeout(() => {
          this.successMessage = '';
        }, 2000);
      },
      error: () => {
        this.errorMessage = 'Failed to delete photo';
      }
    });
  }

  deleteEvent(): void {
    if (!confirm('Are you sure you want to delete this event? All photos will be permanently deleted.')) {
      return;
    }

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

  downloadAllPhotos(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.eventService.downloadEventPhotosAsZip(this.eventId, token).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `event-${this.eventId}-photos.zip`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.errorMessage = 'Failed to download photos';
      }
    });
  }

  copyGuestUrl(): void {
    navigator.clipboard.writeText(this.guestUrl);
    this.successMessage = 'Guest URL copied to clipboard!';
    setTimeout(() => {
      this.successMessage = '';
    }, 2000);
  }

  copyFullAlbumUrl(): void {
    navigator.clipboard.writeText(this.fullAlbumUrl);
    this.successMessage = 'Full album URL copied to clipboard!';
    setTimeout(() => {
      this.successMessage = '';
    }, 2000);
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }
}
