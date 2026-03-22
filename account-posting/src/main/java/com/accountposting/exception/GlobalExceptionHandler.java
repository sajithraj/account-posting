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
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name("NOT_FOUND")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        boolean isBadInput = "INVALID_ENUM_VALUE".equals(ex.getCode())
                || "VALIDATION_FAILED".equals(ex.getCode());
        HttpStatus status = isBadInput ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_ENTITY;
        log.warn("Business rule violation [{}] {}: {}", ex.getCode(), status.value(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name(ex.getCode())
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        log.warn("Malformed request body: {}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name("INVALID_REQUEST_BODY")
                        .message("Request body could not be parsed: " + detail)
                        .build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation: {}", cause);

        String errorCode;
        String message;
        if (cause != null && cause.contains("uq_posting_config_request_type_order")) {
            errorCode = "DUPLICATE_CONFIG_ORDER";
            message = "A config entry with the same request type and order already exists";
        } else if (cause != null && cause.contains("end_to_end_reference_id")) {
            errorCode = "DUPLICATE_E2E_REF";
            message = "A posting with this end-to-end reference ID already exists";
        } else {
            errorCode = "DATA_INTEGRITY_VIOLATION";
            message = "The request conflicts with existing data";
        }

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name(errorCode)
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
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .name("INTERNAL_ERROR")
                        .message("An unexpected error occurred")
                        .build());
    }
}
