package com.orabank.backend.exception;

import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient List<FieldError> errors;

    public ApiException(HttpStatus status, String code, String message, List<FieldError> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public record FieldError(String field, String message) {
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message, List.of());
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, List.of());
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message, List.of());
    }

    public static ApiException notFound(String resource, Object id) {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "%s introuvable (id %s).".formatted(resource, id), List.of());
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, List.of());
    }

    public static ApiException businessRule(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message, List.of());
    }
}
