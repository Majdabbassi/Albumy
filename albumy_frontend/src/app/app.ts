import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { DeepLinkService } from './services/deep-link.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  protected readonly title = signal('albumy_frontend');

  private deepLink = inject(DeepLinkService);

  ngOnInit(): void {
    this.deepLink.init();
  }
}