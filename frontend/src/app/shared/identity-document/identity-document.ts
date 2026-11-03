import { Component, OnDestroy, OnInit, input, output, signal } from '@angular/core';
import { ApiService } from '../../core/services/api.service';
import { apiErrorMessage } from '../../core/services/api-error';

export const IDENTITY_FILE_ACCEPT = 'image/jpeg,image/png,image/webp,application/pdf';
const MAX_IDENTITY_FILE_SIZE = 5 * 1024 * 1024;

export function identityFileError(file: File): string | null {
  if (!IDENTITY_FILE_ACCEPT.split(',').includes(file.type)) {
    return 'Formats acceptés pour la pièce d\'identité : JPEG, PNG, WEBP ou PDF.';
  }
  if (file.size > MAX_IDENTITY_FILE_SIZE) {
    return 'La pièce d\'identité dépasse la taille maximale de 5 Mo.';
  }
  return null;
}

@Component({
  selector: 'app-identity-document',
  standalone: true,
  templateUrl: './identity-document.html',
  styleUrl: './identity-document.css',
})
export class IdentityDocument implements OnInit, OnDestroy {
  visitorId = input.required<number>();
  hasDocument = input(false);
  uploaded = output<void>();

  accept = IDENTITY_FILE_ACCEPT;
  url = signal<string | null>(null);
  isPdf = signal(false);
  loading = signal(false);
  uploading = signal(false);
  error = signal('');

  constructor(private api: ApiService) {}

  ngOnInit() {
    if (this.hasDocument()) this.load();
  }

  ngOnDestroy() {
    this.releaseUrl();
  }

  async load() {
    this.loading.set(true);
    this.error.set('');
    try {
      const blob = await this.api.getIdentityDocument(this.visitorId());
      this.releaseUrl();
      this.isPdf.set(blob.type === 'application/pdf');
      this.url.set(URL.createObjectURL(blob));
    } catch {
      this.error.set('Impossible d\'afficher la pièce d\'identité.');
    } finally {
      this.loading.set(false);
    }
  }

  async onFile(event: Event) {
    const fileInput = event.target as HTMLInputElement;
    const file = fileInput.files?.[0];
    fileInput.value = '';
    if (!file) return;

    const invalid = identityFileError(file);
    if (invalid) {
      this.error.set(invalid);
      return;
    }
    this.uploading.set(true);
    this.error.set('');
    try {
      await this.api.uploadIdentityDocument(this.visitorId(), file);
      this.uploaded.emit();
      await this.load();
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Échec de l\'envoi de la pièce d\'identité.'));
    } finally {
      this.uploading.set(false);
    }
  }

  private releaseUrl() {
    const current = this.url();
    if (current) URL.revokeObjectURL(current);
    this.url.set(null);
  }
}
