import { Pipe, PipeTransform } from '@angular/core';
import { formatTime } from '../../utils/format';

@Pipe({
  name: 'formatTime'
})
export class FormatTimePipe implements PipeTransform {
  transform(time: string | null | undefined): string {
    return formatTime(time || '');
  }
}