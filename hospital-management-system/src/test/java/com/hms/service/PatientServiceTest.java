package com.hms.service;

import com.hms.dto.PatientDto;
import com.hms.entity.BloodGroup;
import com.hms.entity.Department;
import com.hms.entity.Doctor;
import com.hms.entity.Gender;
import com.hms.entity.Patient;
import com.hms.exception.BusinessRuleException;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PatientServiceTest {

    @Mock private PatientRepository patients;
    @Mock private DoctorService doctorService;
    @Mock private DepartmentService departmentService;

    private PatientService service;

    private Department cardiology;
    private Department neurology;
    private Doctor cardiologist;

    @BeforeEach
    void setUp() {
        service = new PatientService(patients, doctorService, departmentService);

        cardiology = Department.builder().id(1L).name("Cardiology").active(true).build();
        neurology = Department.builder().id(2L).name("Neurology").active(true).build();
        cardiologist = Doctor.builder().id(10L).name("Anita Rao")
                .specialization("Cardiology").experienceYears(14)
                .email("anita@hospital.local").consultationFee(new BigDecimal("1200.00"))
                .department(cardiology).active(true).build();

        when(patients.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private PatientDto.CreateRequest request(Long doctorId, Long departmentId) {
        return new PatientDto.CreateRequest("Priya", "Sharma",
                LocalDate.of(1991, 3, 14), Gender.FEMALE, BloodGroup.O_POSITIVE,
                "9812345670", "priya@example.com", "Pune", "Hypertension",
                doctorId, departmentId);
    }

    @Test
    @DisplayName("the response carries a derived age, never a stored one")
    void responseCarriesDerivedAge() {
        PatientDto.Response response = service.register(request(null, null));

        int expected = LocalDate.now().getYear() - 1991
                - (LocalDate.now().getMonthValue() < 3
                   || (LocalDate.now().getMonthValue() == 3
                       && LocalDate.now().getDayOfMonth() < 14) ? 1 : 0);
        assertThat(response.age()).isEqualTo(expected);
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1991, 3, 14));
    }

    @Test
    @DisplayName("assigning a doctor sets the department from that doctor")
    void departmentComesFromTheDoctor() {
        when(doctorService.require(10L)).thenReturn(cardiologist);

        PatientDto.Response response = service.register(request(10L, null));

        assertThat(response.doctor().id()).isEqualTo(10L);
        assertThat(response.department().name()).isEqualTo("Cardiology");
    }

    @Test
    @DisplayName("a contradictory department is overridden by the doctor's")
    void doctorWinsOverExplicitDepartment() {
        // The two columns are denormalised and can disagree. The rule is that
        // the doctor wins, so there is always one answer rather than two.
        when(doctorService.require(10L)).thenReturn(cardiologist);

        PatientDto.Response response = service.register(request(10L, 2L));

        assertThat(response.department().name()).isEqualTo("Cardiology");
        assertThat(response.department().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a department with no doctor is honoured")
    void departmentWithoutDoctorIsKept() {
        when(departmentService.require(2L)).thenReturn(neurology);

        PatientDto.Response response = service.register(request(null, 2L));

        // The case the nullable doctor_id exists for: admitted to a department
        // before anyone is assigned.
        assertThat(response.doctor()).isNull();
        assertThat(response.department().name()).isEqualTo("Neurology");
    }

    @Test
    @DisplayName("an inactive doctor cannot be assigned")
    void refusesInactiveDoctor() {
        cardiologist.setActive(false);
        when(doctorService.require(10L)).thenReturn(cardiologist);

        assertThatThrownBy(() -> service.register(request(10L, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer active");
    }

    @Test
    @DisplayName("a future date of birth is refused")
    void refusesFutureDateOfBirth() {
        PatientDto.CreateRequest future = new PatientDto.CreateRequest("A", "B",
                LocalDate.now().plusDays(1), Gender.OTHER, BloodGroup.O_POSITIVE,
                "9812345670", null, null, null, null, null);

        assertThatThrownBy(() -> service.register(future))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("future");
        verify(patients, never()).save(any());
    }

    @Test
    @DisplayName("an implausibly old date of birth is refused")
    void refusesImplausibleAge() {
        PatientDto.CreateRequest ancient = new PatientDto.CreateRequest("A", "B",
                LocalDate.now().minusYears(150), Gender.OTHER, BloodGroup.O_POSITIVE,
                "9812345670", null, null, null, null, null);

        assertThatThrownBy(() -> service.register(ancient))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("130");
    }

    @Test
    @DisplayName("deleting a patient is a soft delete")
    void deleteIsSoft() {
        Patient patient = Patient.builder().id(5L).firstName("Priya").lastName("Sharma")
                .dateOfBirth(LocalDate.of(1991, 3, 14)).gender(Gender.FEMALE)
                .bloodGroup(BloodGroup.O_POSITIVE).phone("9812345670").active(true).build();
        when(patients.findById(5L)).thenReturn(Optional.of(patient));

        service.delete(5L);

        // A medical record is retained, not destroyed.
        assertThat(patient.isActive()).isFalse();
        verify(patients).save(patient);
        verify(patients, never()).delete(any());
    }
}
