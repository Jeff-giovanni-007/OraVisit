package com.orabank.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordSetupValidateRequest(
        @NotBlank(message = "Le lien est incomplet.")
        @Size(max = 1000, message = "Le lien est invalide.")
        String token) {

    @Override
    public String toString() {
        return "PasswordSetupValidateRequest(****)";
    }
}
