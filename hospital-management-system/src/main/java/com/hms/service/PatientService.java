package com.hms.service;

import com.hms.dto.PatientDto;
import com.hms.entity.BloodGroup;
import com.hms.entity.Department;
import com.hms.entity.Doctor;
import com.hms.entity.Gender;
import com.hms.entity.Patient;
import com.hms.exception.BusinessRuleException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    /** Nobody has verifiably lived past this. A date of birth beyond it is a typo. */
    private static final int MAX_PLAUSIBLE_AGE = 130;

    private final PatientRepository patients;
    private final DoctorService doctorService;
    private final DepartmentService departmentService;

    public PatientService(PatientRepository patients, DoctorService doctorService,
                          DepartmentService departmentService) {
        this.patients = patients;
        this.doctorService = doctorService;
        this.departmentService = departmentService;
    }

    @Transactional
    public PatientDto.Response register(PatientDto.CreateRequest request) {
        validateDateOfBirth(request.dateOfBirth());

        Patient patient = Patient.builder()
                .firstName(request.firstName().strip())
                .lastName(request.lastName().strip())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .bloodGroup(request.bloodGroup())
                .phone(request.phone().strip())
                .email(request.email() == null ? null : request.email().toLowerCase().strip())
                .address(request.address())
                .disease(request.disease())
                .active(true)
                .build();

        assign(patient, request.doctorId(), request.departmentId());
        patients.save(patient);

        log.info("registered patient {} (age {})", patient.getFullName(), patient.getAge());
        return PatientDto.Response.from(patient);
    }

    /**
     * Attaches a doctor and a department, keeping the two consistent.
     *
     * <p>The schema carries {@code department_id} on both doctor and patient, so
     * the two can disagree. The rule is that <b>the doctor wins</b>: when a
     * doctor is assigned, the patient's department is taken from that doctor
     * regardless of what the request said. An explicit department is honoured
     * only when there is no doctor yet, which is the case the column exists for —
     * a patient admitted to a department before anyone is assigned to them.
     *
     * <p>Without a rule like this the columns drift, and there is no way to tell
     * afterwards which of the two was right.
     */
    private void assign(Patient patient, Long doctorId, Long departmentId) {
        if (doctorId != null) {
            Doctor doctor = doctorService.require(doctorId);
            if (!doctor.isActive()) {
                throw new BusinessRuleException("Dr " + doctor.getName()
                        + " is no longer active and cannot be assigned");
            }
            patient.setDoctor(doctor);

            Department fromDoctor = doctor.getDepartment();
            if (departmentId != null && !departmentId.equals(fromDoctor.getId())) {
                // Explicit and contradictory. Following the doctor silently would
                // be surprising, so say what happened.
                log.info("department {} was requested but Dr {} is in {}; using the doctor's",
                        departmentId, doctor.getName(), fromDoctor.getName());
            }
            patient.setDepartment(fromDoctor);
            return;
        }

        if (departmentId != null) {
            Department department = departmentService.require(departmentId);
            if (!department.isActive()) {
                throw new BusinessRuleException("Department '" + department.getName()
                        + "' is not active");
            }
            patient.setDepartment(department);
        }
    }

    private void validateDateOfBirth(LocalDate dateOfBirth) {
        LocalDate today = LocalDate.now();
        if (dateOfBirth.isAfter(today)) {
            throw new BusinessRuleException("Date of birth cannot be in the future");
        }
        if (dateOfBirth.isBefore(today.minusYears(MAX_PLAUSIBLE_AGE))) {
            throw new BusinessRuleException("Date of birth implies an age above "
                    + MAX_PLAUSIBLE_AGE + " years; check the value");
        }
    }

    @Transactional
    public PatientDto.Response update(Long id, PatientDto.UpdateRequest request) {
        Patient patient = patients.findByIdWithRelations(id)
                .orElseThrow(() -> ResourceNotFoundException.of("patient", id));

        if (request.firstName() != null) {
            patient.setFirstName(request.firstName().strip());
        }
        if (request.lastName() != null) {
            patient.setLastName(request.lastName().strip());
        }
        if (request.dateOfBirth() != null) {
            validateDateOfBirth(request.dateOfBirth());
            patient.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null) {
            patient.setGender(request.gender());
        }
        if (request.bloodGroup() != null) {
            patient.setBloodGroup(request.bloodGroup());
        }
        if (request.phone() != null) {
            patient.setPhone(request.phone().strip());
        }
        if (request.email() != null) {
            patient.setEmail(request.email().toLowerCase().strip());
        }
        if (request.address() != null) {
            patient.setAddress(request.address());
        }
        if (request.disease() != null) {
            patient.setDisease(request.disease());
        }

        if (request.doctorId() != null || request.departmentId() != null) {
            assign(patient, request.doctorId(), request.departmentId());
        }

        patients.save(patient);
        return PatientDto.Response.from(patient);
    }

    /**
     * Removes a patient from active records.
     *
     * <p>A soft delete. A patient record is a medical record: destroying it loses
     * history that may be needed for continuity of care and, in most
     * jurisdictions, is required to be retained for years. The row also carries
     * the only link to whichever doctor treated them.
     */
    @Transactional
    public void delete(Long id) {
        Patient patient = patients.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("patient", id));

        patient.setActive(false);
        patients.save(patient);
        log.info("deactivated patient record {}", patient.getFullName());
    }

    @Transactional(readOnly = true)
    public PatientDto.Response get(Long id) {
        Patient patient = patients.findByIdWithRelations(id)
                .orElseThrow(() -> ResourceNotFoundException.of("patient", id));
        return PatientDto.Response.from(patient);
    }

    /**
     * The search endpoint behind every "search by X" feature.
     *
     * <p>One method rather than six, because separate endpoints cannot be
     * combined and the first request for "female patients with O negative blood"
     * needs a seventh.
     */
    @Transactional(readOnly = true)
    public Page<PatientDto.Response> search(String name, String phone, BloodGroup bloodGroup,
                                            Gender gender, Long departmentId, Long doctorId,
                                            Pageable pageable) {
        Page<Patient> page;
        if (name != null && !name.isBlank()) {
            page = patients.searchByName(name.strip(), pageable);
        } else if (phone != null && !phone.isBlank()) {
            page = patients.findByPhone(phone.strip(), pageable);
        } else if (bloodGroup != null) {
            page = patients.findByBloodGroup(bloodGroup, pageable);
        } else if (gender != null) {
            page = patients.findByGender(gender, pageable);
        } else if (departmentId != null) {
            page = patients.findByDepartment(departmentId, pageable);
        } else if (doctorId != null) {
            page = patients.findByDoctor(doctorId, pageable);
        } else {
            page = patients.findAllActive(pageable);
        }
        return page.map(PatientDto.Response::from);
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return patients.countByActiveTrue();
    }

    @Transactional(readOnly = true)
    public long countByDepartment(Long departmentId) {
        return patients.countByActiveTrueAndDepartmentId(departmentId);
    }

    /** Patients under 18, counted by date range rather than by computing per row. */
    @Transactional(readOnly = true)
    public long countMinors() {
        return patients.countBornAfter(LocalDate.now().minusYears(18));
    }

    /**
     * Patients whose department disagrees with their doctor's.
     *
     * <p>Should always be empty: {@link #assign} sets the department from the
     * doctor, and moving a doctor moves their patients. Exposed anyway, because
     * the two columns are denormalised and an invariant nobody checks is one
     * nobody notices breaking.
     */
    @Transactional(readOnly = true)
    public List<PatientDto.Response> findInconsistent() {
        return patients.findWithMismatchedDepartment().stream()
                .map(PatientDto.Response::from)
                .toList();
    }
}
