import { Pipe, PipeTransform } from '@angular/core';
import { formatDate } from '../../utils/format';

@Pipe({
  name: 'formatDate'
})
export class FormatDatePipe implements PipeTransform {
  transform(date: string | null | undefined): string {
    return formatDate(date || '');
  }
}