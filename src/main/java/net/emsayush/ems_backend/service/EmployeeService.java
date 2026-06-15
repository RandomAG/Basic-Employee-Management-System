package net.emsayush.ems_backend.service;

import net.emsayush.ems_backend.dto.DepartmentAnalyticsDto;
import net.emsayush.ems_backend.dto.EmployeeRequestDto;
import net.emsayush.ems_backend.dto.EmployeeResponseDto;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Service contract for all Employee-related business operations.
 *
 * <p>Defining operations through an interface (rather than directly on a
 * concrete class) gives us:
 * <ul>
 *   <li><strong>Loose coupling</strong> – the controller depends on the
 *       abstraction, not the implementation.</li>
 *   <li><strong>Testability</strong> – the interface can be trivially mocked
 *       in unit tests via Mockito.</li>
 *   <li><strong>Replaceability</strong> – a different implementation
 *       (e.g., caching, multi-datasource) can be swapped in without touching
 *       the controller.</li>
 * </ul>
 */
public interface EmployeeService {

    /**
     * Persists a new employee record.
     *
     * @param requestDto validated inbound payload
     * @return the persisted employee mapped to a response DTO
     * @throws net.emsayush.ems_backend.exception.ResourceNotFoundException never thrown here,
     *         but declared for symmetry
     * @throws org.springframework.dao.DataIntegrityViolationException if the e-mail already exists
     */
    EmployeeResponseDto createEmployee(EmployeeRequestDto requestDto);

    /**
     * Retrieves all employees with server-side pagination and sorting.
     *
     * @param page      zero-based page index
     * @param size      number of records per page
     * @param sortBy    entity field name to sort by (e.g. {@code "salary"})
     * @param direction sort direction – {@code "asc"} or {@code "desc"}
     * @return a {@link Page} of response DTOs
     */
    Page<EmployeeResponseDto> getAllEmployees(int page, int size, String sortBy, String direction);

    /**
     * Retrieves a single employee by their primary key.
     *
     * @param id the surrogate primary key
     * @return the matching employee as a response DTO
     * @throws net.emsayush.ems_backend.exception.ResourceNotFoundException if no employee exists with that id
     */
    EmployeeResponseDto getEmployeeById(Long id);

    /**
     * Updates an existing employee's profile fields.
     *
     * @param id         the primary key of the employee to update
     * @param requestDto validated payload containing the new field values
     * @return the updated employee as a response DTO
     * @throws net.emsayush.ems_backend.exception.ResourceNotFoundException if no employee exists with that id
     */
    EmployeeResponseDto updateEmployee(Long id, EmployeeRequestDto requestDto);

    /**
     * Hard-deletes an employee record from the database.
     *
     * @param id the primary key of the employee to delete
     * @throws net.emsayush.ems_backend.exception.ResourceNotFoundException if no employee exists with that id
     */
    void deleteEmployee(Long id);

    /**
     * Returns per-department analytics (headcount + average salary).
     *
     * @return list of {@link DepartmentAnalyticsDto} sorted by headcount descending
     */
    List<DepartmentAnalyticsDto> getDepartmentAnalytics();
}
