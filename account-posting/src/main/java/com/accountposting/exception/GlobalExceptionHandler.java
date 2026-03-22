package com.accountposting.exception;

import com.accountposting.dto.retry.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name("NOT_FOUND")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        // Field / enum validation failures are bad input (400); other business rules are 422
        boolean isBadInput = "INVALID_ENUM_VALUE".equals(ex.getCode())
                || "VALIDATION_FAILED".equals(ex.getCode());
        HttpStatus status = isBadInput ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_ENTITY;
        log.warn("Business exception [{}] → {}: {}", ex.getCode(), status.value(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name(ex.getCode())
                        .message(ex.getMessage())
                        .build());
    }

    /**
     * Catches malformed JSON, unresolvable types, bad date formats, etc.
     * These are always the caller's fault → HTTP 400.
     * <p>
     * Note: after changing sourceName/requestType to String in the DTO,
     * enum deserialization no longer fails here. This handler remains as a
     * safety net for other unreadable payloads (e.g. invalid LocalDate format).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        log.warn("Unreadable HTTP message: {}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name("INVALID_REQUEST_BODY")
                        .message("Request body could not be parsed: " + detail)
                        .build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("uq_posting_config_request_type_order")) {
            message = "A config entry with the same request type and order already exists";
        } else {
            message = "Data integrity violation";
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name("DUPLICATE_CONFIG_ORDER")
                        .message(message)
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        List<ErrorResponse.FieldError> fieldErrors = result.getFieldErrors().stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name("VALIDATION_FAILED")
                        .message("Request validation failed")
                        .errors(fieldErrors)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name("INTERNAL_ERROR")
                        .message("An unexpected error occurred")
                        .build());
    }
}
