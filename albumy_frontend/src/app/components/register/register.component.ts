import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
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
  inviteValid: boolean | null = null;
  errorMessage = '';
  successMessage = '';

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.inviteToken = params['invite'] || '';
      if (this.inviteToken) {
        this.validateInvite();
      }
    });
  }

  validateInvite(): void {
    this.authService.validateInvite(this.inviteToken).subscribe({
      next: (isValid) => {
        this.inviteValid = isValid;
        if (!isValid) {
          this.errorMessage = 'Invalid or expired invite token';
        }
      },
      error: () => {
        this.inviteValid = false;
        this.errorMessage = 'Invalid or expired invite token';
      }
    });
  }

  onSubmit(): void {
    if (!this.inviteValid) {
      this.errorMessage = 'Invalid invite token';
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';

    this.authService.register(this.username, this.password, this.email, this.displayName, this.inviteToken).subscribe({
      next: () => {
        this.successMessage = 'Registration successful! Redirecting to login...';
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 2000);
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
