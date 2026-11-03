package com.orabank.backend.controller;

import com.orabank.backend.dto.EmployeeCreateRequest;
import com.orabank.backend.dto.EmployeeImportResult;
import com.orabank.backend.dto.EmployeeResponse;
import com.orabank.backend.dto.EmployeeRoleUpdateRequest;
import com.orabank.backend.dto.EmployeeSummaryResponse;
import com.orabank.backend.dto.EmployeeUpdateRequest;
import com.orabank.backend.dto.PageResponse;
import com.orabank.backend.entity.Direction;
import com.orabank.backend.entity.Role;
import com.orabank.backend.service.EmployeeImportService;
import com.orabank.backend.service.EmployeeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeImportService employeeImportService;

    @GetMapping
    public PageResponse<EmployeeResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Direction direction,
            @PageableDefault(size = 20, sort = {"lastName", "firstName"}) Pageable pageable) {
        return employeeService.search(q, role, direction, pageable);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Direction direction) {
        return employeeService.export(q, role, direction).toResponse();
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTION')")
    public List<EmployeeSummaryResponse> lookup(@RequestParam(required = false) String q) {
        return employeeService.lookup(q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody EmployeeCreateRequest request) {
        return employeeService.create(request);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeImportResult importEmployees(@RequestParam("file") MultipartFile file) {
        return employeeImportService.importFile(file);
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeUpdateRequest request) {
        return employeeService.update(id, request);
    }

    @PatchMapping("/{id}/role")
    public EmployeeResponse changeRole(@PathVariable Long id, @Valid @RequestBody EmployeeRoleUpdateRequest request) {
        return employeeService.changeRole(id, request.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        employeeService.delete(id);
    }

    @PostMapping("/{id}/password-setup-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendPasswordSetupLink(@PathVariable Long id) {
        employeeService.sendPasswordSetupLink(id);
    }
}
