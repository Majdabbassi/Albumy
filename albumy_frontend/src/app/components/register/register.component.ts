import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { IconComponent } from '../../shared/icon/icon.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent implements OnInit {
  username = '';
  password = '';
  email = '';
  displayName = '';
  inviteToken = '';
  inviteValid = false;
  inviteMessage = '';
  checkingInvite = false;
  errorMessage = '';
  successMessage = '';
  submitting = false;

  constructor(
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.inviteToken = this.route.snapshot.queryParamMap.get('invite') || '';
    if (!this.inviteToken) {
      this.inviteMessage = 'Registration is invite-only. Please open the registration link you received from an admin.';
      return;
    }

    this.checkingInvite = true;
    this.authService.validateInvite(this.inviteToken).subscribe({
      next: (response) => {
        this.inviteValid = response?.valid === true;
        this.inviteMessage = response?.message || (this.inviteValid ? 'Your invite is valid. Create your organizer account.' : 'This invite is not valid');
        this.checkingInvite = false;
      },
      error: () => {
        this.inviteValid = false;
        this.inviteMessage = 'This invite is not valid';
        this.checkingInvite = false;
      }
    });
  }

  onSubmit(): void {
    if (this.submitting) {
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    if (!this.inviteValid) {
      this.errorMessage = 'A valid invite link is required to register.';
      return;
    }

    this.submitting = true;
    this.authService.register(this.username, this.password, this.email, this.displayName, this.inviteToken).subscribe({
      next: (response) => {
        this.authService.saveToken(response.token);
        this.authService.saveRole(response.role);
        this.successMessage = 'Registration successful! Redirecting to your dashboard…';
        setTimeout(() => {
          this.router.navigate(['/dashboard']);
        }, 1000);
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'Registration failed';
      }
    });
  }
}