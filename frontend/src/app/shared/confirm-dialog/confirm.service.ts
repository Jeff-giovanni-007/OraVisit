import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  title: string;
  message: string;
  details?: string[];
  confirmLabel: string;
  tone?: 'danger' | 'primary';
  irreversible?: boolean;
}

interface ConfirmRequest extends ConfirmOptions {
  resolve: (confirmed: boolean) => void;
}

/** Demande de confirmation affichée dans une fenêtre modale (remplace window.confirm). */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly request = signal<ConfirmRequest | null>(null);

  ask(options: ConfirmOptions): Promise<boolean> {
    this.request()?.resolve(false);
    return new Promise(resolve => this.request.set({ ...options, resolve }));
  }

  answer(confirmed: boolean) {
    const request = this.request();
    this.request.set(null);
    request?.resolve(confirmed);
  }
}
