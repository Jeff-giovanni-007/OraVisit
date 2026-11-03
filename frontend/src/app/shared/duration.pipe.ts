import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'duration', standalone: true })
export class DurationPipe implements PipeTransform {
  transform(minutes: number | null | undefined): string {
    if (minutes == null) return '—';
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return `${hours} h ${String(rest).padStart(2, '0')} min`;
  }
}
