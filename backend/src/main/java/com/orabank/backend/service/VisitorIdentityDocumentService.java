package com.orabank.backend.service;

import com.orabank.backend.entity.AuditAction;
import com.orabank.backend.entity.AuditTarget;
import com.orabank.backend.entity.Visitor;
import com.orabank.backend.entity.VisitorIdentityDocument;
import com.orabank.backend.exception.ApiException;
import com.orabank.backend.repository.VisitorIdentityDocumentRepository;
import com.orabank.backend.repository.VisitorRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorIdentityDocumentService {

    private final VisitorRepository visitorRepository;
    private final VisitorIdentityDocumentRepository documentRepository;
    private final VisitorService visitorService;
    private final AuditService auditService;

    @Transactional
    public void storeForOwnVisitor(Long visitorId, MultipartFile file) {
        visitorService.requireAccess(visitorId);
        store(visitorId, file);
    }

    @Transactional
    public void store(Long visitorId, MultipartFile file) {
        Visitor visitor = visitorRepository.findById(visitorId)
                .orElseThrow(() -> ApiException.notFound("Visiteur", visitorId));
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("FILE_REQUIRED", "Le fichier de la pièce d'identité est obligatoire.");
        }

        byte[] content = read(file);
        String contentType = detectContentType(content).orElseThrow(() -> ApiException.badRequest(
                "UNSUPPORTED_FILE_TYPE", "Formats acceptés pour la pièce d'identité : JPEG, PNG, WEBP ou PDF."));

        VisitorIdentityDocument document = documentRepository.findById(visitorId)
                .orElseGet(() -> new VisitorIdentityDocument(visitorId));
        document.setContentType(contentType);
        document.setContent(content);
        documentRepository.save(document);
        log.info("Piece d'identite enregistree : visiteur={}, type={}, taille={} octets",
                visitorId, contentType, content.length);
        auditService.record(AuditAction.IDENTITY_DOCUMENT_STORED, AuditTarget.IDENTITY_DOCUMENT, visitorId,
                visitor.getFullName(), "%s, %d octets".formatted(contentType, content.length));
    }

    @Transactional(readOnly = true)
    public VisitorIdentityDocument load(Long visitorId) {
        visitorService.requireAccess(visitorId);
        VisitorIdentityDocument document = documentRepository.findById(visitorId)
                .orElseThrow(() -> ApiException.notFound("Pièce d'identité du visiteur", visitorId));
        String visitor = visitorRepository.findById(visitorId).map(Visitor::getFullName).orElse(null);
        auditService.record(AuditAction.IDENTITY_DOCUMENT_VIEWED, AuditTarget.IDENTITY_DOCUMENT, visitorId, visitor,
                document.getContentType());
        return document;
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Lecture du fichier envoye impossible", exception);
        }
    }

    private static Optional<String> detectContentType(byte[] content) {
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(content, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (startsWith(content, 'R', 'I', 'F', 'F') && content.length >= 12
                && Arrays.equals(content, 8, 12, new byte[] {'W', 'E', 'B', 'P'}, 0, 4)) {
            return Optional.of("image/webp");
        }
        if (startsWith(content, '%', 'P', 'D', 'F', '-')) {
            return Optional.of("application/pdf");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
