package com.orabank.backend.service;

import com.orabank.backend.entity.Role;
import java.time.Instant;

public record PasswordSetupLinkIssued(
        String matricule,
        String firstName,
        String email,
        Role role,
        boolean passwordAlreadySet,
        String token,
        Instant expiresAt) {

    @Override
    public String toString() {
        return "PasswordSetupLinkIssued(matricule=%s)".formatted(matricule);
    }
}
