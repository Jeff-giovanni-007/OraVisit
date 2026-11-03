package com.orabank.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VisitCancelRequest(
        @NotBlank(message = "Le motif de l'annulation est obligatoire.")
        @Size(max = 255, message = "Le motif de l'annulation ne peut pas dépasser 255 caractères.")
        String reason) {
}
