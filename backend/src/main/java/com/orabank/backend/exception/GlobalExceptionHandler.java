package com.orabank.backend.exception;

import com.orabank.backend.exception.ApiException.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
        log.info("Erreur metier {} sur {} : {}", exception.getCode(), request.getRequestURI(), exception.getMessage());
        return problem(exception.getStatus(), exception.getCode(), exception.getMessage(), exception.getErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .sorted((a, b) -> a.field().compareTo(b.field()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Certains champs sont invalides.", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody() {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Le corps de la requête est invalide ou contient des champs non autorisés.", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "La valeur du paramètre « %s » est invalide.".formatted(exception.getName()),
                List.of(new FieldError(exception.getName(), "Valeur non reconnue.")));
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSort(PropertyReferenceException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Le tri demandé sur « %s » n'est pas pris en charge.".formatted(exception.getPropertyName()),
                List.of(new FieldError("sort", "Propriété de tri inconnue.")));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ProblemDetail handleMissingParameter() {
        return problem(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Un fichier ou un paramètre obligatoire est manquant.", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleFileTooLarge() {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE",
                "Le fichier dépasse la taille maximale autorisée (5 Mo).", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleUnknownRoute() {
        return problem(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "Cette route n'existe pas.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed() {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Cette méthode n'est pas autorisée sur cette route.", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleUnsupportedMediaType() {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Le format de la requête n'est pas accepté sur cette route.", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(HttpServletRequest request) {
        log.info("Acces refuse sur {}", request.getRequestURI());
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Vous n'êtes pas autorisé à effectuer cette action.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        String cause = String.valueOf(exception.getMostSpecificCause().getMessage()).toLowerCase();
        log.warn("Violation de contrainte sur {} : {}", request.getRequestURI(), cause);

        if (cause.contains("ux_visits_badge_active")) {
            return problem(HttpStatus.CONFLICT, "BADGE_ALREADY_IN_USE",
                    "Ce numéro de badge est déjà attribué à une visite en cours.", List.of());
        }
        if (cause.contains("ux_visits_visitor_active")) {
            return problem(HttpStatus.CONFLICT, "VISITOR_ALREADY_CHECKED_IN",
                    "Ce visiteur a déjà une visite en cours.", List.of());
        }
        if (cause.contains("uk_visitors_identity_document")) {
            return problem(HttpStatus.CONFLICT, "VISITOR_DOCUMENT_ALREADY_USED",
                    "Un visiteur existe déjà avec ce type et ce numéro de pièce.", List.of());
        }
        if (cause.contains("uk_employees_matricule")) {
            return problem(HttpStatus.CONFLICT, "MATRICULE_ALREADY_USED",
                    "Ce matricule est déjà utilisé.", List.of());
        }
        if (cause.contains("uk_employees_email")) {
            return problem(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED",
                    "Cette adresse email est déjà utilisée.", List.of());
        }
        return problem(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "L'opération entre en conflit avec des données existantes.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Erreur inattendue sur {}", request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Une erreur interne est survenue. Veuillez réessayer.", List.of());
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}
