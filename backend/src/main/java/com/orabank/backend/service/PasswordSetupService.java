package com.orabank.backend.service;

import com.orabank.backend.config.AppProperties;
import com.orabank.backend.dto.PasswordPolicy;
import com.orabank.backend.dto.PasswordSetupInfoResponse;
import com.orabank.backend.dto.PasswordSetupRequest;
import com.orabank.backend.entity.AuditAction;
import com.orabank.backend.entity.AuditTarget;
import com.orabank.backend.entity.Employee;
import com.orabank.backend.exception.ApiException;
import com.orabank.backend.repository.EmployeeRepository;
import com.orabank.backend.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordSetupService {

    /** Échéance d'un lien telle que l'affichent l'e-mail et le journal d'audit : « 10/10/2026 à 10h12 ». */
    public static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm", Locale.FRENCH);

    private final EmployeeRepository employeeRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final AppProperties properties;
    private final Clock clock;

    @Transactional
    public void issueLink(Employee employee) {
        Instant expiresAt = clock.instant().plus(properties.passwordSetupLinkValidity());
        String token = jwtService.generatePasswordSetupToken(employee, passwordVersion(employee), expiresAt);
        eventPublisher.publishEvent(new PasswordSetupLinkIssued(employee.getMatricule(), employee.getFirstName(),
                employee.getEmail(), employee.getRole(), employee.isPasswordSet(), token, expiresAt));
        log.info("Lien de definition du mot de passe emis pour l'employe {}", employee.getMatricule());
        auditService.record(AuditAction.PASSWORD_SETUP_LINK_ISSUED, AuditTarget.EMPLOYEE, employee.getId(),
                "%s (%s)".formatted(employee.getFullName(), employee.getMatricule()),
                "Envoyé à %s, valable jusqu'au %s".formatted(employee.getEmail(),
                        EXPIRY_FORMAT.format(expiresAt.atZone(clock.getZone()))));
    }

    @Transactional(readOnly = true)
    public PasswordSetupInfoResponse describe(String token) {
        Claims claims = parse(token);
        Employee employee = requireUsable(claims);
        return new PasswordSetupInfoResponse(employee.getMatricule(), employee.getFullName(),
                claims.getExpiration().toInstant(), employee.isPasswordSet());
    }

    @Transactional
    public void definePassword(PasswordSetupRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES) {
            throw ApiException.badRequest("PASSWORD_TOO_LONG", PasswordPolicy.BYTES_MESSAGE);
        }
        Employee employee = requireUsable(parse(request.token()));
        employee.setPasswordHash(passwordEncoder.encode(request.password()));
        log.info("Mot de passe defini par l'employe {}", employee.getMatricule());
        auditService.recordFor(employee, AuditAction.PASSWORD_DEFINED, AuditTarget.EMPLOYEE, employee.getId(),
                "%s (%s)".formatted(employee.getFullName(), employee.getMatricule()));
    }

    private Claims parse(String token) {
        try {
            return jwtService.parsePasswordSetupToken(token.trim());
        } catch (JwtException | IllegalArgumentException invalid) {
            throw invalidLink();
        }
    }

    private Employee requireUsable(Claims claims) {
        return employeeRepository.findByMatriculeIgnoreCase(claims.getSubject())
                .filter(employee -> employee.getRole().hasApplicationAccess())
                .filter(employee -> passwordVersion(employee).equals(jwtService.passwordVersionOf(claims)))
                .orElseThrow(PasswordSetupService::invalidLink);
    }

    private static ApiException invalidLink() {
        return ApiException.badRequest("PASSWORD_SETUP_LINK_INVALID",
                "Ce lien est invalide ou a expiré. Demandez un nouveau lien à votre administrateur.");
    }

    private static String passwordVersion(Employee employee) {
        String current = employee.getPasswordHash() == null ? "" : employee.getPasswordHash();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(current.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
