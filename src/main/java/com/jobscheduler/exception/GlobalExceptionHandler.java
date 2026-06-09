package com.jobscheduler.exception;

import com.jobscheduler.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST API errors.
 * Every unhandled exception flows here and returns a consistent ErrorResponse.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────
    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobNotFound(
            JobNotFoundException ex, HttpServletRequest request) {
        log.warn("Job not found: {}", ex.getJobId());
        return buildResponse(HttpStatus.NOT_FOUND, "Job Not Found",
                ex.getMessage(), request.getRequestURI());
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────
    @ExceptionHandler(JobAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> handleJobAlreadyRunning(
            JobAlreadyRunningException ex, HttpServletRequest request) {
        log.warn("Job already running: {}", ex.getJobId());
        return buildResponse(HttpStatus.CONFLICT, "Job Already Running",
                ex.getMessage(), request.getRequestURI());
    }

    // ── 400 Bad Request — business rules ──────────────────────────────────
    @ExceptionHandler(InvalidJobRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJobRequest(
            InvalidJobRequestException ex, HttpServletRequest request) {
        log.warn("Invalid job request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid Request",
                ex.getMessage(), request.getRequestURI());
    }

    // ── 400 Bad Request — @Valid failures ─────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Failed",
                message, request.getRequestURI());
    }

    // ── 401 Unauthorized — bad credentials ────────────────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials at: {}", request.getRequestURI());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Invalid username or password", request.getRequestURI());
    }

    // ── 403 Forbidden — insufficient role ─────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied at: {} — {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden",
                "You don't have permission to perform this action. Required role: ADMIN",
                request.getRequestURI());
    }

    // ── 500 Internal Server Error — catch-all ─────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI());
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String error, String message, String path) {
        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .build());
    }
}
