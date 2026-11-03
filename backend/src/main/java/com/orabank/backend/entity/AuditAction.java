package com.orabank.backend.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditAction {
    LOGIN_SUCCEEDED("Connexion"),
    LOGIN_FAILED("Échec de connexion"),
    PASSWORD_SETUP_LINK_ISSUED("Lien de mot de passe envoyé"),
    PASSWORD_DEFINED("Mot de passe défini"),
    VISITOR_CREATED("Visiteur enregistré"),
    VISITOR_UPDATED("Visiteur modifié"),
    VISITOR_DELETED("Visiteur supprimé"),
    IDENTITY_DOCUMENT_STORED("Pièce d'identité enregistrée"),
    IDENTITY_DOCUMENT_VIEWED("Pièce d'identité consultée"),
    VISIT_CREATED("Visite enregistrée"),
    VISIT_UPDATED("Visite modifiée"),
    VISIT_CHECKED_OUT("Sortie enregistrée"),
    VISIT_CANCELLED("Visite annulée"),
    VISIT_DELETED("Visite supprimée"),
    VISITS_EXPORTED("Export des visites"),
    EMPLOYEE_CREATED("Employé créé"),
    EMPLOYEE_UPDATED("Employé modifié"),
    EMPLOYEE_ROLE_CHANGED("Rôle modifié"),
    EMPLOYEE_DELETED("Employé supprimé"),
    EMPLOYEES_IMPORTED("Import des employés"),
    EMPLOYEES_EXPORTED("Export des employés"),
    AUDIT_EXPORTED("Export du journal d'audit");

    private final String label;
}
