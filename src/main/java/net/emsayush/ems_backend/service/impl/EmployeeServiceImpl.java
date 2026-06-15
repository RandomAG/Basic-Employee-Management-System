package net.emsayush.ems_backend.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.emsayush.ems_backend.dto.DepartmentAnalyticsDto;
import net.emsayush.ems_backend.dto.EmployeeRequestDto;
import net.emsayush.ems_backend.dto.EmployeeResponseDto;
import net.emsayush.ems_backend.entity.Employee;
import net.emsayush.ems_backend.exception.ResourceNotFoundException;
import net.emsayush.ems_backend.repository.EmployeeRepository;
import net.emsayush.ems_backend.service.EmployeeService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Concrete implementation of {@link EmployeeService}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Business-rule enforcement (e.g. duplicate e-mail guard before hitting DB)</li>
 *   <li>DTO ↔ Entity mapping (manual mapping, keeping dependencies minimal)</li>
 *   <li>Delegating persistence to {@link EmployeeRepository}</li>
 *   <li>Transaction boundary management via {@code @Transactional}</li>
 * </ul>
 *
 * <p>{@code @RequiredArgsConstructor} from Lombok generates a constructor that
 * injects all {@code final} fields — the Spring-recommended way to do
 * constructor injection without verbose boilerplate.
 *
 * <p>{@code @Slf4j} injects a SLF4J {@code Logger} field named {@code log}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Guards against duplicate e-mail at the service layer first, so we can
     * return a descriptive 400 message rather than a raw 409 from the DB.
     * The DB unique constraint remains as a final safety net.
     */
    @Override
    @Transactional
    public EmployeeResponseDto createEmployee(EmployeeRequestDto requestDto) {
        log.debug("Creating employee with email: {}", requestDto.getEmail());

        // Business rule: e-mail must be unique across all employees
        if (employeeRepository.existsByEmail(requestDto.getEmail())) {
            throw new DataIntegrityViolationException(
                    "An employee with email '%s' already exists.".formatted(requestDto.getEmail()));
        }

        Employee employee = mapToEntity(requestDto);
        Employee saved    = employeeRepository.save(employee);

        log.info("Employee created successfully with id={}", saved.getId());
        return mapToResponseDto(saved);
    }

    // ── READ (ALL – paginated) ─────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Constructs a {@link Sort} from the caller-supplied parameters and
     * wraps it in a {@link PageRequest}. Spring Data JPA handles the
     * {@code LIMIT / OFFSET} translation to SQL automatically.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponseDto> getAllEmployees(
            int page, int size, String sortBy, String direction) {

        log.debug("Fetching employees: page={}, size={}, sortBy={}, direction={}",
                page, size, sortBy, direction);

        // Build sort direction safely (default to ASC for unrecognised values)
        Sort sort = direction.equalsIgnoreCase(Sort.Direction.DESC.name())
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return employeeRepository
                .findAll(pageable)
                .map(this::mapToResponseDto);
    }

    // ── READ (SINGLE) ─────────────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public EmployeeResponseDto getEmployeeById(Long id) {
        log.debug("Fetching employee with id={}", id);

        Employee employee = findByIdOrThrow(id);
        return mapToResponseDto(employee);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Uses Spring's "dirty-checking" mechanism: once the entity is loaded
     * inside a transaction, any field mutations are automatically flushed to
     * the database at transaction commit — no explicit {@code save()} call needed
     * (though we do call it here for clarity and to return the refreshed state).
     */
    @Override
    @Transactional
    public EmployeeResponseDto updateEmployee(Long id, EmployeeRequestDto requestDto) {
        log.debug("Updating employee id={}", id);

        Employee employee = findByIdOrThrow(id);

        // Prevent stealing another employee's e-mail during an update
        if (employeeRepository.existsByEmailAndIdNot(requestDto.getEmail(), id)) {
            throw new DataIntegrityViolationException(
                    "Email '%s' is already in use by another employee.".formatted(requestDto.getEmail()));
        }

        // Apply field updates
        employee.setFirstName(requestDto.getFirstName());
        employee.setLastName(requestDto.getLastName());
        employee.setEmail(requestDto.getEmail());
        employee.setDepartment(requestDto.getDepartment());
        employee.setDesignation(requestDto.getDesignation());
        employee.setSalary(requestDto.getSalary());
        employee.setDateOfJoining(requestDto.getDateOfJoining());

        Employee updated = employeeRepository.save(employee);
        log.info("Employee id={} updated successfully", id);
        return mapToResponseDto(updated);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteEmployee(Long id) {
        log.debug("Deleting employee id={}", id);

        // Verify existence before deletion so we can return a proper 404 vs 204
        Employee employee = findByIdOrThrow(id);
        employeeRepository.delete(employee);
        log.info("Employee id={} deleted successfully", id);
    }

    // ── ANALYTICS ─────────────────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<DepartmentAnalyticsDto> getDepartmentAnalytics() {
        log.debug("Fetching department analytics");
        List<DepartmentAnalyticsDto> results = employeeRepository.findDepartmentAnalytics();

        // AVG() in Hibernate 7 HQL returns a raw Double.
        // Round to 2 decimal places here rather than relying on ROUND() in JPQL
        // (which has type-resolution issues in Hibernate 7 constructor expressions).
        results.forEach(dto -> {
            if (dto.getAverageSalary() != null) {
                dto.setAverageSalary(
                        Math.round(dto.getAverageSalary() * 100.0) / 100.0
                );
            }
        });

        return results;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Looks up an employee by primary key, throwing {@link ResourceNotFoundException}
     * with a descriptive message if no record is found.
     *
     * @param id the primary key to look up
     * @return the {@link Employee} entity
     */
    private Employee findByIdOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));
    }

    /**
     * Maps a validated {@link EmployeeRequestDto} to a new {@link Employee} entity.
     *
     * <p>Manual mapping is preferred here over MapStruct or ModelMapper to keep the
     * dependency footprint small and keep the transformation logic visible and explicit.
     *
     * @param dto the inbound request DTO
     * @return a transient {@link Employee} entity ready to be persisted
     */
    private Employee mapToEntity(EmployeeRequestDto dto) {
        return Employee.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .department(dto.getDepartment())
                .designation(dto.getDesignation())
                .salary(dto.getSalary())
                .dateOfJoining(dto.getDateOfJoining())
                .build();
    }

    /**
     * Maps a persisted {@link Employee} entity to an {@link EmployeeResponseDto}.
     *
     * <p>The {@code fullName} convenience field is computed here to avoid
     * polluting the entity with presentation-layer concerns.
     *
     * @param employee the persisted entity
     * @return the response DTO to be serialised by the controller
     */
    private EmployeeResponseDto mapToResponseDto(Employee employee) {
        return EmployeeResponseDto.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .fullName(employee.getFirstName() + " " + employee.getLastName())
                .email(employee.getEmail())
                .department(employee.getDepartment())
                .designation(employee.getDesignation())
                .salary(employee.getSalary())
                .dateOfJoining(employee.getDateOfJoining())
                .build();
    }
}
