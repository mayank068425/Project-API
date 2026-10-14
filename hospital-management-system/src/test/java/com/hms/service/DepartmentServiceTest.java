package com.hms.service;

import com.hms.dto.DepartmentDto;
import com.hms.entity.Department;
import com.hms.exception.BusinessRuleException;
import com.hms.exception.DuplicateResourceException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.DepartmentRepository;
import com.hms.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepartmentServiceTest {

    @Mock private DepartmentRepository departments;
    @Mock private PatientRepository patients;

    private DepartmentService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentService(departments, patients);
    }

    private Department cardiology() {
        return Department.builder().id(1L).name("Cardiology")
                .description("Heart").floorNumber(3).active(true).build();
    }

    @Test
    @DisplayName("a duplicate name is refused")
    void refusesDuplicateName() {
        when(departments.existsByNameIgnoreCase("Cardiology")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new DepartmentDto.CreateRequest("Cardiology", null, 3)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(departments, never()).save(any());
    }

    @Test
    @DisplayName("renaming a department to its own current name is allowed")
    void allowsSelfRename() {
        Department existing = cardiology();
        when(departments.findById(1L)).thenReturn(Optional.of(existing));
        when(departments.findByNameIgnoreCase("Cardiology")).thenReturn(Optional.of(existing));
        when(departments.countActiveDoctors(1L)).thenReturn(2L);

        // The duplicate check has to exclude the row being updated, or every
        // update that leaves the name alone fails.
        DepartmentDto.Response response = service.update(1L,
                new DepartmentDto.UpdateRequest("Cardiology", "Updated", 4));

        assertThat(response.description()).isEqualTo("Updated");
        assertThat(response.floorNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("a name already used by a different department is refused")
    void refusesRenameOntoAnother() {
        Department target = cardiology();
        Department other = Department.builder().id(2L).name("Neurology").active(true).build();
        when(departments.findById(1L)).thenReturn(Optional.of(target));
        when(departments.findByNameIgnoreCase("Neurology")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(1L,
                new DepartmentDto.UpdateRequest("Neurology", null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("deleting a department with doctors is refused, not cascaded")
    void refusesDeleteWithDoctors() {
        // The whole reason CascadeType.REMOVE is absent from the mapping. With
        // it, this call would delete the doctors and, through them, every one of
        // their patients' records.
        when(departments.findById(1L)).thenReturn(Optional.of(cardiology()));
        when(departments.countActiveDoctors(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("3 doctor(s)")
                .hasMessageContaining("Reassign");
    }

    @Test
    @DisplayName("deleting a department with patients is refused")
    void refusesDeleteWithPatients() {
        when(departments.findById(1L)).thenReturn(Optional.of(cardiology()));
        when(departments.countActiveDoctors(1L)).thenReturn(0L);
        when(patients.countByActiveTrueAndDepartmentId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("5 patient(s)");
    }

    @Test
    @DisplayName("an empty department is deactivated rather than removed")
    void deactivatesEmptyDepartment() {
        Department department = cardiology();
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(departments.countActiveDoctors(1L)).thenReturn(0L);
        when(patients.countByActiveTrueAndDepartmentId(1L)).thenReturn(0L);

        service.delete(1L);

        // Soft, because doctors and patients since reassigned still reference
        // this row historically and the name must stay resolvable.
        assertThat(department.isActive()).isFalse();
        verify(departments).save(department);
    }

    @Test
    @DisplayName("an unknown id is a 404, not a null")
    void unknownIdThrows() {
        when(departments.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
