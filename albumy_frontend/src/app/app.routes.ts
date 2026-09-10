import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'register',
    loadComponent: () =>
      import('./components/register/register.component').then((m) => m.RegisterComponent)
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./components/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./components/dashboard/dashboard.component').then((m) => m.DashboardComponent)
  },
  {
    path: 'dashboard/events/:id',
    loadComponent: () =>
      import('./components/event-detail/event-detail.component').then((m) => m.EventDetailComponent)
  },
  {
    path: 'e/:code',
    loadComponent: () =>
      import('./components/guest/guest.component').then((m) => m.GuestComponent)
  },
  {
    path: 'e/:code/full/:token',
    loadComponent: () =>
      import('./components/full-album/full-album.component').then((m) => m.FullAlbumComponent)
  },
  { path: '', redirectTo: '/login', pathMatch: 'full' }
];