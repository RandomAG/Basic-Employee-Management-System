package net.emsayush.ems_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA Entity representing a single Employee record persisted in the
 * {@code employees} table.
 *
 * <p>Lombok annotations eliminate boilerplate:
 * <ul>
 *   <li>{@code @Getter / @Setter} – generates all property accessors</li>
 *   <li>{@code @NoArgsConstructor} – required by JPA spec</li>
 *   <li>{@code @AllArgsConstructor} – convenience for builders / tests</li>
 *   <li>{@code @Builder} – fluent construction pattern</li>
 * </ul>
 */
@Entity
@Table(name = "employees",
       uniqueConstraints = @UniqueConstraint(name = "uk_employee_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    /** Primary key – auto-incremented by the database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Employee's legal first name. Cannot be null or blank. */
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    /** Employee's legal last name. Cannot be null or blank. */
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Corporate e-mail address. Must be unique across the entire table.
     * A database-level unique constraint is declared on the {@code @Table}
     * annotation above for reliable enforcement even outside the JPA layer.
     */
    @Column(name = "email", nullable = false, unique = true, length = 200)
    private String email;

    /** Business unit / department the employee belongs to (e.g., "Engineering"). */
    @Column(name = "department", nullable = false, length = 150)
    private String department;

    /** Job title / designation within the department (e.g., "Senior Engineer"). */
    @Column(name = "designation", nullable = false, length = 150)
    private String designation;

    /**
     * Monthly gross salary stored with precision 15 and scale 2
     * (supports up to 999,999,999,999,999.99).
     * Using {@link BigDecimal} prevents floating-point rounding errors.
     */
    @Column(name = "salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal salary;

    /** The calendar date on which the employee formally joined the organisation. */
    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;
}
