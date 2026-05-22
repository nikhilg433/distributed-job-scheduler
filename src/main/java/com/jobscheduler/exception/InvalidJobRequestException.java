package com.jobscheduler.exception;

/**
 * Thrown for invalid job creation requests that pass bean validation
 * but fail business rule validation in the service layer.
 *
 * Example: providing both cronExpression and scheduledAt simultaneously,
 * or providing neither.
 *
 * Maps to HTTP 400 Bad Request in GlobalExceptionHandler.
 */
public class InvalidJobRequestException extends RuntimeException {

    public InvalidJobRequestException(String message) {
        super(message);
    }
}
