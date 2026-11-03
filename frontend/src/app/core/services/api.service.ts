import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuditLog, DashboardResponse, Direction, Employee, EmployeeImportResult, EmployeeSummary,
  IdentityDocumentType, PageResponse, Role, Visit, Visitor
} from '../models/api.models';

export type QueryParams = Record<string, string | number | boolean | null | undefined>;

export interface NewVisitor {
  firstName: string;
  lastName: string;
  phone: string;
  email: string | null;
  identityDocumentType: IdentityDocumentType;
  identityCardNumber: string;
}

export interface NewEmployee {
  matricule: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  role: Role;
  direction: Direction;
  poste: string;
}

export type EmployeeChanges = Omit<NewEmployee, 'role'>;

export interface VisitChanges {
  hostEmployeeId: number;
  purpose: string;
  badgeNumber: string;
}

export interface NewVisit extends VisitChanges {
  visitorId: number;
}

export interface DownloadedFile {
  blob: Blob;
  filename: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getDashboard() {
    return firstValueFrom(this.http.get<DashboardResponse>(`${this.base}/dashboard`));
  }

  getVisits(params: QueryParams) {
    return firstValueFrom(
      this.http.get<PageResponse<Visit>>(`${this.base}/visits`, { params: toHttpParams(params) })
    );
  }

  exportVisits(params: QueryParams) {
    return this.download(`${this.base}/visits/export`, params, 'visites.xlsx');
  }

  createVisit(body: NewVisit, identityDocument: File | null) {
    const form = new FormData();
    form.append('visit', new Blob([JSON.stringify(body)], { type: 'application/json' }));
    if (identityDocument) form.append('identityDocument', identityDocument);
    return firstValueFrom(this.http.post<Visit>(`${this.base}/visits`, form));
  }

  updateVisit(id: number, body: VisitChanges) {
    return firstValueFrom(this.http.put<Visit>(`${this.base}/visits/${id}`, body));
  }

  checkout(id: number) {
    return firstValueFrom(this.http.patch<Visit>(`${this.base}/visits/${id}/checkout`, {}));
  }

  cancelVisit(id: number, reason: string) {
    return firstValueFrom(this.http.patch<Visit>(`${this.base}/visits/${id}/cancel`, { reason }));
  }

  searchVisitors(params: QueryParams) {
    return firstValueFrom(
      this.http.get<PageResponse<Visitor>>(`${this.base}/visitors`, { params: toHttpParams(params) })
    );
  }

  lookupVisitors(params: QueryParams) {
    return firstValueFrom(
      this.http.get<Visitor[]>(`${this.base}/visitors/lookup`, { params: toHttpParams(params) })
    );
  }

  createVisitor(body: NewVisitor) {
    return firstValueFrom(this.http.post<Visitor>(`${this.base}/visitors`, body));
  }

  updateVisitor(id: number, body: NewVisitor) {
    return firstValueFrom(this.http.put<Visitor>(`${this.base}/visitors/${id}`, body));
  }

  uploadIdentityDocument(visitorId: number, file: File) {
    const form = new FormData();
    form.append('file', file);
    return firstValueFrom(this.http.put<void>(`${this.base}/visitors/${visitorId}/identity-document`, form));
  }

  getIdentityDocument(visitorId: number) {
    return firstValueFrom(
      this.http.get(`${this.base}/visitors/${visitorId}/identity-document`, { responseType: 'blob' })
    );
  }

  lookupEmployees(q: string) {
    return firstValueFrom(
      this.http.get<EmployeeSummary[]>(`${this.base}/employees/lookup`, { params: toHttpParams({ q }) })
    );
  }

  searchEmployees(params: QueryParams) {
    return firstValueFrom(
      this.http.get<PageResponse<Employee>>(`${this.base}/employees`, { params: toHttpParams(params) })
    );
  }

  exportEmployees(params: QueryParams) {
    return this.download(`${this.base}/employees/export`, params, 'employes.xlsx');
  }

  createEmployee(body: NewEmployee) {
    return firstValueFrom(this.http.post<Employee>(`${this.base}/employees`, body));
  }

  updateEmployee(id: number, body: EmployeeChanges) {
    return firstValueFrom(this.http.put<Employee>(`${this.base}/employees/${id}`, body));
  }

  importEmployees(file: File) {
    const form = new FormData();
    form.append('file', file);
    return firstValueFrom(this.http.post<EmployeeImportResult>(`${this.base}/employees/import`, form));
  }

  changeEmployeeRole(id: number, role: Role) {
    return firstValueFrom(this.http.patch<Employee>(`${this.base}/employees/${id}/role`, { role }));
  }

  sendPasswordSetupLink(id: number) {
    return firstValueFrom(this.http.post<void>(`${this.base}/employees/${id}/password-setup-link`, {}));
  }

  getAuditLogs(params: QueryParams) {
    return firstValueFrom(
      this.http.get<PageResponse<AuditLog>>(`${this.base}/audit`, { params: toHttpParams(params) })
    );
  }

  exportAuditLogs(params: QueryParams) {
    return this.download(`${this.base}/audit/export`, params, 'audit.xlsx');
  }

  deleteEmployee(id: number) {
    return firstValueFrom(this.http.delete<void>(`${this.base}/employees/${id}`));
  }

  deleteVisit(id: number) {
    return firstValueFrom(this.http.delete<void>(`${this.base}/visits/${id}`));
  }

  deleteVisitor(id: number) {
    return firstValueFrom(this.http.delete<void>(`${this.base}/visitors/${id}`));
  }

  private async download(url: string, params: QueryParams, fallbackName: string): Promise<DownloadedFile> {
    try {
      const response = await firstValueFrom(
        this.http.get(url, { params: toHttpParams(params), responseType: 'blob', observe: 'response' })
      );
      const filename = filenameOf(response.headers.get('Content-Disposition')) ?? fallbackName;
      return { blob: response.body!, filename };
    } catch (e) {
      throw await withJsonErrorBody(e);
    }
  }
}

function filenameOf(contentDisposition: string | null): string | null {
  return contentDisposition?.match(/filename="?([^";]+)"?/)?.[1] ?? null;
}

async function withJsonErrorBody(error: unknown): Promise<unknown> {
  if (!(error instanceof HttpErrorResponse) || !(error.error instanceof Blob)) return error;
  try {
    return new HttpErrorResponse({
      error: JSON.parse(await error.error.text()),
      headers: error.headers,
      status: error.status,
      statusText: error.statusText,
      url: error.url ?? undefined,
    });
  } catch {
    return error;
  }
}

function toHttpParams(values: QueryParams): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
