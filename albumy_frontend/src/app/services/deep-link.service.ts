import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { App as CapacitorApp } from '@capacitor/app';

@Injectable({
  providedIn: 'root'
})
export class DeepLinkService {
  constructor(private router: Router) {}

  init(): void {
    try {
      CapacitorApp.addListener('appUrlOpen', (data) => {
        this.routeDeepLink(data.url);
      });
    } catch {
      // Web platform: no native shell to report app-open events.
    }
  }

  private routeDeepLink(url: string): void {
    try {
      const parsed = new URL(url);
      if (parsed.protocol === 'albumy:') {
        const code = parsed.searchParams.get('code');
        if (code) {
          this.router.navigate(['/e', code]);
        }
        return;
      }
      const match = parsed.pathname.match(/^\/e\/([^/]+)(?:\/full\/([^/]+))?$/);
      if (match) {
        if (match[2]) {
          this.router.navigate(['/e', match[1], 'full', match[2]]);
        } else {
          this.router.navigate(['/e', match[1]]);
        }
      }
    } catch {
      // ignore malformed URLs
    }
  }
}