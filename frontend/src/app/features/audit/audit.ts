import { Component, ElementRef, OnDestroy, OnInit, afterRenderEffect, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { apiErrorMessage } from '../../core/services/api-error';
import {
  AUDIT_ACTION_LABELS, AUDIT_ACTIONS, AUDIT_OUTCOME_LABELS, AUDIT_TARGET_LABELS, AUDIT_TARGETS,
  AuditLog, PageResponse, ROLE_LABELS
} from '../../core/models/api.models';
import { Pagination } from '../../shared/pagination/pagination';
import { saveFile } from '../../shared/save-file';
import { spaceColumns } from '../../shared/space-columns';

/** Date/heure to Objet concerné; the last column holds the button. */
const SPACED_COLUMNS = 4;

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination],
  templateUrl: './audit.html',
  styleUrl: './audit.css',
})
export class AuditPage implements OnInit, OnDestroy {
  logs = signal<PageResponse<AuditLog> | null>(null);
  actionLabel = AUDIT_ACTION_LABELS;
  actions = AUDIT_ACTIONS;
  targetLabel = AUDIT_TARGET_LABELS;
  targets = AUDIT_TARGETS;
  outcomeLabel = AUDIT_OUTCOME_LABELS;
  roleLabel = ROLE_LABELS;

  filters = this.emptyFilters();
  page = 0;
  size = 20;
  loading = signal(false);
  exporting = signal(false);
  error = signal('');
  detail = signal<AuditLog | null>(null);
  actor = signal<{ id: number; label: string } | null>(null);
  private searchTimer?: ReturnType<typeof setTimeout>;
  private lastRequest = 0;
  private table = viewChild.required<ElementRef<HTMLTableElement>>('table');
  private wrapWidth = 0;
  private resizeObserver = new ResizeObserver(([entry]) => {
    if (entry.contentRect.width === this.wrapWidth) return;
    this.wrapWidth = entry.contentRect.width;
    spaceColumns(this.table().nativeElement, SPACED_COLUMNS);
  });

  constructor(private api: ApiService) {
    afterRenderEffect(() => {
      this.logs();
      this.resizeObserver.observe(this.table().nativeElement.parentElement!);
      spaceColumns(this.table().nativeElement, SPACED_COLUMNS);
    });
  }

  async ngOnInit() { await this.load(); }

  ngOnDestroy() {
    clearTimeout(this.searchTimer);
    this.resizeObserver.disconnect();
  }

  async load(): Promise<void> {
    const request = ++this.lastRequest;
    this.loading.set(true);
    this.error.set('');
    try {
      const page = await this.api.getAuditLogs({
        ...this.queryFilters(),
        page: this.page,
        size: this.size,
        sort: 'occurredAt,desc',
      });
      if (request !== this.lastRequest) return;
      if (!page.content.length && this.page > 0 && page.totalPages > 0) {
        this.page = page.totalPages - 1;
        return await this.load();
      }
      this.logs.set(page);
    } catch (e) {
      if (request !== this.lastRequest) return;
      this.error.set(apiErrorMessage(e, "Erreur de chargement du journal d'audit"));
      this.logs.set(null);
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
    this.load();
  }

  hasFilters() {
    const f = this.filters;
    return !!(f.q.trim() || f.action || f.targetType || f.dateFrom || f.dateTo || this.actor());
  }

  resetFilters() {
    this.filters = this.emptyFilters();
    this.actor.set(null);
    this.applyFilters();
  }

  goToPage(page: number) {
    this.page = page;
    this.load();
  }

  changePageSize(size: number) {
    this.size = size;
    this.page = 0;
    this.load();
  }

  /** Actions faites par cet auteur (filtre exact actorId) ; sans compte connu (connexion refusée), le matricule saisi. */
  filterByActor(log: AuditLog) {
    if (log.actorId == null) {
      this.actor.set(null);
      this.filters.q = log.actorMatricule;
    } else {
      this.actor.set({ id: log.actorId, label: `${log.actorName ?? 'Inconnu'} (${log.actorMatricule})` });
    }
    this.applyFilters();
  }

  clearActor() {
    this.actor.set(null);
    this.applyFilters();
  }

  async downloadExcel() {
    this.exporting.set(true);
    this.error.set('');
    try {
      saveFile(await this.api.exportAuditLogs(this.queryFilters()));
      await this.load();
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Impossible de télécharger le fichier Excel'));
    } finally {
      this.exporting.set(false);
    }
  }

  private queryFilters() {
    return { ...this.filters, q: this.filters.q.trim(), actorId: this.actor()?.id };
  }

  private emptyFilters() {
    return { q: '', action: '', targetType: '', dateFrom: '', dateTo: '' };
  }
}
