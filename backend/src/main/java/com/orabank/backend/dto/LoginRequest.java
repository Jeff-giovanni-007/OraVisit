package com.orabank.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Le matricule est obligatoire.")
        @Size(max = 20, message = "Le matricule ne peut pas dépasser 20 caractères.")
        String matricule,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        String password) {

    @Override
    public String toString() {
        return "LoginRequest(matricule=%s)".formatted(matricule);
    }
}
