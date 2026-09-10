import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { resolveApiUrl } from '../config/api.config';

@Injectable({
  providedIn: 'root'
})
export class EventService {
  private readonly apiUrl = resolveApiUrl();

  constructor(private http: HttpClient) {}

  getEvents(token: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/events`, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  getEvent(id: number, token: string, beforeId?: number, limit = 60): Observable<any> {
    let params: any = { limit };
    if (beforeId) {
      params.beforeId = beforeId;
    }
    return this.http.get(`${this.apiUrl}/events/${id}`, {
      headers: { Authorization: `Bearer ${token}` },
      params
    });
  }

  createEvent(name: string, date: string, startTime: string, token: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/events`, { name, date, startTime }, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  updateEvent(id: number, name: string, date: string, startTime: string, token: string): Observable<any> {
    return this.http.put(`${this.apiUrl}/events/${id}`, { name, date, startTime }, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  deleteEvent(id: number, token: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/events/${id}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  setEventCover(id: number, file: File, token: string): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post(`${this.apiUrl}/events/${id}/cover`, formData, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  deletePhoto(photoId: number, token: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/events/photos/${photoId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  deleteGuestPhoto(eventCode: string, photoId: number, guestToken: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/events/code/${eventCode}/photos/${photoId}`, {
      headers: { 'X-Guest-Token': guestToken }
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
    return this.http.get<boolean>(`${this.apiUrl}/events/code/${eventCode}/name-available?name=${encodeURIComponent(name)}`);
  }

  claimGuest(eventCode: string, name: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/events/code/${eventCode}/claim`, { name });
  }

  getPhotosByUploader(eventCode: string, uploaderName: string, guestToken?: string, beforeId?: number, limit = 60): Observable<any> {
    const params: any = { limit };
    if (beforeId) {
      params.beforeId = beforeId;
    }
    if (!guestToken && uploaderName) {
      params.uploaderName = uploaderName;
    }
    const headers: any = guestToken ? { 'X-Guest-Token': guestToken } : {};
    return this.http.get(`${this.apiUrl}/events/code/${eventCode}/photos`, { params, headers });
  }

  getFullAlbum(fullAlbumToken: string, beforeId?: number, limit = 60): Observable<any> {
    const params: any = { limit };
    if (beforeId) {
      params.beforeId = beforeId;
    }
    return this.http.get(`${this.apiUrl}/events/full/${fullAlbumToken}`, { params });
  }

  // ------------------------------------------------------------------
  // Chunked / resumable uploads
  // ------------------------------------------------------------------

  initChunkedUpload(eventCode: string, guestToken: string, fileName: string, mimeType: string, size: number, totalChunks: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/uploads`, {
      eventCode,
      guestToken,
      fileName,
      mimeType,
      size,
      totalChunks
    });
  }

  uploadChunk(uploadId: string, chunkIndex: number, data: Blob): Observable<any> {
    return this.http.put(`${this.apiUrl}/uploads/${uploadId}/chunks/${chunkIndex}`, data, {
      headers: { 'Content-Type': 'application/octet-stream' }
    });
  }

  getReceivedChunks(uploadId: string): Observable<number[]> {
    return this.http.get<number[]>(`${this.apiUrl}/uploads/${uploadId}/chunks`);
  }

  completeChunkedUpload(uploadId: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/uploads/${uploadId}/complete`, {});
  }
}