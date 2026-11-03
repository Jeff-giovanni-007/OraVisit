import { Component, ElementRef, OnDestroy, OnInit, afterRenderEffect, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { apiErrorMessage } from '../../core/services/api-error';
import { AuthService } from '../../core/services/auth.service';
import { IDENTITY_DOCUMENT_LABELS, PageResponse, Visitor } from '../../core/models/api.models';
import { ConfirmService } from '../../shared/confirm-dialog/confirm.service';
import { IdentityDocument } from '../../shared/identity-document/identity-document';
import { Pagination } from '../../shared/pagination/pagination';
import { spaceColumns } from '../../shared/space-columns';
import { VisitorEdit } from '../../shared/visitor-edit/visitor-edit';

/** Nom, Téléphone, Email, Pièce and Visites; the last two columns hold the buttons. */
const SPACED_COLUMNS = 5;

@Component({
  selector: 'app-visiteurs',
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination, IdentityDocument, VisitorEdit],
  templateUrl: './visiteurs.html',
  styleUrl: './visiteurs.css',
})
export class VisiteursPage implements OnInit, OnDestroy {
  visitors = signal<PageResponse<Visitor> | null>(null);
  selected = signal<Visitor | null>(null);
  editing = signal<Visitor | null>(null);
  q = '';
  page = 0;
  size = 20;
  loading = signal(false);
  error = signal('');
  message = signal('');
  deletingId = signal<number | null>(null);
  documentLabel = IDENTITY_DOCUMENT_LABELS;
  private searchTimer?: ReturnType<typeof setTimeout>;
  private lastRequest = 0;
  private table = viewChild.required<ElementRef<HTMLTableElement>>('table');
  private wrapWidth = 0;
  private resizeObserver = new ResizeObserver(([entry]) => {
    if (entry.contentRect.width === this.wrapWidth) return;
    this.wrapWidth = entry.contentRect.width;
    spaceColumns(this.table().nativeElement, SPACED_COLUMNS);
  });

  constructor(private api: ApiService, public auth: AuthService, private confirm: ConfirmService) {
    afterRenderEffect(() => {
      this.visitors();
      this.deletingId();
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
      const page = await this.api.searchVisitors({
        q: this.q.trim(), page: this.page, size: this.size, sort: 'visitCount,desc',
      });
      if (request !== this.lastRequest) return;
      if (!page.content.length && this.page > 0 && page.totalPages > 0) {
        this.page = page.totalPages - 1;
        return await this.load();
      }
      this.visitors.set(page);
    } catch (e) {
      if (request !== this.lastRequest) return;
      this.error.set(apiErrorMessage(e, 'Erreur de chargement des visiteurs'));
      this.visitors.set(null);
    } finally {
      if (request === this.lastRequest) this.loading.set(false);
    }
  }

  onSearchInput() {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.search(), 300);
  }

  search() {
    clearTimeout(this.searchTimer);
    this.page = 0;
    this.load();
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

  async onVisitorSaved(updated: Visitor) {
    this.editing.set(null);
    this.error.set('');
    this.message.set(`Visiteur ${updated.fullName} modifié.`);
    await this.load();
  }

  async deleteVisitor(v: Visitor) {
    const name = v.fullName;
    const card = v.hasIdentityDocument ? " et sa pièce d'identité" : '';
    const confirmed = await this.confirm.ask({
      title: 'Supprimer le visiteur',
      message: `Supprimer définitivement le visiteur ${name} ?`,
      details: [`Ses visites (${v.visitCount})${card} seront aussi supprimées.`],
      confirmLabel: 'Supprimer',
      irreversible: true,
    });
    if (!confirmed) return;

    this.deletingId.set(v.id);
    this.error.set('');
    this.message.set('');
    try {
      await this.api.deleteVisitor(v.id);
      this.message.set(`Visiteur ${name} supprimé.`);
      await this.load();
    } catch (e) {
      this.error.set(apiErrorMessage(e, 'Suppression du visiteur impossible'));
    } finally {
      this.deletingId.set(null);
    }
  }

  onIdentityUploaded(visitor: Visitor) {
    visitor.hasIdentityDocument = true;
  }
}
