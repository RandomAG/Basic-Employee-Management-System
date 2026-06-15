package net.emsayush.ems_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.emsayush.ems_backend.dto.DepartmentAnalyticsDto;
import net.emsayush.ems_backend.dto.EmployeeRequestDto;
import net.emsayush.ems_backend.dto.EmployeeResponseDto;
import net.emsayush.ems_backend.service.EmployeeService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller that exposes the Employee Management System API endpoints.
 *
 * <p><strong>Base URL:</strong> {@code /api/employees}
 *
 * <p>This class is intentionally thin — it is responsible only for:
 * <ol>
 *   <li>Parsing / validating HTTP request data</li>
 *   <li>Delegating business logic to {@link EmployeeService}</li>
 *   <li>Wrapping results in the appropriate HTTP response</li>
 * </ol>
 *
 * <p>No business logic or database access should ever live in a controller.
 *
 * <h2>Available Endpoints</h2>
 * <pre>
 * POST   /api/employees                                  → Create employee
 * GET    /api/employees?page=0&size=10&sortBy=id&dir=asc → List (paginated)
 * GET    /api/employees/{id}                             → Get by ID
 * PUT    /api/employees/{id}                             → Update employee
 * DELETE /api/employees/{id}                             → Delete employee
 * GET    /api/employees/analytics/department             → Department analytics
 * </pre>
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Creates a new employee record.
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body. If any
     * constraint is violated, Spring throws {@link org.springframework.web.bind.MethodArgumentNotValidException}
     * which is caught by {@link net.emsayush.ems_backend.exception.GlobalExceptionHandler}
     * and returned as a structured 400 JSON response.
     *
     * @param requestDto validated employee creation payload
     * @return {@code 201 Created} with the persisted employee in the body
     */
    @PostMapping
    public ResponseEntity<EmployeeResponseDto> createEmployee(
            @Valid @RequestBody EmployeeRequestDto requestDto) {

        EmployeeResponseDto created = employeeService.createEmployee(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── READ ALL (paginated + sorted) ─────────────────────────────────────────

    /**
     * Returns a paginated, sortable list of all employees.
     *
     * <p>Example requests:
     * <ul>
     *   <li>{@code GET /api/employees} – defaults (page 0, 10/page, sorted by id asc)</li>
     *   <li>{@code GET /api/employees?page=1&size=5&sortBy=salary&direction=desc}</li>
     * </ul>
     *
     * @param page      zero-based page number (default {@code 0})
     * @param size      page size / records per page (default {@code 10})
     * @param sortBy    field name to sort by (default {@code "id"})
     * @param direction sort order: {@code "asc"} or {@code "desc"} (default {@code "asc"})
     * @return {@code 200 OK} with a {@link Page} payload that includes metadata
     *         (totalElements, totalPages, currentPage, etc.)
     */
    @GetMapping
    public ResponseEntity<Page<EmployeeResponseDto>> getAllEmployees(
            @RequestParam(defaultValue = "0")    int    page,
            @RequestParam(defaultValue = "10")   int    size,
            @RequestParam(defaultValue = "id")   String sortBy,
            @RequestParam(defaultValue = "asc")  String direction) {

        Page<EmployeeResponseDto> employees =
                employeeService.getAllEmployees(page, size, sortBy, direction);
        return ResponseEntity.ok(employees);
    }

    // ── READ SINGLE ───────────────────────────────────────────────────────────

    /**
     * Retrieves a single employee by their numeric database ID.
     *
     * @param id the employee's primary key (path variable)
     * @return {@code 200 OK} with the employee DTO, or {@code 404 Not Found}
     *         if no employee exists with that ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponseDto> getEmployeeById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Fully updates (replaces all fields of) an existing employee.
     *
     * <p>A partial update (PATCH semantics) could be implemented by making all
     * DTO fields optional and only applying non-null values, but a full PUT
     * replacement is simpler and sufficient for this system.
     *
     * @param id         the primary key of the employee to update
     * @param requestDto validated payload with all new field values
     * @return {@code 200 OK} with the updated employee DTO
     */
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponseDto> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequestDto requestDto) {

        return ResponseEntity.ok(employeeService.updateEmployee(id, requestDto));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-deletes an employee record from the database.
     *
     * <p>Returns {@code 204 No Content} on success (standard REST convention for
     * a successful DELETE with no response body).
     *
     * @param id the primary key of the employee to delete
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    // ── ANALYTICS ─────────────────────────────────────────────────────────────

    /**
     * Returns department-wise corporate analytics: headcount and average salary
     * per department, sorted by headcount descending.
     *
     * <p>This endpoint is deliberately mapped under
     * {@code /analytics/department} — a sub-resource path — so it does not
     * conflict with {@code GET /api/employees/{id}} (which would match
     * {@code /analytics} as an ID).
     *
     * @return {@code 200 OK} with a list of {@link DepartmentAnalyticsDto}
     */
    @GetMapping("/analytics/department")
    public ResponseEntity<List<DepartmentAnalyticsDto>> getDepartmentAnalytics() {
        return ResponseEntity.ok(employeeService.getDepartmentAnalytics());
    }
}
