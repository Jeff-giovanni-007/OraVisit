package com.orabank.backend.dto;

import com.orabank.backend.entity.IdentityDocumentType;
import com.orabank.backend.entity.Visitor;

public record VisitorSummaryResponse(
        Long id,
        String fullName,
        String phone,
        IdentityDocumentType identityDocumentType,
        String identityCardNumber,
        boolean hasIdentityDocument) {

    public static VisitorSummaryResponse from(Visitor visitor, boolean hasIdentityDocument) {
        return new VisitorSummaryResponse(
                visitor.getId(),
                visitor.getFullName(),
                visitor.getPhone(),
                visitor.getIdentityDocumentType(),
                visitor.getIdentityCardNumber(),
                hasIdentityDocument);
    }
}
