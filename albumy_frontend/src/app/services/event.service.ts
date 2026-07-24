import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class EventService {
  private apiUrl = 'http://localhost:8080';

  constructor(private http: HttpClient) {}

  getEvents(token: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/events`, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  getEvent(id: number, token: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/events/${id}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  createEvent(name: string, date: string, startTime: string, token: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/events`, { name, date, startTime }, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  deleteEvent(id: number, token: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/events/${id}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  deletePhoto(photoId: number, token: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/events/photos/${photoId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  downloadEventPhotosAsZip(eventId: number, token: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/events/${eventId}/photos/zip`, {
      headers: { Authorization: `Bearer ${token}` },
      responseType: 'blob'
    });
  }

  getEventPublicInfo(eventCode: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/events/code/${eventCode}`);
  }

  isNameAvailable(eventCode: string, name: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.apiUrl}/events/code/${eventCode}/name-available?name=${name}`);
  }

  uploadPhoto(eventCode: string, uploaderName: string, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    if (uploaderName) {
      formData.append('uploaderName', uploaderName);
    }
    return this.http.post(`${this.apiUrl}/events/code/${eventCode}/photos`, formData);
  }

  getPhotosByUploader(eventCode: string, uploaderName: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/events/code/${eventCode}/photos?uploaderName=${uploaderName}`);
  }

  getFullAlbum(fullAlbumToken: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/events/full/${fullAlbumToken}`);
  }
}
