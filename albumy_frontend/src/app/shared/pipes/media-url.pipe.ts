import { Pipe, PipeTransform } from '@angular/core';
import { photoSrc } from '../../utils/media';

@Pipe({
  name: 'mediaUrl'
})
export class MediaUrlPipe implements PipeTransform {
  transform(photo: any, variant?: 'thumb' | 'med' | 'full'): string {
    return photoSrc(photo, variant);
  }
}