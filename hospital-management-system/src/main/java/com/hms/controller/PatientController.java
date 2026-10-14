package com.hms.controller;

import com.hms.dto.PageResponse;
import com.hms.dto.PatientDto;
import com.hms.entity.BloodGroup;
import com.hms.entity.Gender;
import com.hms.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patients")
public class PatientController {

    private final PatientService patients;

    public PatientController(PatientService patients) {
        this.patients = patients;
    }

    @PostMapping
    @Operation(summary = "Register a patient",
            description = "Send a date of birth, not an age. Age is derived on every read, "
                    + "so it cannot go stale. If a doctor is supplied, the patient's "
                    + "department is taken from that doctor.")
    public ResponseEntity<PatientDto.Response> register(
            @Valid @RequestBody PatientDto.CreateRequest request) {
        PatientDto.Response created = patients.register(request);
        return ResponseEntity.created(java.net.URI.create("/api/patients/" + created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List patients, filtered by any combination of criteria")
    public PageResponse<PatientDto.Response> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @Parameter(description = "A+, O-, AB+ and so on")
            @RequestParam(required = false) BloodGroup bloodGroup,
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName") String sortBy) {
        return PageResponse.from(
                patients.search(name, phone, bloodGroup, gender, departmentId, doctorId,
                        PageRequest.of(page, Math.min(size, 100), Sort.by(sortBy))),
                Function.identity());
    }

    @GetMapping("/search")
    @Operation(summary = "Search patients by name, phone, blood group or gender")
    public PageResponse<PatientDto.Response> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) BloodGroup bloodGroup,
            @RequestParam(required = false) Gender gender,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
                patients.search(name, phone, bloodGroup, gender, null, null,
                        PageRequest.of(page, Math.min(size, 100))),
                Function.identity());
    }

    @GetMapping("/count")
    @Operation(summary = "Patient counts")
    public Map<String, Long> count(@RequestParam(required = false) Long departmentId) {
        return departmentId == null
                ? Map.of("total", patients.countAll(), "minors", patients.countMinors())
                : Map.of("total", patients.countByDepartment(departmentId));
    }

    @GetMapping("/{id}")
    public PatientDto.Response get(@PathVariable Long id) {
        return patients.get(id);
    }

    @PutMapping("/{id}")
    public PatientDto.Response update(@PathVariable Long id,
                                      @Valid @RequestBody PatientDto.UpdateRequest request) {
        return patients.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a patient",
            description = "A soft delete. A patient record is a medical record and is "
                    + "retained rather than destroyed.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Removed from active records")})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patients.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Patients whose department disagrees with their doctor's.
     *
     * <p>Should always be empty. The two columns are denormalised, and an
     * invariant that is never checked is one nobody notices breaking.
     */
    @GetMapping("/inconsistent")
    @Operation(summary = "Records where the patient's department and their doctor's "
            + "disagree. Should be empty.")
    public Map<String, Object> inconsistent() {
        List<PatientDto.Response> mismatched = patients.findInconsistent();
        return Map.of("count", mismatched.size(), "patients", mismatched,
                "message", mismatched.isEmpty()
                        ? "Every patient's department matches their doctor's."
                        : "Some patients belong to a different department than their doctor.");
    }
}
