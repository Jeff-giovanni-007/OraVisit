package com.orabank.backend.dto;

import com.orabank.backend.entity.Direction;
import com.orabank.backend.entity.Employee;
import com.orabank.backend.entity.Role;

public record EmployeeResponse(
        Long id,
        String matricule,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        Role role,
        Direction direction,
        String poste,
        boolean passwordSet) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getMatricule(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getRole(),
                employee.getDirection(),
                employee.getPoste(),
                employee.isPasswordSet());
    }
}
