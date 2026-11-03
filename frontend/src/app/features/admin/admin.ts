import { Component, ElementRef, OnDestroy, OnInit, afterRenderEffect, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { apiErrorMessage } from '../../core/services/api-error';
import { AuthService } from '../../core/services/auth.service';
import {
  DIRECTION_LABELS, DIRECTIONS, Direction, Employee, EmployeeImportResult, PageResponse, ROLE_LABELS, Role
} from '../../core/models/api.models';
import { ConfirmService } from '../../shared/confirm-dialog/confirm.service';
import { EmployeeEdit } from '../../shared/employee-edit/employee-edit';
import { Pagination } from '../../shared/pagination/pagination';
import { saveFile } from '../../shared/save-file';
import { spaceColumns } from '../../shared/space-columns';

/** Matricule to Mot de passe; the last column holds the buttons. */
const SPACED_COLUMNS = 6;

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination, EmployeeEdit],
  templateUrl: './admin.html',
  styleUrl: './admin.css',
})
export class AdminPage implements OnDestroy, OnInit {
  employees = signal<PageResponse<Employee> | null>(null);
  message = signal('');
  error = signal('');
  importResult = signal<EmployeeImportResult | null>(null);
  dirLabels = DIRECTION_LABELS;
  roleLabels = ROLE_LABELS;
  roles: Role[] = ['DEFAULT', 'RECEPTION', 'ADMIN'];
  directions = DIRECTIONS;

  filters = { q: '', role: '', direction: '' };
  page = 0;
  size = 20;
  loading = signal(false);
  exporting = signal(false);
  importing = signal(false);
  busyId = signal<number | null>(null);
  editing = signal<Employee | null>(null);

  form = {
    matricule: '',
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    role: 'DEFAULT' as Role,
    direction: '' as Direction | '',
    poste: '',
  };
  saving = signal(false);

  private table = viewChild.required<ElementRef<HTMLTableElement>>('table');
  private wrapWidth = 0;
  private resizeObserver = new ResizeObserver(([entry]) => {
    if (entry.contentRect.width === this.wrapWidth) return;
    this.wrapWidth = entry.contentRect.width;
    spaceColumns(this.table().nativeElement, SPACED_COLUMNS);
  });

  constructor(private api: ApiService, public auth: AuthService, private confirm: ConfirmService) {
    afterRenderEffect(() => {
      this.employees();
      this.resizeObserver.observe(this.table().nativeElement.parentElement!);
      spaceColumns(this.table().nativeElement, SPACED_COLUMNS);
    });
  }

  private searchTimer?: ReturnType<typeof setTimeout>;
  private lastRequest = 0;

  async ngOnInit() { await this.reload(); }

  ngOnDestroy() {
    clearTimeout(this.searchTimer);
    this.resizeObserver.disconnect();
  }

  async reload(): Promise<void> {
    const request = ++this.lastRequest;
    this.loading.set(true);
    try {
      const page = await this.api.searchEmployees({
        ...this.filters, q: this.filters.q.trim(), page: this.page, size: this.size,
      });
      if (request !== this.lastRequest) return;
      if (!page.content.length && this.page > 0 && page.totalPages > 0) {
        this.page = page.totalPages - 1;
        return this.reload();
      }
      this.employees.set(page);
    } catch (e) {
      if (request === this.lastRequest) this.error.set(apiErrorMessage(e, 'Erreur chargement employés'));
    } finally {
      if (request === this.lastRequest) this.loading.set(false);
    }
  }

  onSearchInput() {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.applyFilters(), 300);
  }

  applyFilters() {
    clearTimeout(this.searchTimer);
    this.page = 0;
    this.reload();
  }

  goToPage(page: number) {
    this.page = page;
    this.reload();
  }

  changePageSize(size: number) {
    this.size = size;
    this.page = 0;
    this.reload();
  }

  async downloadExcel() {
    this.clearFeedback();
    this.exporting.set(true);
    try {
      saveFile(await this.api.exportEmployees({ ...this.filters, q: this.filters.q.trim() }));
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Impossible de télécharger le fichier Excel'));
    } finally {
      this.exporting.set(false);
    }
  }

  isSelf(e: Employee) {
    return this.auth.currentUser()?.id === e.id;
  }

  async addOne() {
    this.clearFeedback();
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
    const direction = this.form.direction;
    if (!direction) {
      this.error.set('Direction obligatoire'); return;
    }
    if (!this.form.poste.trim()) {
      this.error.set('Poste obligatoire'); return;
    }
    this.saving.set(true);
    try {
      const created = await this.api.createEmployee({
        matricule: this.form.matricule.trim(),
        firstName: this.form.firstName.trim(),
        lastName: this.form.lastName.trim(),
        email: this.form.email.trim(),
        phone: this.form.phone.trim(),
        role: this.form.role,
        direction,
        poste: this.form.poste.trim(),
      });
      this.message.set(created.role === 'DEFAULT'
        ? `Employé ${created.fullName} créé.`
        : `Compte ${created.fullName} créé. Un e-mail contenant un lien pour définir son mot de passe a été envoyé à ${created.email}.`);
      this.form.matricule = ''; this.form.firstName = ''; this.form.lastName = '';
      this.form.email = ''; this.form.phone = ''; this.form.poste = '';
      await this.reload();
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erreur création'));
    } finally {
      this.saving.set(false);
    }
  }

  async onFile(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.clearFeedback();
    this.importResult.set(null);
    this.importing.set(true);
    try {
      const res = await this.api.importEmployees(file);
      this.importResult.set(res);
      this.message.set(`Import terminé : ${res.created} créé(s), ${res.skipped} ignoré(s), ${res.failed} échec(s). `
        + 'Les agents d\'accueil et administrateurs créés reçoivent un lien par e-mail pour définir leur mot de passe.');
      this.applyFilters();
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Erreur import Excel'));
    } finally {
      this.importing.set(false);
    }
    input.value = '';
  }

  async changeRole(e: Employee, event: Event) {
    const select = event.target as HTMLSelectElement;
    const role = select.value as Role;
    const name = e.fullName;
    const grantsAccess = e.role === 'DEFAULT' && role !== 'DEFAULT';
    const removesAccess = e.role !== 'DEFAULT' && role === 'DEFAULT';

    const confirmed = await this.confirm.ask(removesAccess
      ? {
        title: "Retirer l'accès",
        message: `Retirer l'accès à l'application de ${name} ?`,
        details: ['Son mot de passe sera supprimé.'],
        confirmLabel: "Retirer l'accès",
      }
      : {
        title: 'Modifier le rôle',
        message: `Attribuer le rôle « ${this.roleLabels[role]} » à ${name} ?`,
        details: grantsAccess
          ? [`Un e-mail sera envoyé à ${e.email} avec un lien pour définir son mot de passe.`]
          : [],
        confirmLabel: 'Attribuer le rôle',
        tone: 'primary',
      });
    if (!confirmed) {
      select.value = e.role;
      return;
    }

    this.clearFeedback();
    this.busyId.set(e.id);
    try {
      const updated = await this.api.changeEmployeeRole(e.id, role);
      this.message.set(grantsAccess
        ? `Rôle « ${this.roleLabels[role]} » attribué à ${name}. Un lien pour définir son mot de passe a été envoyé à ${updated.email}.`
        : `Rôle de ${name} mis à jour.`);
      await this.reload();
    } catch (err) {
      select.value = e.role;
      this.error.set(apiErrorMessage(err, 'Modification du rôle impossible'));
    } finally {
      this.busyId.set(null);
    }
  }

  async sendLink(e: Employee) {
    this.clearFeedback();
    this.busyId.set(e.id);
    try {
      await this.api.sendPasswordSetupLink(e.id);
      this.message.set(e.passwordSet
        ? `Lien de réinitialisation du mot de passe envoyé à ${e.email}.`
        : `Lien de définition du mot de passe envoyé à ${e.email}.`);
    } catch (err) {
      this.error.set(apiErrorMessage(err, 'Envoi du lien impossible'));
    } finally {
      this.busyId.set(null);
    }
  }

  async onEmployeeSaved(updated: Employee) {
    const before = this.editing();
    this.editing.set(null);
    this.clearFeedback();
    const name = updated.fullName;
    const identityChanged = !!before && (
      before.matricule.toLowerCase() !== updated.matricule.toLowerCase()
      || before.email.toLowerCase() !== updated.email.toLowerCase());
    this.message.set(identityChanged && updated.role !== 'DEFAULT' && !updated.passwordSet
      ? `Employé ${name} modifié. Un nouveau lien pour définir son mot de passe a été envoyé à ${updated.email}.`
      : `Employé ${name} modifié.`);
    if (this.isSelf(updated)) {
      this.auth.updateCurrentUser({ firstName: updated.firstName, lastName: updated.lastName });
    }
    await this.reload();
  }

  async deleteEmployee(e: Employee) {
    const name = e.fullName;
    const confirmed = await this.confirm.ask({
      title: "Supprimer l'employé",
      message: `Supprimer définitivement l'employé ${name} (${e.matricule}) ?`,
      details: [`${e.poste} · ${this.dirLabels[e.direction]}`],
      confirmLabel: 'Supprimer',
      irreversible: true,
    });
    if (!confirmed) return;

    this.clearFeedback();
    this.busyId.set(e.id);
    try {
      await this.api.deleteEmployee(e.id);
      this.message.set(`Employé ${name} supprimé.`);
      await this.reload();
    } catch (err) {
      this.error.set(apiErrorMessage(err, 'Suppression impossible'));
    } finally {
      this.busyId.set(null);
    }
  }

  private clearFeedback() {
    this.message.set('');
    this.error.set('');
  }
}
