package com.orabank.backend.service;

import com.orabank.backend.dto.EmployeeCreateRequest;
import com.orabank.backend.dto.EmployeeImportResult;
import com.orabank.backend.entity.AuditAction;
import com.orabank.backend.entity.AuditTarget;
import com.orabank.backend.entity.Direction;
import com.orabank.backend.entity.Role;
import com.orabank.backend.exception.ApiException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeImportService {

    private static final Map<String, List<String>> COLUMNS = Map.of(
            "matricule", List.of("matricule"),
            "firstName", List.of("firstName", "Prénom"),
            "lastName", List.of("lastName", "Nom"),
            "email", List.of("email", "e-mail"),
            "phone", List.of("phone", "Téléphone"),
            "role", List.of("role", "Rôle"),
            "direction", List.of("direction"),
            "poste", List.of("poste", "Fonction", "Intitulé du poste"));
    private static final List<String> REQUIRED_COLUMNS =
            List.of("matricule", "firstName", "lastName", "email", "phone", "direction", "poste");

    private final EmployeeService employeeService;
    private final AuditService auditService;
    private final Validator validator;
    private final DataFormatter formatter = new DataFormatter();

    public EmployeeImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("FILE_REQUIRED", "Fichier Excel obligatoire.");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw ApiException.badRequest("INVALID_FILE", "Formats acceptés : .xlsx ou .xls.");
        }
        int created = 0, skipped = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) throw ApiException.badRequest("EMPTY_SHEET", "Feuille Excel vide.");
            Map<String, Integer> columns = columnIndexes(headerRow);

            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, columns)) continue;
                int line = r + 1;
                try {
                    String matricule = cell(row, columns, "matricule");
                    String firstName = cell(row, columns, "firstName");
                    String lastName = cell(row, columns, "lastName");
                    String email = cell(row, columns, "email");
                    String phone = cell(row, columns, "phone");
                    Role role = parseRole(cell(row, columns, "role"));
                    Direction direction = parseDirection(cell(row, columns, "direction"));
                    String poste = cell(row, columns, "poste");
                    EmployeeCreateRequest employee = new EmployeeCreateRequest(
                            matricule, firstName, lastName, email,
                            phone, role, direction, poste);
                    requireValid(employee);
                    employeeService.create(employee);
                    created++;
                } catch (ApiException ex) {
                    if ("MATRICULE_ALREADY_USED".equals(ex.getCode()) || "EMAIL_ALREADY_USED".equals(ex.getCode())) {
                        skipped++;
                        errors.add("Ligne " + line + " : ignorée (" + ex.getMessage() + ")");
                    } else {
                        failed++;
                        errors.add("Ligne " + line + " : " + ex.getMessage());
                    }
                } catch (Exception ex) {
                    failed++;
                    errors.add("Ligne " + line + " : " + ex.getMessage());
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Import Excel echoue", e);
            throw ApiException.badRequest("IMPORT_FAILED", "Lecture du fichier impossible : " + e.getMessage());
        }
        auditService.record(AuditAction.EMPLOYEES_IMPORTED, AuditTarget.FILE, null, file.getOriginalFilename(),
                "%d créé(s), %d ignoré(s), %d échec(s)".formatted(created, skipped, failed));
        return new EmployeeImportResult(created, skipped, failed, errors);
    }

    private void requireValid(EmployeeCreateRequest employee) {
        Set<ConstraintViolation<EmployeeCreateRequest>> violations = validator.validate(employee);
        if (!violations.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining(" ")));
        }
    }

    private Map<String, Integer> columnIndexes(Row headerRow) {
        Map<String, Integer> indexes = new HashMap<>();
        for (Cell headerCell : headerRow) {
            String title = normalize(formatter.formatCellValue(headerCell));
            COLUMNS.forEach((column, titles) -> {
                if (titles.stream().anyMatch(accepted -> normalize(accepted).equals(title))) {
                    indexes.putIfAbsent(column, headerCell.getColumnIndex());
                }
            });
        }
        List<String> missing = REQUIRED_COLUMNS.stream()
                .filter(column -> !indexes.containsKey(column))
                .map(column -> String.join(" ou ", COLUMNS.get(column)))
                .toList();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("IMPORT_INVALID_HEADER",
                    "Colonnes manquantes dans le fichier : " + String.join(", ", missing) + ".");
        }
        return indexes;
    }

    private static String normalize(String title) {
        return Normalizer.normalize(title.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_'’-]", "");
    }

    private boolean isBlankRow(Row row, Map<String, Integer> columns) {
        return columns.keySet().stream().allMatch(column -> cell(row, columns, column).isBlank());
    }

    private String cell(Row row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        Cell c = index == null ? null : row.getCell(index);
        return c == null ? "" : formatter.formatCellValue(c).trim();
    }

    private Role parseRole(String raw) {
        if (raw == null || raw.isBlank()) return Role.DEFAULT;
        String value = normalize(raw.replaceAll("\\(.*\\)", ""));
        for (Role role : Role.values()) {
            if (normalize(role.name()).equals(value) || normalize(role.getLabel()).equals(value)) return role;
        }
        throw ApiException.badRequest("INVALID_ROLE", "Rôle invalide : " + raw + ".");
    }

    private Direction parseDirection(String raw) {
        if (raw == null || raw.isBlank())
            throw ApiException.badRequest("INVALID_DIRECTION", "La direction est obligatoire.");
        String value = normalize(raw);
        for (Direction direction : Direction.values()) {
            if (normalize(direction.name()).equals(value) || normalize(direction.getLabel()).equals(value)) {
                return direction;
            }
        }
        throw ApiException.badRequest("INVALID_DIRECTION", "Direction inconnue : " + raw + ".");
    }
}
