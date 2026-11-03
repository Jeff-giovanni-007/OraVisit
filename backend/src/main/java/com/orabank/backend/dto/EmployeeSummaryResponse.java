package com.orabank.backend.dto;

import com.orabank.backend.entity.Direction;
import com.orabank.backend.entity.Employee;

public record EmployeeSummaryResponse(
        Long id,
        String matricule,
        String fullName,
        Direction direction,
        String poste) {

    public static EmployeeSummaryResponse from(Employee employee) {
        return new EmployeeSummaryResponse(
                employee.getId(),
                employee.getMatricule(),
                employee.getFullName(),
                employee.getDirection(),
                employee.getPoste());
    }
}
