package com.sajith.payments.redesign.exception;

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.sajith.payments.redesign.dto.retry.ErrorResponseV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseV2> handleNotFound(ResourceNotFoundException ex) {
        log.error("Resource not found :: {} .", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseV2.builder()
                        .id(UUID.randomUUID().toString())
                        .name("NOT_FOUND")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseV2> handleBusiness(BusinessException ex) {
        boolean isBadInput = "INVALID_ENUM_VALUE".equals(ex.getCode())
                || "VALIDATION_FAILED".equals(ex.getCode())
                || "DUPLICATE_E2E_REF".equals(ex.getCode())
                || "DUPLICATE_CONFIG_ORDER".equals(ex.getCode());
        HttpStatus status = isBadInput ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_ENTITY;
        log.error("Business rule violation [{}] {}: {} .", ex.getCode(), status.value(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(ErrorResponseV2.builder()
                        .id(UUID.randomUUID().toString())
                        .name(ex.getCode())
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(InvalidDefinitionException.class)
    public ResponseEntity<ErrorResponseV2> handleInvalidDefinition(InvalidDefinitionException ex) {
        log.error("Jackson type definition error — likely missing serializer/deserializer :: {} .", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseV2.builder()
                        .id(UUID.randomUUID().toString())
                        .name("SERIALIZATION_CONFIG_ERROR")
                        .message("Server could not process the request due to a data type configuration issue")
                        .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseV2> handleNotReadable(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        log.error("Malformed request body :: {} .Error message :: {} .", detail, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseV2.builder()
                        .id(UUID.randomUUID().toString())
                        .name("INVALID_REQUEST_BODY")
                        .message("Request body could not be parsed: " + detail)
                        .build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseV2> handleDataIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.error("Data integrity violation :: {}. Error message :: {} .", cause, ex.getMessage());

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

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseV2.builder()
                        .id(UUID.randomUUID().toString())
                        .name(errorCode)
                        .message(message)
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseV2> handleValidation(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        List<ErrorResponseV2.FieldError> fieldErrors = result.getFieldErrors().stream()
                .map(fe -> ErrorResponseV2.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseV2.builder()
                        .id(UUID.randomUUID().toString())
                        .name("VALIDATION_FAILED")
                        .message("Request validation failed")
                        .errors(fieldErrors)
                        .build());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseV2> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed :: {} .", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponseV2.builder()
                        .id(UUID.randomUUID().toString())
                        .name("METHOD_NOT_ALLOWED")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseV2> handleGeneral(Exception ex) {
        log.error("Unhandled exception. Error message :: {} .", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseV2.builder()
                        .id(UUID.randomUUID().toString())
                        .name("INTERNAL_ERROR")
                        .message("An unexpected error occurred")
                        .build());
    }
}
