package net.emsayush.ems_backend.repository;

import net.emsayush.ems_backend.dto.DepartmentAnalyticsDto;
import net.emsayush.ems_backend.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link Employee} aggregate root.
 *
 * <p>By extending {@link JpaRepository} the following methods are available
 * for free (no implementation needed):
 * <ul>
 *   <li>{@code save(entity)}         – INSERT or UPDATE</li>
 *   <li>{@code findById(id)}         – SELECT by PK</li>
 *   <li>{@code findAll()}            – SELECT *</li>
 *   <li>{@code findAll(Pageable)}    – paginated SELECT *</li>
 *   <li>{@code deleteById(id)}       – DELETE by PK</li>
 *   <li>{@code count()}              – COUNT(*)</li>
 * </ul>
 *
 * <p>Custom query methods follow Spring Data's derived-query naming convention,
 * and more complex analytics are expressed as JPQL {@code @Query} methods.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Checks whether an employee with the given e-mail already exists.
     *
     * <p>Used during creation to give a friendly validation error before
     * hitting the database unique constraint.
     *
     * @param email the e-mail address to check
     * @return {@code true} if a record with that e-mail already exists
     */
    boolean existsByEmail(String email);

    /**
     * Checks whether any <em>other</em> employee (different primary key)
     * already owns the supplied e-mail. Used during updates to allow an
     * employee to keep their own e-mail unchanged.
     *
     * @param email e-mail address to check for conflicts
     * @param id    the ID of the employee being updated (excluded from the check)
     * @return {@code true} if a <em>different</em> employee already has that e-mail
     */
    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Finds an employee by their e-mail address.
     *
     * @param email e-mail address to search for
     * @return an {@link Optional} containing the employee, or empty if not found
     */
    Optional<Employee> findByEmail(String email);

    // ── Analytics ─────────────────────────────────────────────────────────────

    /**
     * Department-wise analytics query using JPQL.
     *
     * <p>Groups all employees by department and returns, per group:
     * <ol>
     *   <li>The department name</li>
     *   <li>The total headcount</li>
     *   <li>The average salary (rounded to 2 decimal places)</li>
     * </ol>
     *
     * <p>The {@code new} expression in JPQL maps the result set columns directly
     * into a {@link DepartmentAnalyticsDto} via its all-args constructor, avoiding
     * the need for a separate mapping step.
     *
     * @return an ordered list of analytics DTOs sorted by headcount descending
     */
    @Query("""
            SELECT new net.emsayush.ems_backend.dto.DepartmentAnalyticsDto(
                       e.department,
                       COUNT(e.id),
                       AVG(e.salary)
                   )
            FROM   Employee e
            GROUP  BY e.department
            ORDER  BY COUNT(e.id) DESC
            """)
    List<DepartmentAnalyticsDto> findDepartmentAnalytics();
}
