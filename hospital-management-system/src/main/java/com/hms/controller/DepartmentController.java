package com.hms.controller;

import com.hms.dto.DepartmentDto;
import com.hms.dto.PageResponse;
import com.hms.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/departments")
@Tag(name = "Departments")
public class DepartmentController {

    private final DepartmentService departments;

    public DepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    @PostMapping
    @Operation(summary = "Add a department. The name must be unique.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "409", description = "A department with that name exists")
    })
    public ResponseEntity<DepartmentDto.Response> create(
            @Valid @RequestBody DepartmentDto.CreateRequest request) {
        DepartmentDto.Response created = departments.create(request);
        // 201 with a Location header, which is what a POST that creates a
        // resource is supposed to return.
        return ResponseEntity.created(java.net.URI.create("/api/departments/" + created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List departments with their doctor counts")
    public PageResponse<DepartmentDto.Response> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy) {
        return PageResponse.from(
                departments.list(PageRequest.of(page, Math.min(size, 100), Sort.by(sortBy))),
                Function.identity());
    }

    @GetMapping("/{id}")
    public DepartmentDto.Response get(@PathVariable Long id) {
        return departments.get(id);
    }

    @PutMapping("/{id}")
    public DepartmentDto.Response update(@PathVariable Long id,
                                         @Valid @RequestBody DepartmentDto.UpdateRequest request) {
        return departments.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a department",
            description = "Refused while doctors or patients are still assigned. "
                    + "The relationship is deliberately mapped without a remove cascade: "
                    + "deleting a department must not delete its doctors and their "
                    + "patients' records.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removed"),
            @ApiResponse(responseCode = "409", description = "Doctors or patients still assigned")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        departments.delete(id);
        return ResponseEntity.noContent().build();
    }
}
