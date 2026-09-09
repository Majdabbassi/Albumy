import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../icon/icon.component';
import { fileUrl } from '../../config/api.config';

@Component({
  selector: 'app-lightbox',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './lightbox.component.html',
  styleUrls: ['./lightbox.component.css']
})
export class LightboxComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input() photos: any[] = [];
  @Input() set index(value: number) {
    this._index = value;
  }
  get index(): number {
    return this._index;
  }
  @Output() indexChange = new EventEmitter<number>();
  @Output() close = new EventEmitter<void>();

  @ViewChild('closeBtn') closeBtn?: ElementRef<HTMLButtonElement>;

  private _index = -1;
  private touchX = 0;
  private touchActive = false;

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (this._index < 0) {
      return;
    }
    if (event.key === 'Escape') {
      this.close.emit();
    } else if (event.key === 'ArrowRight') {
      this.step(1);
    } else if (event.key === 'ArrowLeft') {
      this.step(-1);
    }
  }

  ngOnInit(): void {
    document.body.style.overflow = 'hidden';
  }

  ngAfterViewInit(): void {
    this.closeBtn?.nativeElement.focus();
  }

  ngOnDestroy(): void {
    document.body.style.overflow = '';
  }

  step(direction: number): void {
    const next = this._index + direction;
    if (next >= 0 && next < this.photos.length) {
      this._index = next;
      this.indexChange.emit(next);
    }
  }

  onTouchStart(event: TouchEvent): void {
    this.touchX = event.touches[0].clientX;
    this.touchActive = true;
  }

  onTouchEnd(event: TouchEvent): void {
    if (!this.touchActive) {
      return;
    }
    this.touchActive = false;
    const dx = event.changedTouches[0].clientX - this.touchX;
    if (Math.abs(dx) > 48) {
      this.step(dx < 0 ? 1 : -1);
    }
  }

  onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.close.emit();
    }
  }

  download(photo: any): void {
    const link = document.createElement('a');
    link.href = this.photoSrc(photo, 'full');
    link.download = photo.fileName || 'photo';
    link.rel = 'noopener';
    link.click();
  }

  photoSrc(photo: any, variant?: 'thumb' | 'med' | 'full'): string {
    if (photo.video || photo.isVideo || /\.(mp4|mov|webm|m4v|3gp)$/i.test(photo.fileName || photo.fileUrl || '')) {
      return fileUrl(
        variant === 'thumb'
          ? photo.posterUrl || photo.webUrl || photo.fileUrl
          : photo.webUrl || photo.fileUrl
      );
    }
    const url =
      variant === 'thumb' ? photo.thumbUrl :
      variant === 'med' ? photo.medUrl :
      variant === 'full' ? photo.fullUrl :
      photo.fileUrl;
    return fileUrl(url || photo.fileUrl);
  }

  isVideo(photo: any): boolean {
    return (
      photo.video === true ||
      photo.isVideo === true ||
      /\.(mp4|mov|webm|m4v|3gp)$/i.test(photo.fileName || photo.fileUrl || '')
    );
  }

  uploaderName(photo: any): string {
    return photo?.uploaderName || photo?.displayName || 'Guest';
  }
}