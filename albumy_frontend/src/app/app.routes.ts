import { Routes } from '@angular/router';
import { RegisterComponent } from './components/register/register.component';
import { LoginComponent } from './components/login/login.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { EventDetailComponent } from './components/event-detail/event-detail.component';
import { GuestComponent } from './components/guest/guest.component';
import { FullAlbumComponent } from './components/full-album/full-album.component';

export const routes: Routes = [
  { path: 'register', component: RegisterComponent },
  { path: 'login', component: LoginComponent },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'dashboard/events/:id', component: EventDetailComponent },
  { path: 'e/:code', component: GuestComponent },
  { path: 'e/:code/full/:token', component: FullAlbumComponent },
  { path: '', redirectTo: '/login', pathMatch: 'full' }
];
