import { Component, OnInit, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { apiErrorMessage } from '../../core/services/api-error';
import { AuthService } from '../../core/services/auth.service';
import { DIRECTION_LABELS, DIRECTIONS, Direction, Employee, ROLE_LABELS } from '../../core/models/api.models';

@Component({
  selector: 'app-employee-edit',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './employee-edit.html',
})
export class EmployeeEdit implements OnInit {
  employee = input.required<Employee>();
  saved = output<Employee>();
  closed = output<void>();

  dirLabels = DIRECTION_LABELS;
  roleLabels = ROLE_LABELS;
  directions = DIRECTIONS;
  isSelf = computed(() => this.auth.currentUser()?.id === this.employee().id);
  form = { matricule: '', firstName: '', lastName: '', email: '', phone: '', direction: '' as Direction, poste: '' };
  error = signal('');
  saving = signal(false);

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit() {
    const e = this.employee();
    this.form = {
      matricule: e.matricule,
      firstName: e.firstName,
      lastName: e.lastName,
      email: e.email,
      phone: e.phone,
      direction: e.direction,
      poste: e.poste,
    };
  }

  async save() {
    this.error.set('');
    if (!this.form.matricule.trim()) {
      this.error.set('Matricule obligatoire'); return;
    }
    if (!this.form.firstName.trim() || !this.form.lastName.trim()) {
      this.error.set('Nom et prénom obligatoires'); return;
    }
    if (!this.form.email.trim()) {
      this.error.set('Email obligatoire'); return;
    }
    if (!this.form.phone.trim()) {
      this.error.set('Téléphone obligatoire'); return;
    }
    if (!this.form.poste.trim()) {
      this.error.set('Poste obligatoire'); return;
    }
    this.saving.set(true);
    try {
      const updated = await this.api.updateEmployee(this.employee().id, {
        matricule: this.form.matricule.trim(),
        firstName: this.form.firstName.trim(),
        lastName: this.form.lastName.trim(),
        email: this.form.email.trim(),
        phone: this.form.phone.trim(),
        direction: this.form.direction,
        poste: this.form.poste.trim(),
      });
      this.saved.emit(updated);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Modification de l\'employé impossible'));
    } finally {
      this.saving.set(false);
    }
  }
}
