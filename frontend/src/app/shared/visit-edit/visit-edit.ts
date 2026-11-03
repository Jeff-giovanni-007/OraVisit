import { Component, OnInit, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { apiErrorMessage } from '../../core/services/api-error';
import { EmployeeSummary, STATUS_LABEL, Visit } from '../../core/models/api.models';

@Component({
  selector: 'app-visit-edit',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './visit-edit.html',
  styleUrl: './visit-edit.css',
})
export class VisitEdit implements OnInit {
  visit = input.required<Visit>();
  saved = output<Visit>();
  closed = output<void>();

  statusLabel = STATUS_LABEL;
  form = { purpose: '', badgeNumber: '', hostEmployeeId: null as number | null };
  empName = '';
  empMatricule = '';
  empField = signal<'name' | 'matricule' | null>(null);
  suggestions = signal<EmployeeSummary[]>([]);
  error = signal('');
  saving = signal(false);
  private lookupRequest = 0;

  constructor(private api: ApiService) {}

  ngOnInit() {
    const v = this.visit();
    this.form = { purpose: v.purpose, badgeNumber: v.badgeNumber, hostEmployeeId: v.hostEmployee.id };
    this.empName = v.hostEmployee.fullName;
    this.empMatricule = v.hostEmployee.matricule;
  }

  async onSearchEmp(field: 'name' | 'matricule') {
    this.form.hostEmployeeId = null;
    this.empField.set(field);
    if (field === 'name') this.empMatricule = ''; else this.empName = '';
    const request = ++this.lookupRequest;
    const q = (field === 'name' ? this.empName : this.empMatricule).trim();
    if (!q) { this.suggestions.set([]); return; }
    try {
      const found = await this.api.lookupEmployees(q);
      if (request === this.lookupRequest) this.suggestions.set(found);
    } catch {
      if (request === this.lookupRequest) this.suggestions.set([]);
    }
  }

  pickEmp(e: EmployeeSummary) {
    this.lookupRequest++;
    this.form.hostEmployeeId = e.id;
    this.empName = e.fullName;
    this.empMatricule = e.matricule;
    this.suggestions.set([]);
  }

  async save() {
    this.error.set('');
    if (!this.form.hostEmployeeId) {
      this.error.set('Sélectionnez un employé hôte (recherche)'); return;
    }
    if (!this.form.purpose.trim() || !this.form.badgeNumber.trim()) {
      this.error.set('Motif et badge obligatoires'); return;
    }
    this.saving.set(true);
    try {
      const updated = await this.api.updateVisit(this.visit().id, {
        hostEmployeeId: this.form.hostEmployeeId,
        purpose: this.form.purpose.trim(),
        badgeNumber: this.form.badgeNumber.trim(),
      });
      this.saved.emit(updated);
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Modification de la visite impossible'));
    } finally {
      this.saving.set(false);
    }
  }
}
