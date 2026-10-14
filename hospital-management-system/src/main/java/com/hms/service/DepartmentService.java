package com.hms.service;

import com.hms.dto.DepartmentDto;
import com.hms.entity.Department;
import com.hms.exception.BusinessRuleException;
import com.hms.exception.DuplicateResourceException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.DepartmentRepository;
import com.hms.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentService.class);

    private final DepartmentRepository departments;
    private final PatientRepository patients;

    public DepartmentService(DepartmentRepository departments, PatientRepository patients) {
        this.departments = departments;
        this.patients = patients;
    }

    @Transactional
    public DepartmentDto.Response create(DepartmentDto.CreateRequest request) {
        String name = request.name().strip();
        // Checked here for a readable message; the unique index is the actual
        // guarantee, because this check loses the race against a simultaneous
        // insert of the same name.
        if (departments.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A department named '" + name
                    + "' already exists");
        }

        Department department = Department.builder()
                .name(name)
                .description(request.description())
                .floorNumber(request.floorNumber())
                .active(true)
                .build();
        departments.save(department);

        log.info("created department {}", name);
        return DepartmentDto.Response.from(department, 0L);
    }

    @Transactional
    public DepartmentDto.Response update(Long id, DepartmentDto.UpdateRequest request) {
        Department department = require(id);

        if (request.name() != null) {
            String name = request.name().strip();
            // Only a clash with a *different* department is a duplicate. Without
            // this, renaming a department to its own current name is rejected.
            departments.findByNameIgnoreCase(name).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new DuplicateResourceException("A department named '" + name
                            + "' already exists");
                }
            });
            department.setName(name);
        }
        if (request.description() != null) {
            department.setDescription(request.description());
        }
        if (request.floorNumber() != null) {
            department.setFloorNumber(request.floorNumber());
        }

        departments.save(department);
        return DepartmentDto.Response.from(department,
                departments.countActiveDoctors(department.getId()));
    }

    /**
     * Removes a department, refusing while anything depends on it.
     *
     * <p>The relationship is mapped without {@code CascadeType.REMOVE}
     * deliberately, and this is the reason. With a cascade, deleting Cardiology
     * would delete its doctors, and a further cascade on the doctor-to-patient
     * side would take every one of their patients' records with them. A single
     * HTTP call, no warning, and a department's worth of medical history gone —
     * the cascade doing exactly what it was told.
     *
     * <p>So the dependants are counted first and the caller is told what is in
     * the way. Reassign the doctors, then remove the department.
     */
    @Transactional
    public void delete(Long id) {
        Department department = require(id);

        long doctorCount = departments.countActiveDoctors(id);
        if (doctorCount > 0) {
            throw new BusinessRuleException("Department '" + department.getName() + "' still has "
                    + doctorCount + " doctor(s). Reassign them before removing it.");
        }
        long patientCount = patients.countByActiveTrueAndDepartmentId(id);
        if (patientCount > 0) {
            throw new BusinessRuleException("Department '" + department.getName() + "' still has "
                    + patientCount + " patient(s) assigned. Reassign them before removing it.");
        }

        // Deactivated rather than deleted: doctors and patients that have since
        // been reassigned still reference this row historically, and the name
        // should stay resolvable.
        department.setActive(false);
        departments.save(department);
        log.info("deactivated department {}", department.getName());
    }

    @Transactional(readOnly = true)
    public DepartmentDto.Response get(Long id) {
        Department department = require(id);
        return DepartmentDto.Response.from(department, departments.countActiveDoctors(id));
    }

    /**
     * Lists departments with their doctor counts.
     *
     * <p>The counts come from one grouped query rather than from reading each
     * department's lazy collection. That version issues a query per row: twenty
     * departments become twenty-one round trips, and the number grows with the
     * data instead of staying fixed.
     */
    @Transactional(readOnly = true)
    public Page<DepartmentDto.Response> list(Pageable pageable) {
        Page<Department> page = departments.findByActiveTrue(pageable);

        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : departments.countActiveDoctorsByDepartment()) {
            counts.put((Long) row[0], (Long) row[1]);
        }

        return page.map(d -> DepartmentDto.Response.from(d,
                counts.getOrDefault(d.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public Department require(Long id) {
        return departments.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("department", id));
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto.Response> listAll() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : departments.countActiveDoctorsByDepartment()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return departments.findAll().stream()
                .filter(Department::isActive)
                .map(d -> DepartmentDto.Response.from(d, counts.getOrDefault(d.getId(), 0L)))
                .toList();
    }
}
