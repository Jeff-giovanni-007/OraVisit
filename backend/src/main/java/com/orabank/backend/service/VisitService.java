package com.orabank.backend.service;

import com.orabank.backend.dto.PageResponse;
import com.orabank.backend.dto.VisitCancelRequest;
import com.orabank.backend.dto.VisitCreateRequest;
import com.orabank.backend.dto.VisitResponse;
import com.orabank.backend.dto.VisitUpdateRequest;
import com.orabank.backend.entity.AuditAction;
import com.orabank.backend.entity.AuditTarget;
import com.orabank.backend.entity.Direction;
import com.orabank.backend.entity.Employee;
import com.orabank.backend.entity.Visit;
import com.orabank.backend.entity.VisitStatus;
import com.orabank.backend.entity.Visitor;
import com.orabank.backend.exception.ApiException;
import com.orabank.backend.export.ExcelExporter;
import com.orabank.backend.export.ExportedFile;
import com.orabank.backend.repository.EmployeeRepository;
import com.orabank.backend.repository.VisitRepository;
import com.orabank.backend.repository.VisitorIdentityDocumentRepository;
import com.orabank.backend.repository.VisitorRepository;
import com.orabank.backend.security.CurrentUser;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisitService {

    private static final List<String> EXPORT_HEADERS = List.of(
            "Arrivée", "Sortie", "Durée", "Visiteur", "Téléphone", "Pièce",
            "Employé hôte", "Matricule de l'hôte", "Poste de l'hôte", "Direction", "Motif", "Badge", "Statut",
            "Annulée le", "Motif de l'annulation", "Enregistrée par");

    private final VisitRepository visitRepository;
    private final VisitorRepository visitorRepository;
    private final EmployeeRepository employeeRepository;
    private final VisitorIdentityDocumentRepository identityDocumentRepository;
    private final VisitorIdentityDocumentService identityDocumentService;
    private final ExcelExporter excelExporter;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public VisitResponse create(VisitCreateRequest request, MultipartFile identityDocument) {
        Visitor visitor = visitorRepository.findById(request.visitorId())
                .orElseThrow(() -> ApiException.notFound("Visiteur", request.visitorId()));
        Employee host = employeeRepository.findById(request.hostEmployeeId())
                .orElseThrow(() -> ApiException.notFound("Employé", request.hostEmployeeId()));

        String badgeNumber = request.badgeNumber().trim();
        if (visitRepository.existsByBadgeNumberIgnoreCaseAndStatus(badgeNumber, VisitStatus.EN_COURS)) {
            throw ApiException.conflict("BADGE_ALREADY_IN_USE",
                    "Ce numéro de badge est déjà attribué à une visite en cours.");
        }
        if (visitRepository.existsByVisitorIdAndStatus(visitor.getId(), VisitStatus.EN_COURS)) {
            throw ApiException.conflict("VISITOR_ALREADY_CHECKED_IN", "Ce visiteur a déjà une visite en cours.");
        }

        if (identityDocument != null && !identityDocument.isEmpty()) {
            identityDocumentService.store(visitor.getId(), identityDocument);
        } else if (!identityDocumentRepository.existsById(visitor.getId())) {
            throw ApiException.badRequest("IDENTITY_DOCUMENT_REQUIRED",
                    "La pièce d'identité numérisée du visiteur est obligatoire pour enregistrer une visite.");
        }

        Long currentId = CurrentUser.require().getId();
        Employee registeredBy = employeeRepository.findById(currentId)
                .orElseThrow(() -> ApiException.notFound("Employé", currentId));

        Visit visit = Visit.builder()
                .visitor(visitor)
                .hostEmployee(host)
                .registeredBy(registeredBy)
                .direction(host.getDirection())
                .purpose(request.purpose().trim())
                .badgeNumber(badgeNumber)
                .status(VisitStatus.EN_COURS)
                .checkInAt(clock.instant())
                .build();

        Visit saved = visitRepository.saveAndFlush(visit);
        log.info("Visite enregistree : id={}, visiteur={}, hote={}, badge={}",
                saved.getId(), visitor.getId(), host.getId(), badgeNumber);
        auditService.record(AuditAction.VISIT_CREATED, AuditTarget.VISIT, saved.getId(),
                visitor.getFullName(), details(saved));
        return VisitResponse.from(saved, true);
    }

    @Transactional
    public VisitResponse update(Long id, VisitUpdateRequest request) {
        Visit visit = require(id);
        requireAccess(id);
        String badgeNumber = request.badgeNumber().trim();
        if (visit.getStatus() == VisitStatus.EN_COURS
                && visitRepository.existsByBadgeNumberIgnoreCaseAndStatusAndIdNot(badgeNumber, VisitStatus.EN_COURS, id)) {
            throw ApiException.conflict("BADGE_ALREADY_IN_USE",
                    "Ce numéro de badge est déjà attribué à une visite en cours.");
        }

        if (!visit.getHostEmployee().getId().equals(request.hostEmployeeId())) {
            Employee host = employeeRepository.findById(request.hostEmployeeId())
                    .orElseThrow(() -> ApiException.notFound("Employé", request.hostEmployeeId()));
            visit.setHostEmployee(host);
            visit.setDirection(host.getDirection());
        }
        visit.setPurpose(request.purpose().trim());
        visit.setBadgeNumber(badgeNumber);

        Visit saved = visitRepository.saveAndFlush(visit);
        log.info("Visite modifiee : id={}, hote={}, badge={}", id, saved.getHostEmployee().getId(), badgeNumber);
        auditService.record(AuditAction.VISIT_UPDATED, AuditTarget.VISIT, id,
                saved.getVisitor().getFullName(), details(saved));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<VisitResponse> search(String q, VisitStatus status, Direction direction,
                                              LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        Page<Visit> page = visitRepository.findAll(filters(q, status, direction, dateFrom, dateTo),
                Pageables.stable(pageable));
        Set<Long> withIdentityDocument = visitorsWithIdentityDocument(page.getContent());
        return PageResponse.of(page, visit ->
                VisitResponse.from(visit, withIdentityDocument.contains(visit.getVisitor().getId())));
    }

    @Transactional
    public VisitResponse checkout(Long id) {
        Visit visit = require(id);
        requireAccess(id);
        requireInProgress(visit, "Seule une visite en cours peut faire l'objet d'une sortie");

        Instant now = clock.instant();
        visit.setCheckOutAt(now.isBefore(visit.getCheckInAt()) ? visit.getCheckInAt() : now);
        visit.setStatus(VisitStatus.TERMINEE);

        log.info("Check-out effectue : visite={}, badge={}", id, visit.getBadgeNumber());
        auditService.record(AuditAction.VISIT_CHECKED_OUT, AuditTarget.VISIT, id,
                visit.getVisitor().getFullName(), details(visit));
        return toResponse(visit);
    }

    @Transactional
    public VisitResponse cancel(Long id, VisitCancelRequest request) {
        Visit visit = require(id);
        requireAccess(id);
        requireInProgress(visit, "Seule une visite en cours peut être annulée");

        Instant now = clock.instant();
        visit.setCancelledAt(now.isBefore(visit.getCheckInAt()) ? visit.getCheckInAt() : now);
        visit.setCancellationReason(request.reason().trim());
        visit.setStatus(VisitStatus.ANNULEE);

        log.info("Visite annulee : id={}, badge={}", id, visit.getBadgeNumber());
        auditService.record(AuditAction.VISIT_CANCELLED, AuditTarget.VISIT, id,
                visit.getVisitor().getFullName(),
                "%s, motif de l'annulation %s".formatted(details(visit), visit.getCancellationReason()));
        return toResponse(visit);
    }

    @Transactional
    public void delete(Long id) {
        Visit visit = require(id);
        requireAccess(id);
        String visitor = visit.getVisitor().getFullName();
        String details = details(visit);
        visitRepository.delete(visit);
        log.info("Visite supprimee : id={}, badge={}", id, visit.getBadgeNumber());
        auditService.record(AuditAction.VISIT_DELETED, AuditTarget.VISIT, id, visitor, details);
    }

    private void requireAccess(Long visitId) {
        CurrentUser.restrictedAgentId().ifPresent(agentId -> {
            if (!visitRepository.existsByIdAndRegisteredById(visitId, agentId)) {
                throw ApiException.forbidden("VISIT_NOT_OWNED",
                        "Cette visite a été enregistrée par un autre agent d'accueil.");
            }
        });
    }

    @Transactional(readOnly = true)
    public ExportedFile export(String q, VisitStatus status, Direction direction,
                               LocalDate dateFrom, LocalDate dateTo) {
        List<Visit> visits = visitRepository.findAll(
                filters(q, status, direction, dateFrom, dateTo),
                Pageables.stable(Sort.by(Sort.Direction.DESC, Visit::getCheckInAt)));

        List<List<Object>> rows = visits.stream()
                .map(visit -> Arrays.<Object>asList(
                        visit.getCheckInAt(),
                        visit.getCheckOutAt(),
                        duration(visit),
                        visit.getVisitor().getFullName(),
                        visit.getVisitor().getPhone(),
                        "%s %s".formatted(visit.getVisitor().getIdentityDocumentType().getLabel(),
                                visit.getVisitor().getIdentityCardNumber()),
                        visit.getHostEmployee().getFullName(),
                        visit.getHostEmployee().getMatricule(),
                        visit.getHostEmployee().getPoste(),
                        visit.getDirection().getLabel(),
                        visit.getPurpose(),
                        visit.getBadgeNumber(),
                        visit.getStatus().getLabel(),
                        visit.getCancelledAt(),
                        visit.getCancellationReason(),
                        visit.getRegisteredBy().getFullName()))
                .toList();

        ExportedFile file = excelExporter.write("visites", EXPORT_HEADERS, rows);
        log.info("Export Excel de {} visite(s)", rows.size());
        auditService.record(AuditAction.VISITS_EXPORTED, AuditTarget.FILE, "%d visite(s)".formatted(rows.size()));
        return file;
    }

    private Specification<Visit> filters(String q, VisitStatus status, Direction direction,
                                         LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "La date de début doit précéder la date de fin.");
        }
        Long agentId = CurrentUser.restrictedAgentId().orElse(null);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (agentId != null) {
                predicates.add(cb.equal(root.get("registeredBy").get("id"), agentId));
            }
            if (q != null && !q.isBlank()) {
                var visitor = root.join("visitor", JoinType.INNER);
                var host = root.join("hostEmployee", JoinType.INNER);
                for (String term : q.trim().toLowerCase().split("\\s+")) {
                    String pattern = "%" + term + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(visitor.get("firstName")), pattern),
                            cb.like(cb.lower(visitor.get("lastName")), pattern),
                            cb.like(cb.lower(visitor.get("phone")), pattern),
                            cb.like(cb.lower(visitor.get("identityCardNumber")), pattern),
                            cb.like(cb.lower(host.get("firstName")), pattern),
                            cb.like(cb.lower(host.get("lastName")), pattern),
                            cb.like(cb.lower(root.get("badgeNumber")), pattern),
                            cb.like(cb.lower(root.get("purpose")), pattern)));
                }
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (direction != null) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("checkInAt"), startOfDay(dateFrom)));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("checkInAt"), startOfDay(dateTo.plusDays(1))));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String details(Visit visit) {
        return "Badge %s, hôte %s (%s), motif %s".formatted(visit.getBadgeNumber(),
                visit.getHostEmployee().getFullName(), visit.getHostEmployee().getMatricule(), visit.getPurpose());
    }

    private VisitResponse toResponse(Visit visit) {
        return VisitResponse.from(visit, identityDocumentRepository.existsById(visit.getVisitor().getId()));
    }

    private Set<Long> visitorsWithIdentityDocument(List<Visit> visits) {
        if (visits.isEmpty()) {
            return Set.of();
        }
        List<Long> visitorIds = visits.stream().map(visit -> visit.getVisitor().getId()).distinct().toList();
        return new HashSet<>(identityDocumentRepository.findVisitorIdsIn(visitorIds));
    }

    private void requireInProgress(Visit visit, String message) {
        if (visit.getStatus() != VisitStatus.EN_COURS) {
            throw ApiException.conflict("VISIT_NOT_ACTIVE",
                    "%s (statut actuel : %s).".formatted(message, visit.getStatus().getLabel()));
        }
    }

    private Visit require(Long id) {
        return visitRepository.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Visite", id));
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant();
    }

    private Duration duration(Visit visit) {
        if (visit.getCheckOutAt() == null) {
            return null;
        }
        return Duration.between(visit.getCheckInAt(), visit.getCheckOutAt()).truncatedTo(ChronoUnit.MINUTES);
    }
}
