package com.orabank.backend.dto;

import java.time.Instant;

public record PasswordSetupInfoResponse(String matricule, String fullName, Instant expiresAt,
                                        boolean passwordAlreadySet) {
}
