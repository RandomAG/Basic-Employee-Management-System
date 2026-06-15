package net.emsayush.ems_backend.dto;

import lombok.*;

/**
 * Projection DTO returned by the department-wise analytics endpoint.
 *
 * <p>Instances are constructed directly from a JPQL {@code constructor expression}
 * inside {@link net.emsayush.ems_backend.repository.EmployeeRepository}.
 *
 * <p>Note: {@code averageSalary} is typed as {@link Double} because Hibernate 7's
 * {@code AVG()} aggregate function returns a {@code Double} in HQL/JPQL. The
 * type must match exactly for the constructor expression to resolve correctly.
 * Rounding to 2 decimal places is applied in the service layer.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code department}    – The department name (grouped by the query).</li>
 *   <li>{@code employeeCount} – Total headcount in that department.</li>
 *   <li>{@code averageSalary} – Mean salary across all employees in the department.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentAnalyticsDto {

    private String department;
    private Long   employeeCount;
    private Double averageSalary;  // AVG() in Hibernate 7 HQL returns Double, not BigDecimal
}
