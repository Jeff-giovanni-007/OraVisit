package com.orabank.backend.dto;

import com.orabank.backend.entity.IdentityDocumentType;
import com.orabank.backend.entity.Visitor;

public record VisitorResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        IdentityDocumentType identityDocumentType,
        String identityCardNumber,
        long visitCount,
        boolean hasIdentityDocument) {

    public static VisitorResponse from(Visitor visitor, long visitCount, boolean hasIdentityDocument) {
        return new VisitorResponse(
                visitor.getId(),
                visitor.getFirstName(),
                visitor.getLastName(),
                visitor.getFullName(),
                visitor.getEmail(),
                visitor.getPhone(),
                visitor.getIdentityDocumentType(),
                visitor.getIdentityCardNumber(),
                visitCount,
                hasIdentityDocument);
    }

    /** Fiche réduite de l'autocomplétion : de quoi identifier la personne au guichet, sans ses coordonnées e-mail. */
    public VisitorResponse withoutEmail() {
        return new VisitorResponse(id, firstName, lastName, fullName, null, phone, identityDocumentType,
                identityCardNumber, visitCount, hasIdentityDocument);
    }
}
