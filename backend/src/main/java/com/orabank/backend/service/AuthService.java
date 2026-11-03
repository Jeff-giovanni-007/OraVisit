package com.orabank.backend.service;

import com.orabank.backend.dto.AuthUserResponse;
import com.orabank.backend.dto.LoginRequest;
import com.orabank.backend.dto.LoginResponse;
import com.orabank.backend.entity.AuditAction;
import com.orabank.backend.entity.AuditTarget;
import com.orabank.backend.entity.Employee;
import com.orabank.backend.exception.ApiException;
import com.orabank.backend.repository.EmployeeRepository;
import com.orabank.backend.security.JwtService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String matricule = request.matricule().trim();
        Optional<Employee> candidate = employeeRepository.findByMatriculeIgnoreCase(matricule)
                .filter(found -> found.getRole().hasApplicationAccess())
                .filter(Employee::isPasswordSet)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()));

        if (candidate.isEmpty()) {
            log.info("Echec de connexion : matricule={}", matricule);
            auditService.recordFailure(AuditAction.LOGIN_FAILED, matricule, "Matricule ou mot de passe incorrect.");
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Matricule ou mot de passe incorrect.");
        }

        Employee employee = candidate.get();
        log.info("Connexion reussie : matricule={}, role={}", employee.getMatricule(), employee.getRole());
        auditService.recordFor(employee, AuditAction.LOGIN_SUCCEEDED, AuditTarget.SESSION, employee.getId(),
                employee.getFullName());
        return new LoginResponse(jwtService.generateToken(employee), AuthUserResponse.from(employee));
    }
}
