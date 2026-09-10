import { Pipe, PipeTransform } from '@angular/core';
import { isVideo } from '../../utils/media';

@Pipe({
  name: 'isVideo'
})
export class IsVideoPipe implements PipeTransform {
  transform(photo: any): boolean {
    return isVideo(photo);
  }
}