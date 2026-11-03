package com.orabank.backend.security;

import com.orabank.backend.entity.Employee;
import com.orabank.backend.entity.Role;
import com.orabank.backend.exception.ApiException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static Employee require() {
        return find().orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentification requise."));
    }

    public static Optional<Employee> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Employee employee) {
            return Optional.of(employee);
        }
        return Optional.empty();
    }

    public static Optional<Long> restrictedAgentId() {
        Employee employee = require();
        return employee.getRole() == Role.ADMIN ? Optional.empty() : Optional.of(employee.getId());
    }

    public static boolean isAdmin() {
        return find().map(employee -> employee.getRole() == Role.ADMIN).orElse(false);
    }
}
