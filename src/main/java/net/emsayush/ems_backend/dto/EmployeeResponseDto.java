package net.emsayush.ems_backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data Transfer Object used for <strong>outbound</strong> employee responses.
 *
 * <p>This DTO is what the REST API serialises to JSON. It intentionally
 * includes only the fields that the client needs to see, hiding any
 * future internal implementation details of the JPA entity.
 *
 * <p>The {@code createdAt} / {@code updatedAt} audit timestamps are added here
 * as a placeholder for when auditing is enabled – they will be {@code null}
 * until then and are omitted from the JSON output via
 * {@code spring.jackson.default-property-inclusion=non_null} (optional).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponseDto {

    /** Database-generated surrogate primary key. */
    private Long id;

    private String firstName;
    private String lastName;

    /** Full name convenience field derived server-side. */
    private String fullName;

    private String email;
    private String department;
    private String designation;
    private BigDecimal salary;
    private LocalDate dateOfJoining;
}
