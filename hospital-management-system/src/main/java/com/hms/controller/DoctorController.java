package com.hms.controller;

import com.hms.dto.DoctorDto;
import com.hms.dto.PageResponse;
import com.hms.dto.PatientDto;
import com.hms.service.DoctorService;
import com.hms.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Function;

@RestController
@RequestMapping("/api/doctors")
@Tag(name = "Doctors")
public class DoctorController {

    private final DoctorService doctors;
    private final PatientService patients;

    public DoctorController(DoctorService doctors, PatientService patients) {
        this.doctors = doctors;
        this.patients = patients;
    }

    @PostMapping
    public ResponseEntity<DoctorDto.Response> create(
            @Valid @RequestBody DoctorDto.CreateRequest request) {
        DoctorDto.Response created = doctors.create(request);
        return ResponseEntity.created(java.net.URI.create("/api/doctors/" + created.id()))
                .body(created);
    }

    /**
     * Lists and filters doctors.
     *
     * <p>Name, specialization and department share this one endpoint rather than
     * having {@code /search/by-name}, {@code /search/by-specialization} and
     * {@code /by-department} alongside it. Separate endpoints cannot be combined,
     * and the first request for "cardiologists in the Cardiology department"
     * needs a fourth.
     */
    @GetMapping
    @Operation(summary = "List doctors, filtered by name, specialization or department")
    public PageResponse<DoctorDto.Response> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy) {
        return PageResponse.from(
                doctors.search(name, specialization, departmentId,
                        PageRequest.of(page, Math.min(size, 100), Sort.by(sortBy))),
                Function.identity());
    }

    @GetMapping("/search")
    @Operation(summary = "Search doctors by name or specialization")
    public PageResponse<DoctorDto.Response> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String specialization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
                doctors.search(name, specialization, null,
                        PageRequest.of(page, Math.min(size, 100))),
                Function.identity());
    }

    @GetMapping("/{id}")
    public DoctorDto.Response get(@PathVariable Long id) {
        return doctors.get(id);
    }

    @GetMapping("/{id}/patients")
    @Operation(summary = "Patients under this doctor's care")
    public PageResponse<PatientDto.Response> patients(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
                patients.search(null, null, null, null, null, id,
                        PageRequest.of(page, Math.min(size, 100))),
                Function.identity());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a doctor",
            description = "Changing the department moves the doctor's patients with them, "
                    + "so a patient's department never disagrees with their doctor's.")
    public DoctorDto.Response update(@PathVariable Long id,
                                     @Valid @RequestBody DoctorDto.UpdateRequest request) {
        return doctors.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a doctor",
            description = "Refused while patients are still assigned. Deleting a doctor "
                    + "must not delete their patients' records.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removed"),
            @ApiResponse(responseCode = "409", description = "Patients still assigned")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        doctors.delete(id);
        return ResponseEntity.noContent().build();
    }
}
