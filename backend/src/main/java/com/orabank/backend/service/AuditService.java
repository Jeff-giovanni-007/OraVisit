package com.orabank.backend.service;

import com.orabank.backend.dto.AuditLogResponse;
import com.orabank.backend.dto.PageResponse;
import com.orabank.backend.entity.AuditAction;
import com.orabank.backend.entity.AuditLog;
import com.orabank.backend.entity.AuditOutcome;
import com.orabank.backend.entity.AuditTarget;
import com.orabank.backend.entity.Employee;
import com.orabank.backend.exception.ApiException;
import com.orabank.backend.export.ExcelExporter;
import com.orabank.backend.export.ExportedFile;
import com.orabank.backend.repository.AuditLogRepository;
import com.orabank.backend.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private static final List<String> EXPORT_HEADERS = List.of(
            "Date", "Action", "Issue", "Auteur", "Matricule", "Rôle",
            "Objet", "Identifiant", "Concerné", "Détails", "Adresse IP");

    private final AuditLogRepository auditLogRepository;
    private final ExcelExporter excelExporter;
    private final Clock clock;
    private final TransactionTemplate ownTransaction;

    public AuditService(AuditLogRepository auditLogRepository, ExcelExporter excelExporter, Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.excelExporter = excelExporter;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(AuditAction action, AuditTarget target, Long targetId, String targetLabel, String details) {
        write(CurrentUser.find().orElse(null), action, AuditOutcome.SUCCESS, target, targetId, targetLabel, details);
    }

    public void record(AuditAction action, AuditTarget target, Long targetId, String targetLabel) {
        record(action, target, targetId, targetLabel, null);
    }

    public void record(AuditAction action, AuditTarget target, String details) {
        record(action, target, null, null, details);
    }

    public void recordFor(Employee actor, AuditAction action, AuditTarget target, Long targetId, String targetLabel) {
        write(actor, action, AuditOutcome.SUCCESS, target, targetId, targetLabel, null);
    }

    public void recordFailure(AuditAction action, String matricule, String details) {
        save(AuditLog.builder()
                .occurredAt(clock.instant())
                .action(action)
                .outcome(AuditOutcome.FAILURE)
                .actorMatricule(truncate(blankToDash(matricule), 40))
                .targetType(AuditTarget.SESSION)
                .details(truncate(details, 500))
                .ipAddress(callerIp())
                .build());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String q, AuditAction action, AuditTarget targetType,
                                                 Long actorId, LocalDate dateFrom, LocalDate dateTo,
                                                 Pageable pageable) {
        return PageResponse.of(
                auditLogRepository.findAll(filters(q, action, targetType, actorId, dateFrom, dateTo),
                        Pageables.stable(pageable)),
                AuditLogResponse::from);
    }

    @Transactional(readOnly = true)
    public ExportedFile export(String q, AuditAction action, AuditTarget targetType,
                               Long actorId, LocalDate dateFrom, LocalDate dateTo) {
        List<List<Object>> rows = auditLogRepository
                .findAll(filters(q, action, targetType, actorId, dateFrom, dateTo),
                        Pageables.stable(Sort.by(Sort.Direction.DESC, AuditLog::getOccurredAt)))
                .stream()
                .map(log -> Arrays.<Object>asList(
                        log.getOccurredAt(),
                        log.getAction().getLabel(),
                        log.getOutcome().getLabel(),
                        log.getActorName(),
                        log.getActorMatricule(),
                        log.getActorRole() == null ? null : log.getActorRole().getLabel(),
                        log.getTargetType().getLabel(),
                        log.getTargetId(),
                        log.getTargetLabel(),
                        log.getDetails(),
                        log.getIpAddress()))
                .toList();

        ExportedFile file = excelExporter.write("audit", EXPORT_HEADERS, rows);
        record(AuditAction.AUDIT_EXPORTED, AuditTarget.FILE, "%d ligne(s)".formatted(rows.size()));
        return file;
    }

    private void write(Employee actor, AuditAction action, AuditOutcome outcome, AuditTarget target,
                       Long targetId, String targetLabel, String details) {
        save(AuditLog.builder()
                .occurredAt(clock.instant())
                .action(action)
                .outcome(outcome)
                .actorId(actor == null ? null : actor.getId())
                .actorMatricule(actor == null ? "—" : truncate(actor.getMatricule(), 40))
                .actorName(actor == null ? null : truncate(actor.getFullName(), 140))
                .actorRole(actor == null ? null : actor.getRole())
                .targetType(target)
                .targetId(targetId)
                .targetLabel(truncate(targetLabel, 160))
                .details(truncate(details, 500))
                .ipAddress(callerIp())
                .build());
    }

    private void save(AuditLog entry) {
        ownTransaction.executeWithoutResult(status -> auditLogRepository.save(entry));
    }

    private Specification<AuditLog> filters(String q, AuditAction action, AuditTarget targetType,
                                           Long actorId, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "La date de début doit précéder la date de fin.");
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                for (String term : q.trim().toLowerCase().split("\\s+")) {
                    String pattern = "%" + term + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("actorMatricule")), pattern),
                            cb.like(cb.lower(root.get("actorName")), pattern),
                            cb.like(cb.lower(root.get("targetLabel")), pattern),
                            cb.like(cb.lower(root.get("details")), pattern)));
                }
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (targetType != null) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), startOfDay(dateFrom)));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), startOfDay(dateTo.plusDays(1))));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant();
    }

    private String callerIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
