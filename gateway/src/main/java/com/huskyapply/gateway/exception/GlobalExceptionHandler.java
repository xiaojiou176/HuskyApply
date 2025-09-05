package com.huskyapply.gateway.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler for the Gateway service.
 *
 * <p>This handler provides centralized exception handling with proper error responses, logging, and
 * user-friendly error messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Handle validation errors. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationExceptions(
      MethodArgumentNotValidException ex, WebRequest request) {

    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });

    ErrorResponse errorResponse =
        new ErrorResponse(
            "Validation Failed",
            "Request validation failed",
            errors,
            request.getDescription(false),
            LocalDateTime.now());

    logger.warn("Validation error on {}: {}", request.getDescription(false), errors);
    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
  }

  /** Handle authentication failures. */
  @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
  public ResponseEntity<ErrorResponse> handleAuthenticationException(
      Exception ex, WebRequest request) {

    ErrorResponse errorResponse =
        new ErrorResponse(
            "Authentication Failed",
            "Invalid credentials provided",
            null,
            request.getDescription(false),
            LocalDateTime.now());

    logger.warn("Authentication failed on {}: {}", request.getDescription(false), ex.getMessage());
    return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
  }

  /** Handle artifact not found exceptions. */
  @ExceptionHandler(ArtifactNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleArtifactNotFoundException(
      ArtifactNotFoundException ex, WebRequest request) {

    ErrorResponse errorResponse =
        new ErrorResponse(
            "Artifact Not Found",
            ex.getMessage(),
            Map.of("jobId", ex.getJobId().toString()),
            request.getDescription(false),
            LocalDateTime.now());

    logger.info("Artifact not found for job {}", ex.getJobId());
    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
  }

  /** Handle general exceptions. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {

    ErrorResponse errorResponse =
        new ErrorResponse(
            "Internal Server Error",
            "An unexpected error occurred",
            null,
            request.getDescription(false),
            LocalDateTime.now());

    logger.error("Unhandled exception on {}", request.getDescription(false), ex);
    return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /** Handle SSE-related exceptions. */
  @ExceptionHandler(IllegalStateException.class)
  public void handleSseException(IllegalStateException ex, WebRequest request) {
    if (ex.getMessage() != null && ex.getMessage().contains("ResponseBodyEmitter")) {
      logger.warn("SSE connection error on {}: {}", request.getDescription(false), ex.getMessage());
    } else {
      logger.error("Illegal state exception on {}", request.getDescription(false), ex);
    }
    // SSE errors are handled by completing the emitter, no response needed
  }

  /** Error response DTO. */
  public static class ErrorResponse {
    private final String error;
    private final String message;
    private final Map<String, String> details;
    private final String path;
    private final LocalDateTime timestamp;

    public ErrorResponse(
        String error,
        String message,
        Map<String, String> details,
        String path,
        LocalDateTime timestamp) {
      this.error = error;
      this.message = message;
      this.details = details;
      this.path = path;
      this.timestamp = timestamp;
    }

    // Getters
    public String getError() {
      return error;
    }

    public String getMessage() {
      return message;
    }

    public Map<String, String> getDetails() {
      return details;
    }

    public String getPath() {
      return path;
    }

    public LocalDateTime getTimestamp() {
      return timestamp;
    }
  }
}
