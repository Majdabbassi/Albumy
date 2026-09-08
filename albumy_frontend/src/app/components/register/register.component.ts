import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
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
        this.inviteMessage = response?.message || (this.inviteValid ? 'Invite is valid' : 'This invite is not valid');
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
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.inviteValid) {
      this.errorMessage = 'A valid invite link is required to register.';
      return;
    }

    this.authService.register(this.username, this.password, this.email, this.displayName, this.inviteToken).subscribe({
      next: (response) => {
        this.authService.saveToken(response.token);
        this.successMessage = 'Registration successful! Redirecting to dashboard...';
        setTimeout(() => {
          this.router.navigate(['/dashboard']);
        }, 1000);
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Registration failed';
      }
    });
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
