import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { EventService } from '../../services/event.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  events: any[] = [];
  showCreateForm = false;
  newEventName = '';
  newEventDate = '';
  newEventStartTime = '';
  errorMessage = '';
  successMessage = '';
  isAdmin = false;
  invites: any[] = [];
  latestInviteUrl = '';

  constructor(
    private authService: AuthService,
    private eventService: EventService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const token = this.authService.getToken();
    if (!token) {
      this.router.navigate(['/login']);
      return;
    }

    this.isAdmin = this.authService.isAdmin();
    this.loadEvents();

    if (this.isAdmin) {
      this.loadInvites();
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

    this.authService.createInvite(token).subscribe({
      next: (invite) => {
        this.latestInviteUrl = invite?.registrationUrl || '';
        this.loadInvites();
        this.successMessage = 'Invite link generated! Copy it and send it to a future organizer.';
      },
      error: () => {
        this.errorMessage = 'Failed to generate invite';
      }
    });
  }

  copyInviteUrl(url: string): void {
    navigator.clipboard.writeText(url);
    this.successMessage = 'Invite URL copied to clipboard!';
    setTimeout(() => {
      this.successMessage = '';
    }, 2000);
  }

  loadEvents(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.eventService.getEvents(token).subscribe({
      next: (data) => {
        this.events = data;
      },
      error: (err) => {
        this.errorMessage = 'Failed to load events: ' + (err.error?.message || err.message);
      }
    });
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

    this.eventService.createEvent(this.newEventName, this.newEventDate, this.newEventStartTime, token).subscribe({
      next: () => {
        this.successMessage = 'Event created successfully!';
        this.newEventName = '';
        this.newEventDate = '';
        this.newEventStartTime = '';
        this.showCreateForm = false;
        this.loadEvents();
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to create event';
      }
    });
  }

  deleteEvent(eventId: number): void {
    if (!confirm('Are you sure you want to delete this event? All photos will be deleted.')) {
      return;
    }

    const token = this.authService.getToken();
    if (!token) return;

    this.eventService.deleteEvent(eventId, token).subscribe({
      next: () => {
        this.loadEvents();
      },
      error: () => {
        this.errorMessage = 'Failed to delete event';
      }
    });
  }

  viewEvent(eventId: number): void {
    this.router.navigate(['/dashboard/events', eventId]);
  }

  logout(): void {
    this.authService.removeToken();
    this.router.navigate(['/login']);
  }
}
