package com.orabank.backend.service;

import com.orabank.backend.dto.EmployeeCreateRequest;
import com.orabank.backend.dto.EmployeeResponse;
import com.orabank.backend.dto.EmployeeSummaryResponse;
import com.orabank.backend.dto.EmployeeUpdateRequest;
import com.orabank.backend.dto.PageResponse;
import com.orabank.backend.entity.AuditAction;
import com.orabank.backend.entity.AuditTarget;
import com.orabank.backend.entity.Direction;
import com.orabank.backend.entity.Employee;
import com.orabank.backend.entity.Role;
import com.orabank.backend.exception.ApiException;
import com.orabank.backend.export.ExcelExporter;
import com.orabank.backend.export.ExportedFile;
import com.orabank.backend.repository.EmployeeRepository;
import com.orabank.backend.repository.VisitRepository;
import com.orabank.backend.repository.VisitorRepository;
import com.orabank.backend.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final int LOOKUP_LIMIT = 20;
    private static final List<String> EXPORT_HEADERS = List.of(
            "Matricule", "Prénom", "Nom", "Email", "Téléphone", "Rôle", "Direction", "Poste",
            "Mot de passe", "Créé le", "Modifié le");

    private final EmployeeRepository employeeRepository;
    private final VisitRepository visitRepository;
    private final VisitorRepository visitorRepository;
    private final PasswordSetupService passwordSetupService;
    private final ExcelExporter excelExporter;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> search(String q, Role role, Direction direction, Pageable pageable) {
        return PageResponse.of(employeeRepository.findAll(filters(q, role, direction), Pageables.stable(pageable)),
                EmployeeResponse::from);
    }

    @Transactional(readOnly = true)
    public List<EmployeeSummaryResponse> lookup(String q) {
        Pageable pageable = PageRequest.of(0, LOOKUP_LIMIT, Pageables.stable(Sort.by(Employee::getLastName, Employee::getFirstName)));
        return employeeRepository.findAll(filters(q, null, null), pageable)
                .map(EmployeeSummaryResponse::from)
                .getContent();
    }

    @Transactional(readOnly = true)
    public ExportedFile export(String q, Role role, Direction direction) {
        List<List<Object>> rows = employeeRepository
                .findAll(filters(q, role, direction), Pageables.stable(Sort.by(Employee::getLastName, Employee::getFirstName)))
                .stream()
                .map(employee -> Arrays.<Object>asList(
                        employee.getMatricule(),
                        employee.getFirstName(),
                        employee.getLastName(),
                        employee.getEmail(),
                        employee.getPhone(),
                        employee.getRole().getLabel(),
                        employee.getDirection().getLabel(),
                        employee.getPoste(),
                        passwordStatus(employee),
                        employee.getCreatedAt(),
                        employee.getUpdatedAt()))
                .toList();

        ExportedFile file = excelExporter.write("employes", EXPORT_HEADERS, rows);
        log.info("Export Excel de {} employe(s)", rows.size());
        auditService.record(AuditAction.EMPLOYEES_EXPORTED, AuditTarget.FILE,
                "%d employé(s)".formatted(rows.size()));
        return file;
    }

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        String matricule = request.matricule().trim();
        String email = request.email().trim();
        if (employeeRepository.existsByMatriculeIgnoreCase(matricule)) {
            throw ApiException.conflict("MATRICULE_ALREADY_USED", "Ce matricule est déjà utilisé.");
        }
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_USED", "Cette adresse email est déjà utilisée.");
        }

        Employee employee = employeeRepository.save(Employee.builder()
                .matricule(matricule)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .phone(request.phone().trim())
                .role(request.role())
                .direction(request.direction())
                .poste(request.poste().trim())
                .build());

        if (employee.getRole().hasApplicationAccess()) {
            passwordSetupService.issueLink(employee);
        }
        log.info("Employe cree : id={}, matricule={}, role={}",
                employee.getId(), employee.getMatricule(), employee.getRole());
        auditService.record(AuditAction.EMPLOYEE_CREATED, AuditTarget.EMPLOYEE, employee.getId(), label(employee),
                "Rôle %s, %s, %s".formatted(employee.getRole().getLabel(), employee.getPoste(),
                        employee.getDirection().getLabel()));
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = require(id);
        String matricule = request.matricule().trim();
        String email = request.email().trim();
        boolean matriculeChanged = !employee.getMatricule().equalsIgnoreCase(matricule);
        boolean emailChanged = !employee.getEmail().equalsIgnoreCase(email);

        if (matriculeChanged && employee.getId().equals(CurrentUser.require().getId())) {
            throw ApiException.conflict("CANNOT_CHANGE_OWN_MATRICULE",
                    "Vous ne pouvez pas modifier votre propre matricule.");
        }
        if (employeeRepository.existsByMatriculeIgnoreCaseAndIdNot(matricule, id)) {
            throw ApiException.conflict("MATRICULE_ALREADY_USED", "Ce matricule est déjà utilisé.");
        }
        if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw ApiException.conflict("EMAIL_ALREADY_USED", "Cette adresse email est déjà utilisée.");
        }

        employee.setMatricule(matricule);
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setEmail(email);
        employee.setPhone(request.phone().trim());
        employee.setDirection(request.direction());
        employee.setPoste(request.poste().trim());
        employee = employeeRepository.saveAndFlush(employee);

        if ((matriculeChanged || emailChanged) && employee.getRole().hasApplicationAccess()
                && !employee.isPasswordSet()) {
            passwordSetupService.issueLink(employee);
        }
        log.info("Employe modifie : id={}, matricule={}", id, employee.getMatricule());
        auditService.record(AuditAction.EMPLOYEE_UPDATED, AuditTarget.EMPLOYEE, id, label(employee),
                "%s, %s, %s, %s".formatted(employee.getEmail(), employee.getPhone(), employee.getPoste(),
                        employee.getDirection().getLabel()));
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public EmployeeResponse changeRole(Long id, Role role) {
        Employee employee = require(id);
        Role previous = employee.getRole();
        if (previous == role) {
            return EmployeeResponse.from(employee);
        }
        if (employee.getId().equals(CurrentUser.require().getId())) {
            throw ApiException.conflict("CANNOT_CHANGE_OWN_ROLE", "Vous ne pouvez pas modifier votre propre rôle.");
        }

        employee.setRole(role);
        if (!previous.hasApplicationAccess()) {
            employee.setPasswordHash(null);
            passwordSetupService.issueLink(employee);
        } else if (!role.hasApplicationAccess()) {
            employee.setPasswordHash(null);
        }

        log.info("Role modifie : id={}, {} -> {}", id, previous, role);
        auditService.record(AuditAction.EMPLOYEE_ROLE_CHANGED, AuditTarget.EMPLOYEE, id, label(employee),
                "%s → %s".formatted(previous.getLabel(), role.getLabel()));
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public void sendPasswordSetupLink(Long id) {
        Employee employee = require(id);
        if (!employee.getRole().hasApplicationAccess()) {
            throw ApiException.conflict("EMPLOYEE_WITHOUT_ACCESS",
                    "Cet employé n'a pas accès à l'application : attribuez-lui d'abord un rôle.");
        }
        passwordSetupService.issueLink(employee);
    }

    @Transactional
    public void delete(Long id) {
        Employee employee = require(id);
        if (employee.getId().equals(CurrentUser.require().getId())) {
            throw ApiException.conflict("CANNOT_DELETE_SELF", "Vous ne pouvez pas supprimer votre propre compte.");
        }
        long visits = visitRepository.countByHostEmployeeIdOrRegisteredById(id, id);
        if (visits > 0) {
            String message = "Cet employé est lié à %d visite(s) et ne peut pas être supprimé.".formatted(visits);
            if (employee.getRole().hasApplicationAccess()) {
                message += " Pour lui retirer l'accès à l'application, attribuez-lui le rôle Employé.";
            }
            throw ApiException.conflict("EMPLOYEE_HAS_VISITS", message);
        }
        int visitors = visitorRepository.clearCreatedBy(id);
        employeeRepository.delete(employee);
        log.info("Employe supprime : id={}, matricule={}, fiches visiteurs detachees={}",
                id, employee.getMatricule(), visitors);
        auditService.record(AuditAction.EMPLOYEE_DELETED, AuditTarget.EMPLOYEE, id, label(employee),
                "Rôle %s, %d fiche(s) visiteur détachée(s)".formatted(employee.getRole().getLabel(), visitors));
    }

    private Specification<Employee> filters(String q, Role role, Direction direction) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                for (String term : q.trim().toLowerCase().split("\\s+")) {
                    String pattern = "%" + term + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("matricule")), pattern),
                            cb.like(cb.lower(root.get("firstName")), pattern),
                            cb.like(cb.lower(root.get("lastName")), pattern),
                            cb.like(cb.lower(root.get("email")), pattern),
                            cb.like(cb.lower(root.get("poste")), pattern)));
                }
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (direction != null) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String label(Employee employee) {
        return "%s (%s)".formatted(employee.getFullName(), employee.getMatricule());
    }

    private String passwordStatus(Employee employee) {
        if (!employee.getRole().hasApplicationAccess()) {
            return "Sans accès";
        }
        return employee.isPasswordSet() ? "Défini" : "En attente";
    }

    private Employee require(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Employé", id));
    }
}
