package com.orabank.backend.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    ADMIN("Administrateur"),
    RECEPTION("Agent d'accueil"),
    DEFAULT("Employé");

    private final String label;

    public boolean hasApplicationAccess() {
        return this != DEFAULT;
    }
}
