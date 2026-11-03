import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { apiErrorMessage } from '../../core/services/api-error';
import { AuthService } from '../../core/services/auth.service';
import {
  DashboardResponse, DIRECTION_LABELS, DIRECTIONS, EmployeeSummary, IDENTITY_DOCUMENT_LABELS,
  IDENTITY_DOCUMENT_TYPES, IdentityDocumentType, PageResponse, STATUS_LABEL, STATUS_CLASS,
  VISIT_STATUSES, Visit, Visitor
} from '../../core/models/api.models';
import { ConfirmService } from '../../shared/confirm-dialog/confirm.service';
import { Pagination } from '../../shared/pagination/pagination';
import { VisitCancel } from '../../shared/visit-cancel/visit-cancel';
import { VisitEdit } from '../../shared/visit-edit/visit-edit';
import { DurationPipe } from '../../shared/duration.pipe';
import { saveFile } from '../../shared/save-file';
import {
  IDENTITY_FILE_ACCEPT, IdentityDocument, identityFileError
} from '../../shared/identity-document/identity-document';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination, IdentityDocument, VisitEdit, VisitCancel, DurationPipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardPage implements OnInit, OnDestroy {
  visits = signal<PageResponse<Visit> | null>(null);
  stats = signal<DashboardResponse | null>(null);
  erreur = signal('');
  message = signal('');
  loading = signal(false);
  exporting = signal(false);
  modal = signal(false);
  detail = signal<Visit | null>(null);
  editing = signal<Visit | null>(null);
  cancelling = signal<Visit | null>(null);
  statusLabel = STATUS_LABEL;
  statusClass = STATUS_CLASS;
  statuses = VISIT_STATUSES;
  dirLabel = DIRECTION_LABELS;
  directions = DIRECTIONS;

  filters = this.emptyFilters();
  page = 0;
  size = 20;
  private searchTimer?: ReturnType<typeof setTimeout>;
  private lastRequest = 0;
  private editFromDetail = false;

  form = this.emptyForm();
  empName = '';
  empMatricule = '';
  empField = signal<'name' | 'matricule' | null>(null);
  suggestions = signal<EmployeeSummary[]>([]);
  selectedEmp = signal<EmployeeSummary | null>(null);
  formError = signal('');
  saving = signal(false);

  documentLabel = IDENTITY_DOCUMENT_LABELS;
  documentTypes = IDENTITY_DOCUMENT_TYPES;
  visitorQuery = '';
  visitorResults = signal<Visitor[]>([]);
  visitorSearched = signal(false);
  selectedVisitor = signal<Visitor | null>(null);
  existingVisitor = signal<Visitor | null>(null);
  private visitorTimer?: ReturnType<typeof setTimeout>;
  private documentTimer?: ReturnType<typeof setTimeout>;
  private visitorRequest = 0;
  private documentRequest = 0;

  identityAccept = IDENTITY_FILE_ACCEPT;
  identityFile: File | null = null;
  identityPreview = signal<string | null>(null);
  private createdVisitor: { id: number; key: string } | null = null;

  constructor(private api: ApiService, public auth: AuthService, private confirm: ConfirmService) {}

  async ngOnInit() { await this.reload(); }

  ngOnDestroy() {
    clearTimeout(this.searchTimer);
    this.resetVisitorLookup();
    this.setIdentityPreview(null);
  }

  async reload() {
    await Promise.all([this.loadStats(), this.loadVisits()]);
  }

  async loadStats() {
    this.stats.set(await this.api.getDashboard().catch(() => null));
  }

  async loadVisits(): Promise<void> {
    const request = ++this.lastRequest;
    this.loading.set(true);
    this.erreur.set('');
    try {
      const page = await this.api.getVisits({
        ...this.queryFilters(),
        page: this.page, size: this.size, sort: 'checkInAt,desc',
      });
      if (request !== this.lastRequest) return;
      if (!page.content.length && this.page > 0 && page.totalPages > 0) {
        this.page = page.totalPages - 1;
        return this.loadVisits();
      }
      this.visits.set(page);
    } catch (e) {
      if (request === this.lastRequest) this.erreur.set(apiErrorMessage(e, 'Erreur de chargement'));
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
    this.loadVisits();
  }

  hasFilters() {
    const f = this.filters;
    return !!(f.q.trim() || f.status || f.direction || f.dateFrom || f.dateTo);
  }

  resetFilters() {
    this.filters = this.emptyFilters();
    this.applyFilters();
  }

  goToPage(page: number) {
    this.page = page;
    this.loadVisits();
  }

  changePageSize(size: number) {
    this.size = size;
    this.page = 0;
    this.loadVisits();
  }

  async downloadExcel() {
    this.exporting.set(true);
    this.erreur.set('');
    try {
      saveFile(await this.api.exportVisits(this.queryFilters()));
    } catch (e) {
      this.erreur.set(apiErrorMessage(e, 'Impossible de télécharger le fichier Excel'));
    } finally {
      this.exporting.set(false);
    }
  }

  async onSearchEmp(field: 'name' | 'matricule') {
    this.selectedEmp.set(null);
    this.form.hostEmployeeId = null;
    this.empField.set(field);
    if (field === 'name') this.empMatricule = ''; else this.empName = '';
    const q = (field === 'name' ? this.empName : this.empMatricule).trim();
    if (!q) { this.suggestions.set([]); return; }
    try {
      this.suggestions.set(await this.api.lookupEmployees(q));
    } catch { this.suggestions.set([]); }
  }

  pickEmp(e: EmployeeSummary) {
    this.selectedEmp.set(e);
    this.form.hostEmployeeId = e.id;
    this.empName = e.fullName;
    this.empMatricule = e.matricule;
    this.suggestions.set([]);
  }

  openModal() {
    this.formError.set('');
    this.form = this.emptyForm();
    this.empName = '';
    this.empMatricule = '';
    this.empField.set(null);
    this.selectedEmp.set(null);
    this.suggestions.set([]);
    this.resetVisitorLookup();
    this.selectedVisitor.set(null);
    this.createdVisitor = null;
    this.identityFile = null;
    this.setIdentityPreview(null);
    this.modal.set(true);
  }

  closeModal() {
    this.modal.set(false);
    this.resetVisitorLookup();
    this.setIdentityPreview(null);
  }

  onVisitorQuery() {
    clearTimeout(this.visitorTimer);
    this.visitorRequest++;
    const q = this.visitorQuery.trim();
    this.visitorSearched.set(false);
    if (q.length < 2) {
      this.visitorResults.set([]);
      return;
    }
    this.visitorTimer = setTimeout(() => this.searchVisitors(q), 300);
  }

  pickVisitor(visitor: Visitor) {
    this.resetVisitorLookup();
    this.selectedVisitor.set(visitor);
    this.formError.set('');
  }

  changeVisitor() {
    this.selectedVisitor.set(null);
    this.formError.set('');
    this.onDocumentChange();
  }

  onDocumentChange() {
    clearTimeout(this.documentTimer);
    this.documentRequest++;
    this.existingVisitor.set(null);
    const type = this.form.identityDocumentType;
    const number = this.form.identityCardNumber.trim();
    if (!number) return;
    this.documentTimer = setTimeout(() => this.findExistingVisitor(type, number), 400);
  }

  onDocumentTypeChange(type: IdentityDocumentType) {
    this.form.identityDocumentType = type;
    this.onDocumentChange();
  }

  onIdentityFile(event: Event) {
    const fileInput = event.target as HTMLInputElement;
    const file = fileInput.files?.[0] ?? null;
    fileInput.value = '';
    if (!file) return;
    const invalid = identityFileError(file);
    if (invalid) {
      this.formError.set(invalid);
      return;
    }
    this.formError.set('');
    this.identityFile = file;
    this.setIdentityPreview(file.type.startsWith('image/') ? URL.createObjectURL(file) : null);
  }

  removeIdentityFile() {
    this.identityFile = null;
    this.setIdentityPreview(null);
  }

  identityOnFile(): boolean {
    return !!(this.selectedVisitor() ?? this.existingVisitor())?.hasIdentityDocument;
  }

  async createVisit() {
    this.formError.set('');
    const selected = this.selectedVisitor();
    if (!selected) {
      if (!this.form.visitorFirstName.trim() || !this.form.visitorLastName.trim()) {
        this.formError.set('Nom et prénom du visiteur obligatoires'); return;
      }
      if (!this.form.visitorPhone.trim()) {
        this.formError.set('Téléphone obligatoire'); return;
      }
      if (!this.form.identityCardNumber.trim()) {
        this.formError.set('Numéro de pièce obligatoire'); return;
      }
    }
    if (!this.form.hostEmployeeId) {
      this.formError.set('Sélectionnez un employé hôte (recherche)'); return;
    }
    if (!this.form.purpose.trim() || !this.form.badgeNumber.trim()) {
      this.formError.set('Motif et badge obligatoires'); return;
    }
    if (!this.identityFile && !this.identityOnFile()) {
      this.formError.set('Pièce d\'identité numérisée obligatoire'); return;
    }
    this.saving.set(true);
    try {
      const visitorId = selected ? selected.id : await this.saveNewVisitor();
      await this.api.createVisit({
        visitorId,
        hostEmployeeId: this.form.hostEmployeeId,
        purpose: this.form.purpose.trim(),
        badgeNumber: this.form.badgeNumber.trim(),
      }, this.identityFile);
      const visitorName = selected ? selected.fullName
        : `${this.form.visitorFirstName.trim()} ${this.form.visitorLastName.trim()}`;
      this.closeModal();
      this.clearFeedback();
      await this.reload();
      this.message.set(`Visite enregistrée : ${visitorName}.`);
    } catch (e) {
      this.formError.set(apiErrorMessage(e, 'Erreur enregistrement'));
    } finally {
      this.saving.set(false);
    }
  }

  async checkout(v: Visit) {
    this.clearFeedback();
    try {
      await this.api.checkout(v.id);
      this.detail.set(null);
      this.message.set(`Sortie enregistrée : ${v.visitor.fullName}.`);
      await this.reload();
    } catch (e) {
      this.detail.set(null);
      this.erreur.set(apiErrorMessage(e, 'Enregistrement de la sortie impossible'));
    }
  }

  editVisit(v: Visit) {
    this.editFromDetail = this.detail() !== null;
    this.detail.set(null);
    this.editing.set(v);
  }

  closeEdit() {
    if (this.editFromDetail) this.detail.set(this.editing());
    this.editing.set(null);
    this.editFromDetail = false;
  }

  async onVisitSaved(updated: Visit) {
    this.editing.set(null);
    if (this.editFromDetail) this.detail.set(updated);
    this.editFromDetail = false;
    this.clearFeedback();
    this.message.set(`Visite modifiée : ${updated.visitor.fullName}.`);
    await this.reload();
  }

  cancelVisit(v: Visit) {
    this.detail.set(null);
    this.cancelling.set(v);
  }

  closeCancel() {
    this.cancelling.set(null);
  }

  async onVisitCancelled(updated: Visit) {
    this.cancelling.set(null);
    this.clearFeedback();
    this.message.set(`Visite annulée : ${updated.visitor.fullName}.`);
    await this.reload();
  }

  async deleteVisit(v: Visit) {
    const who = v.visitor.fullName;
    const confirmed = await this.confirm.ask({
      title: 'Supprimer la visite',
      message: `Supprimer définitivement la visite de ${who} (badge ${v.badgeNumber}) ?`,
      confirmLabel: 'Supprimer',
      irreversible: true,
    });
    if (!confirmed) return;
    this.clearFeedback();
    try {
      await this.api.deleteVisit(v.id);
      this.detail.set(null);
      this.message.set(`Visite supprimée : ${who}.`);
      await this.reload();
    } catch (e) {
      this.detail.set(null);
      this.erreur.set(apiErrorMessage(e, 'Suppression de la visite impossible'));
    }
  }

  private clearFeedback() {
    this.message.set('');
    this.erreur.set('');
  }

  private queryFilters() {
    return { ...this.filters, q: this.filters.q.trim() };
  }

  private emptyFilters() {
    return { q: '', status: '', direction: '', dateFrom: '', dateTo: '' };
  }

  onIdentityUploaded(v: Visit) {
    v.visitor.hasIdentityDocument = true;
  }

  private async saveNewVisitor(): Promise<number> {
    const visitor = {
      firstName: this.form.visitorFirstName.trim(),
      lastName: this.form.visitorLastName.trim(),
      phone: this.form.visitorPhone.trim(),
      email: this.form.visitorEmail.trim() || null,
      identityDocumentType: this.form.identityDocumentType,
      identityCardNumber: this.form.identityCardNumber.trim(),
    };
    const key = JSON.stringify(visitor);
    if (this.createdVisitor?.key !== key) {
      const created = await this.api.createVisitor(visitor);
      this.createdVisitor = { id: created.id, key };
    }
    return this.createdVisitor.id;
  }

  private async searchVisitors(q: string) {
    const request = ++this.visitorRequest;
    try {
      const results = await this.api.lookupVisitors({ q });
      if (request !== this.visitorRequest) return;
      this.visitorResults.set(results);
    } catch {
      if (request === this.visitorRequest) this.visitorResults.set([]);
    } finally {
      if (request === this.visitorRequest) this.visitorSearched.set(true);
    }
  }

  private async findExistingVisitor(type: IdentityDocumentType, number: string) {
    const request = ++this.documentRequest;
    try {
      const results = await this.api.lookupVisitors({ identityDocumentType: type, identityCardNumber: number });
      if (request === this.documentRequest) this.existingVisitor.set(results[0] ?? null);
    } catch {
      if (request === this.documentRequest) this.existingVisitor.set(null);
    }
  }

  private resetVisitorLookup() {
    clearTimeout(this.visitorTimer);
    clearTimeout(this.documentTimer);
    this.visitorRequest++;
    this.documentRequest++;
    this.visitorQuery = '';
    this.visitorResults.set([]);
    this.visitorSearched.set(false);
    this.existingVisitor.set(null);
  }

  private setIdentityPreview(url: string | null) {
    const previous = this.identityPreview();
    if (previous) URL.revokeObjectURL(previous);
    this.identityPreview.set(url);
  }

  private emptyForm() {
    return {
      visitorFirstName: '',
      visitorLastName: '',
      visitorPhone: '',
      visitorEmail: '',
      identityDocumentType: 'CNI' as IdentityDocumentType,
      identityCardNumber: '',
      purpose: '',
      badgeNumber: '',
      hostEmployeeId: null as number | null,
    };
  }
}
