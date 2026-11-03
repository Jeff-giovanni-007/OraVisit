import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmDialog } from './shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ConfirmDialog],
  template: `<router-outlet /><app-confirm-dialog />`,
  styles: [':host { display: block; min-height: 100vh; }'],
})
export class App {}
