import { Pipe, PipeTransform } from '@angular/core';

const REFRESH_MS = 15_000;

@Pipe({
  name: 'timeAgo',
  pure: false
})
export class TimeAgoPipe implements PipeTransform {
  private cache = new Map<string, { at: number; text: string }>();

  transform(value: string | number | Date | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return '';
    }
    const date = value instanceof Date ? value : new Date(value);
    if (isNaN(date.getTime())) {
      return '';
    }

    const key = String(value);
    const now = Date.now();
    const cached = this.cache.get(key);
    if (cached && now - cached.at < REFRESH_MS) {
      return cached.text;
    }
    const text = this.compute(date, now);
    this.cache.set(key, { at: now, text });
    return text;
  }

  private compute(date: Date, now: number): string {
    const diffMs = Math.max(0, now - date.getTime());
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