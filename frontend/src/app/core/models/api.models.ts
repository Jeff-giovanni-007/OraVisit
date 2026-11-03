export type Role = 'ADMIN' | 'RECEPTION' | 'DEFAULT';
export type VisitStatus = 'EN_COURS' | 'TERMINEE' | 'ANNULEE';
export type IdentityDocumentType = 'CNI' | 'PASSEPORT' | 'PERMIS' | 'CARTE_CONSULAIRE' | 'AUTRE';
export type Direction =
  | 'DIRECTION_GENERALE'
  | 'DIRECTION_GENERALE_ADJOINTE'
  | 'DIRECTION_ADMINISTRATION_FINANCES'
  | 'DIRECTION_TRESORERIE_SALLE_MARCHES'
  | 'DIRECTION_AUDIT_INTERNE'
  | 'DIRECTION_CONFORMITE'
  | 'DIRECTION_OPERATIONS'
  | 'DIRECTION_CAPITAL_HUMAIN'
  | 'DIRECTION_RISQUES'
  | 'DIRECTION_RECOUVREMENT'
  | 'DIRECTION_SYSTEMES_INFORMATION'
  | 'DIRECTION_JURIDIQUE_CONTENTIEUX'
  | 'DIRECTION_CLIENTELE_PARTICULIERS_PROFESSIONNELS'
  | 'DIRECTION_CLIENTELE_INSTITUTIONNELLE'
  | 'DIRECTION_BANQUE_DIGITALE'
  | 'DIRECTION_CLIENTELE_ENTREPRISES';

export interface AuthUser {
  id: number;
  matricule: string;
  firstName: string;
  lastName: string;
  role: Role;
}

export interface LoginResponse {
  accessToken: string;
  user: AuthUser;
}

export interface VisitorSummary {
  id: number;
  fullName: string;
  phone: string;
  identityDocumentType: IdentityDocumentType;
  identityCardNumber: string;
  hasIdentityDocument: boolean;
}

export interface Visitor {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email?: string;
  phone: string;
  identityDocumentType: IdentityDocumentType;
  identityCardNumber: string;
  visitCount: number;
  hasIdentityDocument: boolean;
}

export interface EmployeeSummary {
  id: number;
  matricule: string;
  fullName: string;
  direction: Direction;
  poste: string;
}

export interface Visit {
  id: number;
  visitor: VisitorSummary;
  hostEmployee: EmployeeSummary;
  registeredBy: EmployeeSummary;
  direction: Direction;
  purpose: string;
  badgeNumber: string;
  status: VisitStatus;
  checkInAt: string;
  checkOutAt?: string;
  cancelledAt?: string;
  cancellationReason?: string;
  durationMinutes?: number;
}

export interface Employee {
  id: number;
  matricule: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone: string;
  role: Role;
  direction: Direction;
  poste: string;
  passwordSet: boolean;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DashboardResponse {
  visitsToday: number;
  activeVisits: number;
  completedToday: number;
  cancelledToday: number;
  totalVisitors?: number;
  totalVisits?: number;
  totalEmployees?: number;
}

export interface EmployeeImportResult {
  created: number;
  skipped: number;
  failed: number;
  errors: string[];
}

export interface PasswordSetupInfo {
  matricule: string;
  fullName: string;
  expiresAt: string;
  passwordAlreadySet: boolean;
}

export type AuditAction =
  | 'LOGIN_SUCCEEDED'
  | 'LOGIN_FAILED'
  | 'PASSWORD_SETUP_LINK_ISSUED'
  | 'PASSWORD_DEFINED'
  | 'VISITOR_CREATED'
  | 'VISITOR_UPDATED'
  | 'VISITOR_DELETED'
  | 'IDENTITY_DOCUMENT_STORED'
  | 'IDENTITY_DOCUMENT_VIEWED'
  | 'VISIT_CREATED'
  | 'VISIT_UPDATED'
  | 'VISIT_CHECKED_OUT'
  | 'VISIT_CANCELLED'
  | 'VISIT_DELETED'
  | 'VISITS_EXPORTED'
  | 'EMPLOYEE_CREATED'
  | 'EMPLOYEE_UPDATED'
  | 'EMPLOYEE_ROLE_CHANGED'
  | 'EMPLOYEE_DELETED'
  | 'EMPLOYEES_IMPORTED'
  | 'EMPLOYEES_EXPORTED'
  | 'AUDIT_EXPORTED';

export type AuditTarget = 'SESSION' | 'EMPLOYEE' | 'VISITOR' | 'VISIT' | 'IDENTITY_DOCUMENT' | 'FILE';

export type AuditOutcome = 'SUCCESS' | 'FAILURE';

export interface AuditLog {
  id: number;
  occurredAt: string;
  action: AuditAction;
  outcome: AuditOutcome;
  actorId?: number;
  actorMatricule: string;
  actorName?: string;
  actorRole?: Role;
  targetType: AuditTarget;
  targetId?: number;
  targetLabel?: string;
  details?: string;
  ipAddress?: string;
}

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  LOGIN_SUCCEEDED: 'Connexion',
  LOGIN_FAILED: 'Échec de connexion',
  PASSWORD_SETUP_LINK_ISSUED: 'Lien de mot de passe envoyé',
  PASSWORD_DEFINED: 'Mot de passe défini',
  VISITOR_CREATED: 'Visiteur enregistré',
  VISITOR_UPDATED: 'Visiteur modifié',
  VISITOR_DELETED: 'Visiteur supprimé',
  IDENTITY_DOCUMENT_STORED: "Pièce d'identité enregistrée",
  IDENTITY_DOCUMENT_VIEWED: "Pièce d'identité consultée",
  VISIT_CREATED: 'Visite enregistrée',
  VISIT_UPDATED: 'Visite modifiée',
  VISIT_CHECKED_OUT: 'Sortie enregistrée',
  VISIT_CANCELLED: 'Visite annulée',
  VISIT_DELETED: 'Visite supprimée',
  VISITS_EXPORTED: 'Export des visites',
  EMPLOYEE_CREATED: 'Employé créé',
  EMPLOYEE_UPDATED: 'Employé modifié',
  EMPLOYEE_ROLE_CHANGED: 'Rôle modifié',
  EMPLOYEE_DELETED: 'Employé supprimé',
  EMPLOYEES_IMPORTED: 'Import des employés',
  EMPLOYEES_EXPORTED: 'Export des employés',
  AUDIT_EXPORTED: "Export du journal d'audit",
};

export const AUDIT_ACTIONS = Object.keys(AUDIT_ACTION_LABELS) as AuditAction[];

export const AUDIT_TARGET_LABELS: Record<AuditTarget, string> = {
  SESSION: 'Session',
  EMPLOYEE: 'Employé',
  VISITOR: 'Visiteur',
  VISIT: 'Visite',
  IDENTITY_DOCUMENT: "Pièce d'identité",
  FILE: 'Fichier',
};

export const AUDIT_TARGETS = Object.keys(AUDIT_TARGET_LABELS) as AuditTarget[];

export const AUDIT_OUTCOME_LABELS: Record<AuditOutcome, string> = {
  SUCCESS: 'Réussie',
  FAILURE: 'Refusée',
};

export const DIRECTION_LABELS: Record<Direction, string> = {
  DIRECTION_GENERALE: 'Direction Générale',
  DIRECTION_GENERALE_ADJOINTE: 'Direction Générale Adjointe',
  DIRECTION_ADMINISTRATION_FINANCES: 'Administration et Finances',
  DIRECTION_TRESORERIE_SALLE_MARCHES: 'Trésorerie et Salle des Marchés',
  DIRECTION_AUDIT_INTERNE: 'Audit Interne',
  DIRECTION_CONFORMITE: 'Conformité',
  DIRECTION_OPERATIONS: 'Opérations',
  DIRECTION_CAPITAL_HUMAIN: 'Capital Humain',
  DIRECTION_RISQUES: 'Risques',
  DIRECTION_RECOUVREMENT: 'Recouvrement',
  DIRECTION_SYSTEMES_INFORMATION: "Systèmes d'Information",
  DIRECTION_JURIDIQUE_CONTENTIEUX: 'Juridique et Contentieux',
  DIRECTION_CLIENTELE_PARTICULIERS_PROFESSIONNELS: 'Clientèle Particuliers et Professionnels',
  DIRECTION_CLIENTELE_INSTITUTIONNELLE: 'Clientèle Institutionnelle',
  DIRECTION_BANQUE_DIGITALE: 'Banque Digitale',
  DIRECTION_CLIENTELE_ENTREPRISES: 'Clientèle Entreprises',
};

export const DIRECTIONS = Object.keys(DIRECTION_LABELS) as Direction[];

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  RECEPTION: "Agent d'accueil",
  DEFAULT: 'Employé',
};

export const STATUS_LABEL: Record<VisitStatus, string> = {
  EN_COURS: 'En cours',
  TERMINEE: 'Terminée',
  ANNULEE: 'Annulée',
};

export const VISIT_STATUSES = Object.keys(STATUS_LABEL) as VisitStatus[];

export const STATUS_CLASS: Record<VisitStatus, string> = {
  EN_COURS: 'en-cours',
  TERMINEE: 'terminee',
  ANNULEE: 'annulee',
};

export const IDENTITY_DOCUMENT_LABELS: Record<IdentityDocumentType, string> = {
  CNI: 'CNI',
  PASSEPORT: 'Passeport',
  PERMIS: 'Permis',
  CARTE_CONSULAIRE: 'Carte consulaire',
  AUTRE: 'Autre',
};

export const IDENTITY_DOCUMENT_TYPES = Object.keys(IDENTITY_DOCUMENT_LABELS) as IdentityDocumentType[];
