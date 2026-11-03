package com.orabank.backend.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditTarget {
    SESSION("Session"),
    EMPLOYEE("Employé"),
    VISITOR("Visiteur"),
    VISIT("Visite"),
    IDENTITY_DOCUMENT("Pièce d'identité"),
    FILE("Fichier");

    private final String label;
}
