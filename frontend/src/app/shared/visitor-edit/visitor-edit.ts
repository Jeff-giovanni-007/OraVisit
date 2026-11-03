import { Component, OnInit, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { apiErrorMessage } from '../../core/services/api-error';
import {
  IDENTITY_DOCUMENT_LABELS, IDENTITY_DOCUMENT_TYPES, IdentityDocumentType, Visitor
} from '../../core/models/api.models';

@Component({
  selector: 'app-visitor-edit',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './visitor-edit.html',
})
export class VisitorEdit implements OnInit {
  visitor = input.required<Visitor>();
  saved = output<Visitor>();
  closed = output<void>();

  documentLabel = IDENTITY_DOCUMENT_LABELS;
  documentTypes = IDENTITY_DOCUMENT_TYPES;
  form = {
    lastName: '', firstName: '', phone: '', email: '',
    identityDocumentType: 'CNI' as IdentityDocumentType, identityCardNumber: '',
  };
  error = signal('');
  saving = signal(false);

  constructor(private api: ApiService) {}

  ngOnInit() {
    const v = this.visitor();
    this.form = {
      lastName: v.lastName,
      firstName: v.firstName,
      phone: v.phone,
      email: v.email ?? '',
      identityDocumentType: v.identityDocumentType,
      identityCardNumber: v.identityCardNumber,
    };
  }

  async save() {
    this.error.set('');
    if (!this.form.lastName.trim() || !this.form.firstName.trim()) {
      this.error.set('Nom et prénom obligatoires'); return;
    }
    if (!this.form.phone.trim()) {
      this.error.set('Téléphone obligatoire'); return;
    }
    if (!this.form.identityCardNumber.trim()) {
      this.error.set('Numéro de pièce obligatoire'); return;
    }
    this.saving.set(true);
    try {
      const updated = await this.api.updateVisitor(this.visitor().id, {
        firstName: this.form.firstName.trim(),
        lastName: this.form.lastName.trim(),
        phone: this.form.phone.trim(),
        email: this.form.email.trim() || null,
        identityDocumentType: this.form.identityDocumentType,
        identityCardNumber: this.form.identityCardNumber.trim(),
      });
      this.saved.emit(updated);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Modification du visiteur impossible'));
    } finally {
      this.saving.set(false);
    }
  }
}
