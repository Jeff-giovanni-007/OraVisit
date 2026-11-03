package com.orabank.backend.dto;

import com.orabank.backend.entity.IdentityDocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VisitorUpdateRequest(
        @NotBlank(message = "Le prénom est obligatoire.")
        @Size(max = 60, message = "Le prénom ne peut pas dépasser 60 caractères.")
        String firstName,

        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 60, message = "Le nom ne peut pas dépasser 60 caractères.")
        String lastName,

        @Email(message = "L'email est invalide.")
        @Size(max = 120, message = "L'email ne peut pas dépasser 120 caractères.")
        String email,

        @NotBlank(message = "Le téléphone est obligatoire.")
        @Size(max = 30, message = "Le téléphone ne peut pas dépasser 30 caractères.")
        @Pattern(regexp = "^[0-9+().\\s-]{6,30}$", message = "Le téléphone est invalide.")
        String phone,

        @NotNull(message = "Le type de pièce est obligatoire.")
        IdentityDocumentType identityDocumentType,

        @NotBlank(message = "Le numéro de pièce est obligatoire.")
        @Size(max = 40, message = "Le numéro de pièce ne peut pas dépasser 40 caractères.")
        String identityCardNumber) {
}
