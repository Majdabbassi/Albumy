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
  inviteToken = '';
  inviteUrl = '';
  showInviteSection = false;

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
    
    this.loadEvents();
  }

  loadEvents(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.eventService.getEvents(token).subscribe({
      next: (data) => {
        this.events = data;
      },
      error: () => {
        this.errorMessage = 'Failed to load events';
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

  generateInvite(): void {
    const token = this.authService.getToken();
    if (!token) return;

    this.eventService.createInvite(token).subscribe({
      next: (response) => {
        this.inviteToken = response.token;
        this.inviteUrl = response.registrationUrl;
        this.showInviteSection = true;
      },
      error: () => {
        this.errorMessage = 'Failed to generate invite. Admin access required.';
      }
    });
  }

  copyInviteUrl(): void {
    navigator.clipboard.writeText(this.inviteUrl);
    this.successMessage = 'Invite URL copied to clipboard!';
    setTimeout(() => {
      this.successMessage = '';
    }, 2000);
  }
}
