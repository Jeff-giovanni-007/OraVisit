package com.orabank.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VisitCreateRequest(
        @NotNull(message = "Le visiteur est obligatoire.")
        @Positive(message = "Le visiteur est invalide.")
        Long visitorId,

        @NotNull(message = "L'employé hôte est obligatoire.")
        @Positive(message = "L'employé hôte est invalide.")
        Long hostEmployeeId,

        @NotBlank(message = "Le motif est obligatoire.")
        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String purpose,

        @NotBlank(message = "Le numéro de badge est obligatoire.")
        @Size(max = 20, message = "Le numéro de badge ne peut pas dépasser 20 caractères.")
        String badgeNumber) {
}
