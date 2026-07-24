import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8080';

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/auth/login`, { username, password });
  }

  register(username: string, password: string, email: string, displayName: string, inviteToken: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/auth/register`, { username, password, email, displayName, inviteToken });
  }

  validateInvite(token: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.apiUrl}/invites/${token}`);
  }

  createInvite(token: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/admin/invites`, {}, {
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
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }
}
