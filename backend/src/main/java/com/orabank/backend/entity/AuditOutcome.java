package com.orabank.backend.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditOutcome {
    SUCCESS("Réussie"),
    FAILURE("Refusée");

    private final String label;
}
