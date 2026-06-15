package net.emsayush.ems_backend.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application-wide exception handler that intercepts exceptions thrown from
 * any {@code @RestController} and converts them into clean, structured JSON
 * error payloads instead of exposing raw stack traces.
 *
 * <p>Uses {@code @RestControllerAdvice} (= {@code @ControllerAdvice} +
 * {@code @ResponseBody}) so every handler method automatically serialises
 * its return value as JSON.
 *
 * <h2>Handled exceptions</h2>
 * <ol>
 *   <li>{@link ResourceNotFoundException}         → 404 Not Found</li>
 *   <li>{@link MethodArgumentNotValidException}   → 400 Bad Request (Bean Validation field errors)</li>
 *   <li>{@link ConstraintViolationException}      → 400 Bad Request (javax/jakarta path/query param violations)</li>
 *   <li>{@link DataIntegrityViolationException}   → 409 Conflict   (e.g. duplicate email)</li>
 *   <li>{@link Exception}                         → 500 Internal Server Error (catch-all safety net)</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    /**
     * Handles the case where a requested resource (e.g., Employee with a given ID)
     * does not exist in the database.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                ex.getMessage(),
                request.getDescription(false)
        );
    }

    // ── 400 Bad Request – @Valid / @Validated field-level errors ──────────────

    /**
     * Handles validation failures triggered by {@code @Valid} on request body DTOs.
     * Returns a map of field → error message pairs so the client knows exactly
     * which fields are invalid.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {

        // Collect all field-level constraint violations into a readable map
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName    = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp",  LocalDateTime.now());
        body.put("status",     HttpStatus.BAD_REQUEST.value());
        body.put("error",      "Validation Failed");
        body.put("message",    "One or more fields failed validation. See 'fieldErrors' for details.");
        body.put("fieldErrors", fieldErrors);
        body.put("path",       request.getDescription(false));

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // ── 400 Bad Request – path/query parameter constraint violations ──────────

    /**
     * Handles violations of constraints applied directly on method parameters
     * (e.g., {@code @RequestParam @Min(1) int page}).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Constraint Violation",
                ex.getMessage(),
                request.getDescription(false)
        );
    }

    // ── 409 Conflict – Database unique-constraint violation ───────────────────

    /**
     * Handles database-level integrity violations, the most common being a
     * duplicate e-mail address breaking the unique constraint on the
     * {@code employees} table.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, WebRequest request) {

        // Extract a meaningful root-cause message, avoiding raw Hibernate internals
        String message = "A database constraint was violated. " +
                "This is most likely caused by a duplicate value in a unique field (e.g. email).";

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "Data Integrity Violation",
                message,
                request.getDescription(false)
        );
    }

    // ── 500 Internal Server Error – catch-all ────────────────────────────────

    /**
     * Safety-net handler for any unhandled exception. Prevents raw stack traces
     * from leaking to the API consumer.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please contact the system administrator.",
                request.getDescription(false)
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a standardised error response body with consistent field names
     * across all error types.
     *
     * @param status  HTTP status to return
     * @param error   short error category label
     * @param message detailed human-readable explanation
     * @param path    the URI path that triggered the error
     * @return a {@link ResponseEntity} wrapping the error payload
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String error, String message, String path) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status",    status.value());
        body.put("error",     error);
        body.put("message",   message);
        body.put("path",      path);

        return new ResponseEntity<>(body, status);
    }
}
