package com.hms.service;

import com.hms.dto.DoctorDto;
import com.hms.entity.Department;
import com.hms.entity.Doctor;
import com.hms.entity.Patient;
import com.hms.exception.BusinessRuleException;
import com.hms.exception.DuplicateResourceException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.DoctorRepository;
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
public class DoctorService {

    private static final Logger log = LoggerFactory.getLogger(DoctorService.class);

    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final DepartmentService departmentService;

    public DoctorService(DoctorRepository doctors, PatientRepository patients,
                         DepartmentService departmentService) {
        this.doctors = doctors;
        this.patients = patients;
        this.departmentService = departmentService;
    }

    @Transactional
    public DoctorDto.Response create(DoctorDto.CreateRequest request) {
        String email = request.email().toLowerCase().strip();
        if (doctors.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("A doctor with email " + email
                    + " already exists");
        }

        Department department = departmentService.require(request.departmentId());
        if (!department.isActive()) {
            throw new BusinessRuleException("Department '" + department.getName()
                    + "' is not active");
        }

        Doctor doctor = Doctor.builder()
                .name(request.name().strip())
                .specialization(request.specialization().strip())
                .experienceYears(request.experienceYears())
                .qualification(request.qualification())
                .mobile(request.mobile())
                .email(email)
                .consultationFee(request.consultationFee())
                .department(department)
                .active(true)
                .build();
        doctors.save(doctor);

        log.info("added doctor {} to {}", doctor.getName(), department.getName());
        return DoctorDto.Response.from(doctor, 0L);
    }

    /**
     * Updates a doctor.
     *
     * <p>Moving a doctor between departments is the interesting case: their
     * patients carry their own {@code department_id}, so after the move those
     * rows point at the department the doctor has left. The patients are moved
     * with them, because the alternative is a patient whose record says
     * Cardiology and whose doctor is in Neurology, and nothing to say which is
     * right.
     */
    @Transactional
    public DoctorDto.Response update(Long id, DoctorDto.UpdateRequest request) {
        Doctor doctor = doctors.findByIdWithDepartment(id)
                .orElseThrow(() -> ResourceNotFoundException.of("doctor", id));

        if (request.email() != null) {
            String email = request.email().toLowerCase().strip();
            doctors.findByEmailIgnoreCase(email).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new DuplicateResourceException("A doctor with email " + email
                            + " already exists");
                }
            });
            doctor.setEmail(email);
        }
        if (request.name() != null) {
            doctor.setName(request.name().strip());
        }
        if (request.specialization() != null) {
            doctor.setSpecialization(request.specialization().strip());
        }
        if (request.experienceYears() != null) {
            doctor.setExperienceYears(request.experienceYears());
        }
        if (request.qualification() != null) {
            doctor.setQualification(request.qualification());
        }
        if (request.mobile() != null) {
            doctor.setMobile(request.mobile());
        }
        if (request.consultationFee() != null) {
            doctor.setConsultationFee(request.consultationFee());
        }

        if (request.departmentId() != null
                && !request.departmentId().equals(doctor.getDepartment().getId())) {
            Department destination = departmentService.require(request.departmentId());
            String from = doctor.getDepartment().getName();
            doctor.setDepartment(destination);

            List<Patient> theirPatients = patients.findByActiveTrueAndDoctorId(id);
            theirPatients.forEach(p -> p.setDepartment(destination));
            patients.saveAll(theirPatients);

            log.info("moved doctor {} from {} to {}, with {} patient(s)", doctor.getName(),
                    from, destination.getName(), theirPatients.size());
        }

        doctors.save(doctor);
        return DoctorDto.Response.from(doctor, doctors.countActivePatients(id));
    }

    /**
     * Removes a doctor, refusing while patients are assigned to them.
     *
     * <p>Same reasoning as departments. A cascade here would delete the patient
     * records of everyone in that doctor's care, which is both a data loss
     * incident and, in most jurisdictions, a records-retention breach.
     */
    @Transactional
    public void delete(Long id) {
        Doctor doctor = doctors.findByIdWithDepartment(id)
                .orElseThrow(() -> ResourceNotFoundException.of("doctor", id));

        long patientCount = doctors.countActivePatients(id);
        if (patientCount > 0) {
            throw new BusinessRuleException("Dr " + doctor.getName() + " still has "
                    + patientCount + " patient(s) assigned. Reassign them before removing "
                    + "this record.");
        }

        doctor.setActive(false);
        doctors.save(doctor);
        log.info("deactivated doctor {}", doctor.getName());
    }

    @Transactional(readOnly = true)
    public DoctorDto.Response get(Long id) {
        Doctor doctor = doctors.findByIdWithDepartment(id)
                .orElseThrow(() -> ResourceNotFoundException.of("doctor", id));
        return DoctorDto.Response.from(doctor, doctors.countActivePatients(id));
    }

    @Transactional(readOnly = true)
    public Doctor require(Long id) {
        return doctors.findByIdWithDepartment(id)
                .orElseThrow(() -> ResourceNotFoundException.of("doctor", id));
    }

    @Transactional(readOnly = true)
    public Page<DoctorDto.Response> search(String name, String specialization,
                                           Long departmentId, Pageable pageable) {
        Page<Doctor> page;
        if (name != null && !name.isBlank()) {
            page = doctors.searchByName(name.strip(), pageable);
        } else if (specialization != null && !specialization.isBlank()) {
            page = doctors.searchBySpecialization(specialization.strip(), pageable);
        } else if (departmentId != null) {
            page = doctors.findByDepartment(departmentId, pageable);
        } else {
            page = doctors.findAllWithDepartment(pageable);
        }

        // One grouped count for the whole page, not one per doctor.
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : doctors.countActivePatientsByDoctor()) {
            counts.put((Long) row[0], (Long) row[1]);
        }

        return page.map(d -> DoctorDto.Response.from(d, counts.getOrDefault(d.getId(), 0L)));
    }
}
