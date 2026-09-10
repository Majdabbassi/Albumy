import { Pipe, PipeTransform } from '@angular/core';
import { heroBackground } from '../../utils/hero';

@Pipe({
  name: 'heroBackground'
})
export class HeroBackgroundPipe implements PipeTransform {
  transform(coverUrl: string | null | undefined): string {
    return heroBackground(coverUrl);
  }
}