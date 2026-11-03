package com.orabank.backend.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Direction {

    DIRECTION_GENERALE("Direction Générale"),
    DIRECTION_GENERALE_ADJOINTE("Direction Générale Adjointe"),
    DIRECTION_ADMINISTRATION_FINANCES("Administration et Finances"),
    DIRECTION_TRESORERIE_SALLE_MARCHES("Trésorerie et Salle des Marchés"),
    DIRECTION_AUDIT_INTERNE("Audit Interne"),
    DIRECTION_CONFORMITE("Conformité"),
    DIRECTION_OPERATIONS("Opérations"),
    DIRECTION_CAPITAL_HUMAIN("Capital Humain"),
    DIRECTION_RISQUES("Risques"),
    DIRECTION_RECOUVREMENT("Recouvrement"),
    DIRECTION_SYSTEMES_INFORMATION("Systèmes d'Information"),
    DIRECTION_JURIDIQUE_CONTENTIEUX("Juridique et Contentieux"),
    DIRECTION_CLIENTELE_PARTICULIERS_PROFESSIONNELS("Clientèle Particuliers et Professionnels"),
    DIRECTION_CLIENTELE_INSTITUTIONNELLE("Clientèle Institutionnelle"),
    DIRECTION_BANQUE_DIGITALE("Banque Digitale"),
    DIRECTION_CLIENTELE_ENTREPRISES("Clientèle Entreprises");

    private final String label;
}
