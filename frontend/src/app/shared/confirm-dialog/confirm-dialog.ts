import { Component, ElementRef, HostListener, effect, viewChild } from '@angular/core';
import { ConfirmService } from './confirm.service';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.css',
})
export class ConfirmDialog {
  private cancelButton = viewChild<ElementRef<HTMLButtonElement>>('cancel');
  private returnFocus: HTMLElement | null = null;

  constructor(public confirm: ConfirmService) {
    effect(() => {
      if (this.confirm.request()) {
        this.returnFocus ??= document.activeElement as HTMLElement | null;
        this.cancelButton()?.nativeElement.focus();
      } else {
        this.returnFocus?.focus();
        this.returnFocus = null;
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.confirm.request()) this.confirm.answer(false);
  }
}
