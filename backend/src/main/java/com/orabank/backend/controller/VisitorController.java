package com.orabank.backend.controller;

import com.orabank.backend.dto.PageResponse;
import com.orabank.backend.dto.VisitorCreateRequest;
import com.orabank.backend.dto.VisitorResponse;
import com.orabank.backend.dto.VisitorUpdateRequest;
import com.orabank.backend.entity.IdentityDocumentType;
import com.orabank.backend.entity.VisitorIdentityDocument;
import com.orabank.backend.service.VisitorIdentityDocumentService;
import com.orabank.backend.service.VisitorService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/visitors")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTION')")
public class VisitorController {

    private final VisitorService visitorService;
    private final VisitorIdentityDocumentService identityDocumentService;

    @GetMapping
    public PageResponse<VisitorResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) IdentityDocumentType identityDocumentType,
            @RequestParam(required = false) String identityCardNumber,
            @PageableDefault(size = 20, sort = {"lastName", "firstName"}) Pageable pageable) {
        return visitorService.search(q, identityDocumentType, identityCardNumber, pageable);
    }

    @GetMapping("/lookup")
    public List<VisitorResponse> lookup(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) IdentityDocumentType identityDocumentType,
            @RequestParam(required = false) String identityCardNumber) {
        return visitorService.lookup(q, identityDocumentType, identityCardNumber);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisitorResponse create(@Valid @RequestBody VisitorCreateRequest request) {
        return visitorService.create(request);
    }

    @PutMapping("/{id}")
    public VisitorResponse update(@PathVariable Long id, @Valid @RequestBody VisitorUpdateRequest request) {
        return visitorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        visitorService.delete(id);
    }

    @PutMapping(value = "/{id}/identity-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadIdentityDocument(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        identityDocumentService.storeForOwnVisitor(id, file);
    }

    @GetMapping("/{id}/identity-document")
    public ResponseEntity<byte[]> identityDocument(@PathVariable Long id) {
        VisitorIdentityDocument document = identityDocumentService.load(id);
        MediaType mediaType = MediaType.parseMediaType(document.getContentType());
        String filename = "piece-identite-%d.%s".formatted(id, mediaType.getSubtype().replace("jpeg", "jpg"));
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(document.getContent());
    }
}
