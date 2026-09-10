import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { EventService } from '../../services/event.service';
import { RealtimeService } from '../../services/realtime.service';
import { IconComponent } from '../../shared/icon/icon.component';
import { ConfirmModalComponent } from '../../shared/confirm-modal/confirm-modal.component';
import { fileUrl } from '../../config/api.config';
import { FormatDatePipe } from '../../shared/pipes/format-date.pipe';
import { FormatTimePipe } from '../../shared/pipes/format-time.pipe';
import { InitialsPipe } from '../../shared/pipes/initials.pipe';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent, ConfirmModalComponent, FormatDatePipe, FormatTimePipe, InitialsPipe],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  events: any[] = [];
  showCreateForm = false;
  newEventName = '';
  newEventDate = '';
  newEventStartTime = '';
  errorMessage = '';
  successMessage = '';
  liveToast = '';
  isAdmin = false;
  invites: any[] = [];
  latestInviteUrl = '';
  generatingInvite = false;
  creatingEvent = false;
  loadingEvents = true;
  userDisplay = '';
  uploadingCover = false;
  coverTargetId = 0;
  editingEvent: any = null;
  editEventName = '';
  editEventDate = '';
  editEventStartTime = '';
savingEvent = false;
  pendingDeleteEventId = 0;

  private realtimeSubs = new Subscription();
  private realtimeEventIds: number[] = [];

  constructor(
    private authService: AuthService,
    private eventService: EventService,
    private realtime: RealtimeService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const token = this.authService.getToken();
    if (!token) {
      this.router.navigate(['/login']);
      return;
    }

    this.isAdmin = this.authService.isAdmin();
    this.userDisplay = this.authService.getUsername() || '';
    this.loadEvents();

    if (this.isAdmin) {
      this.loadInvites();
    }
  }

  ngOnDestroy(): void {
    this.realtimeSubs.unsubscribe();
    for (const eventId of this.realtimeEventIds) {
      this.realtime.offEvent(eventId);
    }
  }

  loadInvites(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.authService.getInvites(token).subscribe({
      next: (data) => {
        this.invites = data;
      },
      error: () => {
        this.errorMessage = 'Failed to load invites';
      }
    });
  }

  generateInvite(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.errorMessage = '';
    this.successMessage = '';
    this.generatingInvite = true;

    this.authService.createInvite(token).subscribe({
      next: (invite) => {
        this.latestInviteUrl = invite?.registrationUrl || '';
        this.loadInvites();
        this.generatingInvite = false;
        this.successMessage = 'Invite link generated. Send it to a future organizer.';
        this.autoDismiss();
      },
      error: () => {
        this.generatingInvite = false;
        this.errorMessage = 'Failed to generate invite';
      }
    });
  }

  copyText(text: string): void {
    navigator.clipboard.writeText(text).then(
      () => {
        this.successMessage = 'Copied to clipboard!';
        this.autoDismiss();
      },
      () => {
        this.errorMessage = 'Could not copy to clipboard';
      }
    );
  }

  loadEvents(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.loadingEvents = true;
    this.eventService.getEvents(token).subscribe({
      next: (data) => {
        this.events = data;
        this.loadingEvents = false;
        this.wireRealtime(data, token);
      },
      error: (err) => {
        this.loadingEvents = false;
        this.errorMessage = 'Failed to load events: ' + (err.error?.message || err.message);
      }
    });
  }

  private wireRealtime(events: any[], token: string | null): void {
    this.realtime.connect();
    this.realtimeSubs.unsubscribe();
    this.realtimeSubs = new Subscription();
    const newIds = new Set(events.map((e) => e.id));
    for (const id of this.realtimeEventIds) {
      if (!newIds.has(id)) {
        this.realtime.offEvent(id);
      }
    }
    this.realtimeEventIds = events.map((e) => e.id);
    for (const event of events) {
      this.realtimeSubs.add(
        this.realtime.onEvent(event.id, {
          Authorization: `Bearer ${token}`
        }).subscribe((msg) => this.handleRealtime(event, msg))
      );
    }
  }

  private handleRealtime(event: any, msg: any): void {
    const target = this.events.find((e) => e.id === event.id);
    if (!target) {
      return;
    }
    if (msg.type === 'PHOTO_ADDED' || msg.type === 'PHOTO_READY') {
      target.photoCount = (target.photoCount || 0) + (msg.type === 'PHOTO_ADDED' ? 1 : 0);
      if (msg.type === 'PHOTO_READY' && msg.data?.thumbUrl && !target.coverUrl) {
        target.coverThumb = msg.data.thumbUrl;
      }
      if (msg.type === 'PHOTO_ADDED') {
        this.liveToast = `New upload in "${target.name}"`;
        this.autoDismissToast();
      }
    } else if (msg.type === 'PHOTO_REMOVED') {
      target.photoCount = Math.max(0, (target.photoCount || 0) - 1);
    }
  }

  coverThumb(event: any): string {
    return event.coverUrl ? fileUrl(event.coverUrl) : (event.coverThumb ? fileUrl(event.coverThumb) : '');
  }

  pickCover(event: any): void {
    const input = document.getElementById('coverFileInput') as HTMLInputElement | null;
    this.coverTargetId = event.id;
    input?.click();
  }

  onCoverSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.coverTargetId) {
      return;
    }
    const token = this.authService.getToken();
    if (!token) {
      return;
    }
    this.uploadingCover = true;
    this.eventService.setEventCover(this.coverTargetId, file, token).subscribe({
      next: () => {
        this.uploadingCover = false;
        this.coverTargetId = 0;
        this.successMessage = 'Cover updated.';
        this.autoDismiss();
        this.loadEvents();
      },
      error: (err) => {
        this.uploadingCover = false;
        this.coverTargetId = 0;
        this.errorMessage = err.error?.message || 'Failed to update cover';
      }
    });
  }

  private autoDismissToast(): void {
    setTimeout(() => {
      this.liveToast = '';
    }, 3500);
  }

  toggleCreateForm(): void {
    this.showCreateForm = !this.showCreateForm;
    this.errorMessage = '';
    this.successMessage = '';
  }

  createEvent(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.errorMessage = '';
    this.successMessage = '';
    this.creatingEvent = true;

    this.eventService.createEvent(this.newEventName, this.newEventDate, this.newEventStartTime, token).subscribe({
      next: () => {
        this.creatingEvent = false;
        this.successMessage = 'Event created successfully!';
        this.newEventName = '';
        this.newEventDate = '';
        this.newEventStartTime = '';
        this.showCreateForm = false;
        this.loadEvents();
        this.autoDismiss();
      },
      error: (err) => {
        this.creatingEvent = false;
        this.errorMessage = err.error?.message || 'Failed to create event';
      }
    });
  }

  deleteEvent(eventId: number): void {
    this.pendingDeleteEventId = eventId;
  }

  confirmDeleteEvent(): void {
    const eventId = this.pendingDeleteEventId;
    if (!eventId) {
      return;
    }

    const token = this.authService.getToken();
    if (!token) return;

    this.eventService.deleteEvent(eventId, token).subscribe({
      next: () => {
        this.pendingDeleteEventId = 0;
        this.loadEvents();
        this.successMessage = 'Event deleted.';
        this.autoDismiss();
      },
      error: () => {
        this.pendingDeleteEventId = 0;
        this.errorMessage = 'Failed to delete event';
      }
    });
  }

  cancelDeleteEvent(): void {
    this.pendingDeleteEventId = 0;
  }

  viewEvent(eventId: number): void {
    this.router.navigate(['/dashboard/events', eventId]);
  }

  openEdit(event: any): void {
    this.editingEvent = event;
    this.editEventName = event.name;
    this.editEventDate = event.date;
    this.editEventStartTime = (event.startTime || '00:00').slice(0, 5);
    this.errorMessage = '';
    this.successMessage = '';
  }

  closeEdit(): void {
    if (this.savingEvent) {
      return;
    }
    this.editingEvent = null;
  }

  saveEvent(): void {
    const event = this.editingEvent;
    if (!event || this.savingEvent) {
      return;
    }
    const token = this.authService.getToken();
    if (!token) {
      return;
    }

    this.savingEvent = true;
    this.eventService.updateEvent(event.id, this.editEventName, this.editEventDate, this.editEventStartTime, token).subscribe({
      next: () => {
        this.savingEvent = false;
        this.editingEvent = null;
        this.successMessage = 'Event updated.';
        this.autoDismiss();
        this.loadEvents();
      },
      error: (err) => {
        this.savingEvent = false;
        this.errorMessage = err.error?.message || 'Failed to update event';
      }
    });
  }

  logout(): void {
    this.authService.removeToken();
    this.router.navigate(['/login']);
  }

  private autoDismiss(): void {
    setTimeout(() => {
      this.successMessage = '';
      this.errorMessage = '';
    }, 3000);
  }
}