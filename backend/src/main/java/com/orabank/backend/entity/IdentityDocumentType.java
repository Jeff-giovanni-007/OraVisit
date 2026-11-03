package com.orabank.backend.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IdentityDocumentType {

    CNI("CNI"),
    PASSEPORT("Passeport"),
    PERMIS("Permis"),
    CARTE_CONSULAIRE("Carte consulaire"),
    AUTRE("Autre");

    private final String label;
}
