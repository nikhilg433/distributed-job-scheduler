package com.jobscheduler.exception;

import com.jobscheduler.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST API errors.
 *
 * Every unhandled exception flows here and is converted into a consistent
 * ErrorResponse JSON body. Controllers never need try-catch blocks.
 *
 * Spring's @RestControllerAdvice intercepts exceptions from all @RestController classes.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─────────────────────────────────────────────────────────────────────
    // 404 Not Found
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobNotFound(
            JobNotFoundException ex, HttpServletRequest request) {
        log.warn("Job not found: {}", ex.getJobId());
        return buildResponse(HttpStatus.NOT_FOUND, "Job Not Found", ex.getMessage(), request.getRequestURI());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 409 Conflict
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(JobAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> handleJobAlreadyRunning(
            JobAlreadyRunningException ex, HttpServletRequest request) {
        log.warn("Job already running: {}", ex.getJobId());
        return buildResponse(HttpStatus.CONFLICT, "Job Already Running", ex.getMessage(), request.getRequestURI());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 400 Bad Request — business rule violations
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidJobRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJobRequest(
            InvalidJobRequestException ex, HttpServletRequest request) {
        log.warn("Invalid job request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid Request", ex.getMessage(), request.getRequestURI());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 400 Bad Request — @Valid bean validation failures
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        // Collect all field-level validation messages into one string
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Failed", message, request.getRequestURI());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 500 Internal Server Error — catch-all
    // ─────────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper — builds the standardized ErrorResponse
    // ─────────────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String error, String message, String path) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
