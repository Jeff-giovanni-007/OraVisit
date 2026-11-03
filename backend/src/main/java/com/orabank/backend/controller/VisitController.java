package com.orabank.backend.controller;

import com.orabank.backend.dto.PageResponse;
import com.orabank.backend.dto.VisitCancelRequest;
import com.orabank.backend.dto.VisitCreateRequest;
import com.orabank.backend.dto.VisitResponse;
import com.orabank.backend.dto.VisitUpdateRequest;
import com.orabank.backend.entity.Direction;
import com.orabank.backend.entity.VisitStatus;
import com.orabank.backend.service.VisitService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTION')")
public class VisitController {

    private final VisitService visitService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VisitResponse create(
            @Valid @RequestPart("visit") VisitCreateRequest request,
            @RequestPart(value = "identityDocument", required = false) MultipartFile identityDocument) {
        return visitService.create(request, identityDocument);
    }

    @GetMapping
    public PageResponse<VisitResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) VisitStatus status,
            @RequestParam(required = false) Direction direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20, sort = "checkInAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return visitService.search(q, status, direction, dateFrom, dateTo, pageable);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) VisitStatus status,
            @RequestParam(required = false) Direction direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return visitService.export(q, status, direction, dateFrom, dateTo).toResponse();
    }

    @PutMapping("/{id}")
    public VisitResponse update(@PathVariable Long id, @Valid @RequestBody VisitUpdateRequest request) {
        return visitService.update(id, request);
    }

    @PatchMapping("/{id}/checkout")
    public VisitResponse checkout(@PathVariable Long id) {
        return visitService.checkout(id);
    }

    @PatchMapping("/{id}/cancel")
    public VisitResponse cancel(@PathVariable Long id, @Valid @RequestBody VisitCancelRequest request) {
        return visitService.cancel(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        visitService.delete(id);
    }
}
