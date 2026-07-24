import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { EventService } from '../../services/event.service';

@Component({
  selector: 'app-full-album',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './full-album.component.html',
  styleUrls: ['./full-album.component.css']
})
export class FullAlbumComponent implements OnInit {
  eventCode = '';
  fullAlbumToken = '';
  event: any = null;
  photos: any[] = [];
  errorMessage = '';
  loading = true;

  constructor(
    private route: ActivatedRoute,
    private eventService: EventService
  ) {}

  ngOnInit(): void {
    this.eventCode = this.route.snapshot.paramMap.get('code') || '';
    this.fullAlbumToken = this.route.snapshot.paramMap.get('token') || '';
    
    if (this.eventCode && this.fullAlbumToken) {
      this.loadFullAlbum();
    } else {
      this.errorMessage = 'Invalid album link';
      this.loading = false;
    }
  }

  loadFullAlbum(): void {
    this.eventService.getFullAlbum(this.fullAlbumToken).subscribe({
      next: (data) => {
        this.event = data;
        this.photos = data.photos || [];
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Album not found or link has expired';
        this.loading = false;
      }
    });
  }

  downloadPhoto(photo: any): void {
    const link = document.createElement('a');
    link.href = `http://localhost:8080${photo.fileUrl}`;
    link.download = photo.fileName;
    link.click();
  }
}
