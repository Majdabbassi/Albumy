import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'timeAgo',
  pure: false
})
export class TimeAgoPipe implements PipeTransform {
  transform(value: string | number | Date | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return '';
    }
    const date = value instanceof Date ? value : new Date(value);
    if (isNaN(date.getTime())) {
      return '';
    }

    const diffMs = Math.max(0, Date.now() - date.getTime());
    const sec = Math.floor(diffMs / 1000);

    if (sec < 10) {
      return 'just now';
    }
    if (sec < 60) {
      return `${sec} sec ago`;
    }

    const min = Math.floor(sec / 60);
    if (min < 60) {
      return `${min} min ago`;
    }

    const hr = Math.floor(min / 60);
    if (hr < 24) {
      return `${hr} hr ago`;
    }

    return (
      date.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' }) +
      ' ' +
      date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
    );
  }
}