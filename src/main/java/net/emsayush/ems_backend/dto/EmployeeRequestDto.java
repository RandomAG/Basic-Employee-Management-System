package net.emsayush.ems_backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data Transfer Object used for <strong>inbound</strong> employee creation and
 * update requests (HTTP POST / PUT bodies).
 *
 * <p>Bean Validation annotations enforce business rules at the HTTP boundary
 * so that invalid data never reaches the service or persistence layers.
 *
 * <p>This class is intentionally decoupled from the {@link net.emsayush.ems_backend.entity.Employee}
 * JPA entity to follow the principle of Separation of Concerns – the
 * entity's structure can evolve independently of the API contract.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeRequestDto {

    /**
     * Employee's first name.
     * Must not be {@code null} or consist solely of whitespace.
     */
    @NotBlank(message = "First name is required and must not be blank")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    /**
     * Employee's last name.
     * Must not be {@code null} or consist solely of whitespace.
     */
    @NotBlank(message = "Last name is required and must not be blank")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    /**
     * Corporate e-mail address.
     * Must be a syntactically valid RFC-5321 address.
     * Uniqueness is enforced at the database layer and surfaced via the
     * global exception handler.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid e-mail address (e.g. john.doe@example.com)")
    @Size(max = 200, message = "Email must not exceed 200 characters")
    private String email;

    /** Business unit the employee belongs to. */
    @NotBlank(message = "Department is required")
    @Size(max = 150, message = "Department name must not exceed 150 characters")
    private String department;

    /** Job title within the department. */
    @NotBlank(message = "Designation is required")
    @Size(max = 150, message = "Designation must not exceed 150 characters")
    private String designation;

    /**
     * Gross monthly salary.
     * Must be a positive number greater than or equal to 1 (using {@code @Min}
     * works on {@link BigDecimal} after Spring's default conversion).
     */
    @NotNull(message = "Salary is required")
    @DecimalMin(value = "1.00", inclusive = true, message = "Salary must be at least 1.00")
    private BigDecimal salary;

    /** ISO-8601 date the employee joined (e.g. {@code 2024-01-15}). */
    @NotNull(message = "Date of joining is required")
    private LocalDate dateOfJoining;
}
