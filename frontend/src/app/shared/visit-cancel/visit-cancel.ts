import { Component, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { apiErrorMessage } from '../../core/services/api-error';
import { Visit } from '../../core/models/api.models';

@Component({
  selector: 'app-visit-cancel',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './visit-cancel.html',
  styleUrl: './visit-cancel.css',
})
export class VisitCancel {
  visit = input.required<Visit>();
  cancelled = output<Visit>();
  closed = output<void>();

  reason = '';
  error = signal('');
  saving = signal(false);

  constructor(private api: ApiService) {}

  async confirm() {
    this.error.set('');
    const reason = this.reason.trim();
    if (!reason) {
      this.error.set("Le motif de l'annulation est obligatoire");
      return;
    }
    this.saving.set(true);
    try {
      this.cancelled.emit(await this.api.cancelVisit(this.visit().id, reason));
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Annulation de la visite impossible'));
    } finally {
      this.saving.set(false);
    }
  }
}
