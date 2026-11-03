package com.orabank.backend.dto;

import com.orabank.backend.entity.Direction;
import com.orabank.backend.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmployeeCreateRequest(
        @NotBlank(message = "Le matricule est obligatoire.")
        @Size(max = 20, message = "Le matricule ne peut pas dépasser 20 caractères.")
        String matricule,

        @NotBlank(message = "Le prénom est obligatoire.")
        @Size(max = 60, message = "Le prénom ne peut pas dépasser 60 caractères.")
        String firstName,

        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 60, message = "Le nom ne peut pas dépasser 60 caractères.")
        String lastName,

        @NotBlank(message = "L'email est obligatoire.")
        @Email(message = "L'email est invalide.")
        @Size(max = 120, message = "L'email ne peut pas dépasser 120 caractères.")
        String email,

        @NotBlank(message = "Le téléphone est obligatoire.")
        @Size(max = 30, message = "Le téléphone ne peut pas dépasser 30 caractères.")
        @Pattern(regexp = "^[0-9+().\\s-]{6,30}$", message = "Le téléphone est invalide.")
        String phone,

        @NotNull(message = "Le rôle est obligatoire.")
        Role role,

        @NotNull(message = "La direction est obligatoire.")
        Direction direction,

        @NotBlank(message = "Le poste est obligatoire.")
        @Size(max = 100, message = "Le poste ne peut pas dépasser 100 caractères.")
        String poste) {
}
