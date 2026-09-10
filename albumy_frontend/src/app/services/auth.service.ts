import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { resolveApiUrl } from '../config/api.config';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly apiUrl = resolveApiUrl();

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/auth/login`, { username, password });
  }

  register(username: string, password: string, email: string, displayName: string, inviteToken: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/auth/register`, { username, password, email, displayName, inviteToken });
  }

  validateInvite(token: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/invites/${token}`);
  }

  createInvite(token: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/admin/invites`, null, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  getInvites(token: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/admin/invites`, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  saveToken(token: string): void {
    localStorage.setItem('jwt_token', token);
  }

  getToken(): string | null {
    return localStorage.getItem('jwt_token');
  }

  removeToken(): void {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('jwt_role');
  }

  saveRole(role: string): void {
    localStorage.setItem('jwt_role', role);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  getUserRole(): string | null {
    const token = this.getToken();
    if (!token) {
      return null;
    }
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.role || null;
    } catch {
      return null;
    }
  }

  getUsername(): string | null {
    const token = this.getToken();
    if (!token) return null;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.sub || payload.username || null;
    } catch (e) {
      console.error('Error parsing JWT:', e);
      return null;
    }
  }

  isAdmin(): boolean {
    return this.getUserRole() === 'ADMIN';
  }
}
