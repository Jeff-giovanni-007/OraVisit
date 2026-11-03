package com.orabank.backend.service;

import com.orabank.backend.dto.PageResponse;
import com.orabank.backend.dto.VisitorCreateRequest;
import com.orabank.backend.dto.VisitorResponse;
import com.orabank.backend.dto.VisitorUpdateRequest;
import com.orabank.backend.entity.AuditAction;
import com.orabank.backend.entity.AuditTarget;
import com.orabank.backend.entity.IdentityDocumentType;
import com.orabank.backend.entity.Visit;
import com.orabank.backend.entity.VisitStatus;
import com.orabank.backend.entity.Visitor;
import com.orabank.backend.exception.ApiException;
import com.orabank.backend.repository.VisitRepository;
import com.orabank.backend.repository.VisitorIdentityDocumentRepository;
import com.orabank.backend.repository.VisitorRepository;
import com.orabank.backend.security.CurrentUser;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorService {

    private final VisitorRepository visitorRepository;
    private final VisitRepository visitRepository;
    private final VisitorIdentityDocumentRepository identityDocumentRepository;
    private final AuditService auditService;

    private static final Sort NAME_TIE_BREAK = Sort.by(Visitor::getLastName, Visitor::getFirstName);
    private static final String VISIT_COUNT = "visitCount";

    private static final int LOOKUP_LIMIT = 8;

    @Transactional(readOnly = true)
    public PageResponse<VisitorResponse> search(String q, IdentityDocumentType documentType, String documentNumber,
                                                Pageable pageable) {
        Long agentId = CurrentUser.restrictedAgentId().orElse(null);
        Specification<Visitor> specification = filters(q, documentType, documentNumber, agentId);
        Sort.Order byVisitCount = pageable.getSort().getOrderFor(VISIT_COUNT);
        Page<Visitor> page = agentId != null && byVisitCount != null
                ? visitorRepository.findAll(specification.and(orderByOwnVisits(agentId, pageable.getSort())),
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                : visitorRepository.findAll(specification, stable(pageable));
        Map<Long, Long> visitCounts = visitCounts(page.getContent(), agentId);
        Set<Long> withIdentityDocument = withIdentityDocument(page.getContent());
        return PageResponse.of(page, visitor -> VisitorResponse.from(visitor,
                visitCounts.getOrDefault(visitor.getId(), 0L),
                withIdentityDocument.contains(visitor.getId())));
    }

    @Transactional(readOnly = true)
    public List<VisitorResponse> lookup(String q, IdentityDocumentType documentType, String documentNumber) {
        Pageable pageable = PageRequest.of(0, LOOKUP_LIMIT, Pageables.stable(NAME_TIE_BREAK));
        Page<Visitor> page = visitorRepository.findAll(filters(q, documentType, documentNumber, null), pageable);
        Map<Long, Long> visitCounts = visitCounts(page.getContent(), CurrentUser.restrictedAgentId().orElse(null));
        Set<Long> withIdentityDocument = withIdentityDocument(page.getContent());
        return page.getContent().stream()
                .map(visitor -> VisitorResponse.from(visitor,
                        visitCounts.getOrDefault(visitor.getId(), 0L),
                        withIdentityDocument.contains(visitor.getId())))
                .map(VisitorResponse::withoutEmail)
                .toList();
    }

    @Transactional
    public VisitorResponse create(VisitorCreateRequest request) {
        String documentNumber = request.identityCardNumber().trim();
        Visitor existing = visitorRepository
                .findByIdentityDocumentTypeAndIdentityCardNumberIgnoreCase(request.identityDocumentType(), documentNumber)
                .orElse(null);
        Visitor visitor = existing != null ? existing : Visitor.builder()
                .identityDocumentType(request.identityDocumentType())
                .identityCardNumber(documentNumber)
                .build();

        visitor.setFirstName(request.firstName().trim());
        visitor.setLastName(request.lastName().trim());
        visitor.setEmail(trimToNull(request.email()));
        visitor.setPhone(request.phone().trim());
        if (visitor.getCreatedBy() == null) {
            visitor.setCreatedBy(CurrentUser.require());
        }
        visitor = visitorRepository.save(visitor);

        log.info("Visiteur enregistre : id={}", visitor.getId());
        auditService.record(existing == null ? AuditAction.VISITOR_CREATED : AuditAction.VISITOR_UPDATED,
                AuditTarget.VISITOR, visitor.getId(), visitor.getFullName(), documentDetails(visitor));
        return VisitorResponse.from(visitor,
                countVisits(List.of(visitor.getId()), CurrentUser.restrictedAgentId().orElse(null))
                        .getOrDefault(visitor.getId(), 0L),
                identityDocumentRepository.existsById(visitor.getId()));
    }

    @Transactional
    public VisitorResponse update(Long id, VisitorUpdateRequest request) {
        Visitor visitor = require(id);
        requireAccess(id);
        String documentNumber = request.identityCardNumber().trim();
        visitorRepository
                .findByIdentityDocumentTypeAndIdentityCardNumberIgnoreCase(request.identityDocumentType(), documentNumber)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("VISITOR_DOCUMENT_ALREADY_USED",
                            "Cette pièce appartient déjà au visiteur %s.".formatted(other.getFullName()));
                });

        visitor.setFirstName(request.firstName().trim());
        visitor.setLastName(request.lastName().trim());
        visitor.setEmail(trimToNull(request.email()));
        visitor.setPhone(request.phone().trim());
        visitor.setIdentityDocumentType(request.identityDocumentType());
        visitor.setIdentityCardNumber(documentNumber);
        visitor = visitorRepository.saveAndFlush(visitor);

        log.info("Visiteur modifie : id={}", id);
        auditService.record(AuditAction.VISITOR_UPDATED, AuditTarget.VISITOR, id, visitor.getFullName(),
                documentDetails(visitor));
        return VisitorResponse.from(visitor,
                countVisits(List.of(id), CurrentUser.restrictedAgentId().orElse(null)).getOrDefault(id, 0L),
                identityDocumentRepository.existsById(id));
    }

    @Transactional
    public void delete(Long id) {
        Visitor visitor = require(id);
        requireAccess(id);
        if (visitRepository.existsByVisitorIdAndStatus(id, VisitStatus.EN_COURS)) {
            throw ApiException.conflict("VISITOR_CHECKED_IN",
                    "Ce visiteur a une visite en cours : enregistrez sa sortie avant de le supprimer.");
        }
        CurrentUser.restrictedAgentId().ifPresent(agentId -> {
            if (visitRepository.existsByVisitorIdAndRegisteredByIdNot(id, agentId)) {
                throw ApiException.forbidden("VISITOR_HAS_OTHER_AGENTS_VISITS",
                        "Ce visiteur a aussi été reçu par d'autres agents d'accueil : "
                                + "seul un administrateur peut le supprimer.");
            }
        });
        String name = visitor.getFullName();
        String document = documentDetails(visitor);
        identityDocumentRepository.deleteById(id);
        int visits = visitRepository.deleteByVisitorId(id);
        visitorRepository.delete(visitor);
        log.info("Visiteur supprime : id={}, visites supprimees={}", id, visits);
        auditService.record(AuditAction.VISITOR_DELETED, AuditTarget.VISITOR, id, name,
                "%s, %d visite(s) supprimée(s)".formatted(document, visits));
    }

    @Transactional(readOnly = true)
    public void requireAccess(Long visitorId) {
        CurrentUser.restrictedAgentId().ifPresent(agentId -> {
            if (!visitorRepository.existsAccessibleBy(visitorId, agentId)) {
                throw ApiException.forbidden("VISITOR_NOT_OWNED",
                        "Ce visiteur a été enregistré par un autre agent d'accueil.");
            }
        });
    }

    private static Pageable stable(Pageable pageable) {
        if (pageable.getSort().getOrderFor("lastName") != null) {
            return Pageables.stable(pageable);
        }
        return Pageables.stable(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(NAME_TIE_BREAK)));
    }

    private Specification<Visitor> filters(String q, IdentityDocumentType documentType, String documentNumber,
                                           Long agentId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (agentId != null) {
                predicates.add(ownedBy(root, query, cb, agentId));
            }
            if (q != null && !q.isBlank()) {
                for (String term : q.trim().toLowerCase().split("\\s+")) {
                    String pattern = "%" + term + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("firstName")), pattern),
                            cb.like(cb.lower(root.get("lastName")), pattern),
                            cb.like(cb.lower(root.get("phone")), pattern),
                            cb.like(cb.lower(root.get("identityCardNumber")), pattern)));
                }
            }
            if (documentType != null) {
                predicates.add(cb.equal(root.get("identityDocumentType"), documentType));
            }
            if (documentNumber != null && !documentNumber.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("identityCardNumber")), documentNumber.trim().toLowerCase()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate ownedBy(Root<Visitor> root, CriteriaQuery<?> query, CriteriaBuilder cb, Long agentId) {
        Subquery<Integer> received = query.subquery(Integer.class);
        Root<Visit> visit = received.from(Visit.class);
        received.select(cb.literal(1))
                .where(cb.equal(visit.get("visitor"), root),
                        cb.equal(visit.get("registeredBy").get("id"), agentId));
        return cb.or(cb.equal(root.get("createdBy").get("id"), agentId), cb.exists(received));
    }

    /** Tri d'un agent sur le nombre de SES visites (la formule {@code visitCount} compte celles de tous les agents). */
    private Specification<Visitor> orderByOwnVisits(Long agentId, Sort requested) {
        return (root, query, cb) -> {
            if (Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType())) {
                return null;
            }
            Subquery<Long> ownVisits = query.subquery(Long.class);
            Root<Visit> visit = ownVisits.from(Visit.class);
            ownVisits.select(cb.count(visit))
                    .where(cb.equal(visit.get("visitor"), root),
                            cb.equal(visit.get("registeredBy").get("id"), agentId));
            List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
            for (Sort.Order order : Pageables.stable(requested.and(NAME_TIE_BREAK))) {
                Expression<?> expression = VISIT_COUNT.equals(order.getProperty())
                        ? ownVisits : attribute(root, order.getProperty());
                orders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
            }
            query.orderBy(orders);
            return null;
        };
    }

    private static Expression<?> attribute(Root<Visitor> root, String property) {
        try {
            return root.get(property);
        } catch (IllegalArgumentException unknown) {
            throw ApiException.badRequest("INVALID_PARAMETER",
                    "Le tri demandé sur « %s » n'est pas pris en charge.".formatted(property));
        }
    }

    /** Nombre de visites par visiteur : toutes pour un administrateur, seulement les siennes pour un agent. */
    private Map<Long, Long> visitCounts(List<Visitor> visitors, Long agentId) {
        if (agentId == null) {
            return visitors.stream().collect(Collectors.toMap(Visitor::getId,
                    visitor -> visitor.getVisitCount() == null ? 0L : visitor.getVisitCount()));
        }
        return countVisits(visitors.stream().map(Visitor::getId).toList(), agentId);
    }

    private Map<Long, Long> countVisits(List<Long> visitorIds, Long agentId) {
        if (visitorIds.isEmpty()) {
            return Map.of();
        }
        List<VisitRepository.VisitorCount> counts = agentId == null
                ? visitRepository.countByVisitorIds(visitorIds)
                : visitRepository.countByVisitorIdsAndRegisteredById(visitorIds, agentId);
        return counts.stream().collect(Collectors.toMap(VisitRepository.VisitorCount::getVisitorId,
                VisitRepository.VisitorCount::getTotal));
    }

    private Set<Long> withIdentityDocument(List<Visitor> visitors) {
        List<Long> ids = visitors.stream().map(Visitor::getId).toList();
        return ids.isEmpty() ? Set.of() : new HashSet<>(identityDocumentRepository.findVisitorIdsIn(ids));
    }

    private static String documentDetails(Visitor visitor) {
        return "%s %s, %s".formatted(visitor.getIdentityDocumentType().getLabel(), visitor.getIdentityCardNumber(),
                visitor.getPhone());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Visitor require(Long id) {
        return visitorRepository.findById(id).orElseThrow(() -> ApiException.notFound("Visiteur", id));
    }
}
