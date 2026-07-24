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

  register(username: string, password: string, email: string, displayName: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/auth/register`, { username, password, email, displayName });
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

  getUserRole(): string | null {
    const token = this.getToken();
    if (!token) return null;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      // Spring Security JWT stores roles in authorities array with ROLE_ prefix
      const authorities = payload.authorities || [];
      if (authorities.length > 0) {
        const role = authorities[0].replace('ROLE_', '');
        return role;
      }
      // Fallback to role field if it exists
      return payload.role || null;
    } catch (e) {
      console.error('Error parsing JWT:', e);
      return null;
    }
  }

  isAdmin(): boolean {
    return this.getUserRole() === 'ADMIN';
  }
}
