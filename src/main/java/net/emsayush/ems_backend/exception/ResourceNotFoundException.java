package net.emsayush.ems_backend.exception;

/**
 * Thrown when a requested {@link net.emsayush.ems_backend.entity.Employee}
 * cannot be found in the database by the given identifier.
 *
 * <p>This is an unchecked exception (extends {@link RuntimeException}) so
 * callers are not forced to declare it in a {@code throws} clause, keeping
 * service method signatures clean. The
 * {@link net.emsayush.ems_backend.exception.GlobalExceptionHandler} intercepts
 * it and returns a structured 404 JSON response.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates a new exception with a descriptive message that identifies the
     * missing resource type and the lookup key that was used.
     *
     * @param resourceName human-readable name of the resource type (e.g. "Employee")
     * @param fieldName    the field used for the lookup (e.g. "id")
     * @param fieldValue   the value that was not found (e.g. {@code 42})
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s : '%s'", resourceName, fieldName, fieldValue));
    }
}
