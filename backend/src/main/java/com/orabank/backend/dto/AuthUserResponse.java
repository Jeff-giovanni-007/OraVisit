package com.orabank.backend.dto;

import com.orabank.backend.entity.Employee;
import com.orabank.backend.entity.Role;

public record AuthUserResponse(
        Long id,
        String matricule,
        String firstName,
        String lastName,
        Role role) {

    public static AuthUserResponse from(Employee employee) {
        return new AuthUserResponse(
                employee.getId(),
                employee.getMatricule(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getRole());
    }
}
