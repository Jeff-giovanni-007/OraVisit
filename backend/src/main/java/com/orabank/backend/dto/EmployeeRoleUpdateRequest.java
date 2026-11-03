package com.orabank.backend.dto;

import com.orabank.backend.entity.Role;
import jakarta.validation.constraints.NotNull;

public record EmployeeRoleUpdateRequest(
        @NotNull(message = "Le rôle est obligatoire.")
        Role role) {
}
