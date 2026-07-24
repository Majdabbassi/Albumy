import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { EventService } from '../../services/event.service';

@Component({
  selector: 'app-guest',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './guest.component.html',
  styleUrls: ['./guest.component.css']
})
export class GuestComponent implements OnInit {
  eventCode = '';
  eventInfo: any = null;
  uploaderName = '';
  nameAvailable: boolean | null = null;
  nameChecking = false;
  showUploadSection = false;
  selectedFiles: File[] = [];
  uploading = false;
  uploadProgress = 0;
  photos: any[] = [];
  errorMessage = '';
  successMessage = '';
  loading = true;

  constructor(
    private route: ActivatedRoute,
    private eventService: EventService
  ) {}

  ngOnInit(): void {
    this.eventCode = this.route.snapshot.paramMap.get('code') || '';
    if (this.eventCode) {
      this.loadEventInfo();
      this.checkStoredName();
    }
  }

  loadEventInfo(): void {
    this.eventService.getEventPublicInfo(this.eventCode).subscribe({
      next: (data) => {
        this.eventInfo = data;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Event not found';
        this.loading = false;
      }
    });
  }

  checkStoredName(): void {
    const storedName = localStorage.getItem(`guest_name_${this.eventCode}`);
    if (storedName) {
      this.uploaderName = storedName;
      this.showUploadSection = true;
      this.loadMyPhotos();
    }
  }

  checkNameAvailability(): void {
    if (!this.uploaderName || this.uploaderName.length < 2) {
      this.nameAvailable = false;
      return;
    }

    this.nameChecking = true;
    this.eventService.isNameAvailable(this.eventCode, this.uploaderName).subscribe({
      next: (available) => {
        this.nameAvailable = available;
        this.nameChecking = false;
      },
      error: () => {
        this.nameAvailable = false;
        this.nameChecking = false;
      }
    });
  }

  submitName(): void {
    if (!this.nameAvailable) {
      this.errorMessage = 'Please choose a different name';
      return;
    }

    localStorage.setItem(`guest_name_${this.eventCode}`, this.uploaderName);
    this.showUploadSection = true;
    this.loadMyPhotos();
  }

  onFileSelected(event: any): void {
    const files: FileList = event.target.files;
    if (files) {
      this.selectedFiles = Array.from(files);
    }
  }

  uploadPhotos(): void {
    if (this.selectedFiles.length === 0) {
      this.errorMessage = 'Please select at least one file';
      return;
    }

    this.uploading = true;
    this.uploadProgress = 0;
    this.errorMessage = '';
    this.successMessage = '';

    let uploadedCount = 0;
    const totalFiles = this.selectedFiles.length;

    this.selectedFiles.forEach((file, index) => {
      this.eventService.uploadPhoto(this.eventCode, this.uploaderName, file).subscribe({
        next: () => {
          uploadedCount++;
          this.uploadProgress = Math.round((uploadedCount / totalFiles) * 100);
          
          if (uploadedCount === totalFiles) {
            this.uploading = false;
            this.successMessage = `${totalFiles} photo(s) uploaded successfully!`;
            this.selectedFiles = [];
            this.loadMyPhotos();
            setTimeout(() => {
              this.successMessage = '';
            }, 3000);
          }
        },
        error: (err) => {
          this.uploading = false;
          this.errorMessage = err.error?.message || 'Failed to upload photo';
        }
      });
    });
  }

  loadMyPhotos(): void {
    this.eventService.getPhotosByUploader(this.eventCode, this.uploaderName).subscribe({
      next: (data) => {
        this.photos = data;
      },
      error: () => {
        this.errorMessage = 'Failed to load photos';
      }
    });
  }

  downloadPhoto(photo: any): void {
    const link = document.createElement('a');
    link.href = `http://localhost:8080${photo.fileUrl}`;
    link.download = photo.fileName;
    link.click();
  }

  changeName(): void {
    localStorage.removeItem(`guest_name_${this.eventCode}`);
    this.uploaderName = '';
    this.showUploadSection = false;
    this.photos = [];
    this.nameAvailable = null;
  }
}
