// src/main/java/com/coresync/hrms/backend/exception/GlobalExceptionHandler.java
package com.coresync.hrms.backend.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new LinkedHashMap<>(Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "status", status.value(),
            "error", error,
            "message", message
        )));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fields = ex.getBindingResult().getFieldErrors().stream()
            .map(this::toFieldError)
            .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "VALIDATION_FAILED");
        body.put("message", "Please correct the highlighted fields and try again.");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(LocationVerificationException.class)
    public ResponseEntity<Map<String, Object>> handleLocationException(LocationVerificationException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new LinkedHashMap<>(Map.of(
            "timestamp",       LocalDateTime.now().toString(),
            "status",          403,
            "error",           "LOCATION_VERIFICATION_FAILED",
            "message",         ex.getMessage(),
            "distanceMeters",  ex.getDistanceMeters(),
            "allowedMeters",   ex.getAllowedRadiusMeters()
        )));
    }

    @ExceptionHandler(OpenSessionException.class)
    public ResponseEntity<Map<String, Object>> handleOpenSessionException(OpenSessionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new LinkedHashMap<>(Map.of(
            "timestamp",      LocalDateTime.now().toString(),
            "status",         409,
            "error",          "OPEN_SESSION_EXISTS",
            "message",        ex.getMessage(),
            "openSessionId",  ex.getOpenSessionId()
        )));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", ex.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(InsufficientBalanceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new LinkedHashMap<>(Map.of(
            "timestamp",     LocalDateTime.now().toString(),
            "status",        409,
            "error",         "INSUFFICIENT_LEAVE_BALANCE",
            "message",       ex.getMessage(),
            "available",     ex.getAvailable(),
            "requested",     ex.getRequested(),
            "leaveTypeCode", ex.getLeaveTypeCode()
        )));
    }

    private Map<String, String> toFieldError(FieldError error) {
        return Map.of(
            "field", error.getField(),
            "message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()
        );
    }
}
