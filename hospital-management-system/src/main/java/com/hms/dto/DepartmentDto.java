package com.hms.dto;

import com.hms.entity.Department;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Department request and response shapes.
 *
 * <p>These exist so entities never leave the service layer. Returning a
 * {@code Department} directly would send Jackson round the
 * Department -> Doctor -> Department cycle and produce a StackOverflowError
 * <em>after</em> the response status is committed, giving the client a truncated
 * body and the log a stack trace thousands of frames deep.
 *
 * <p>{@code @JsonIgnore} would stop that, at the cost of hiding the field from
 * callers who legitimately want it. {@code @JsonManagedReference} works but
 * bakes serialisation concerns into the persistence model. Mapping to a record
 * solves the cycle and also stops {@code version}, audit timestamps and the
 * soft-delete flag leaking out.
 */
public final class DepartmentDto {

    private DepartmentDto() {
    }

    public record CreateRequest(
            @NotBlank(message = "Department name is required")
            @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Min(value = -5, message = "Floor number looks wrong")
            @Max(value = 200, message = "Floor number looks wrong") Integer floorNumber) {
    }

    public record UpdateRequest(
            @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Min(-5) @Max(200) Integer floorNumber) {
    }

    /**
     * @param doctorCount computed by a grouped count rather than by walking the
     *                    lazy collection, which would be one query per department
     */
    public record Response(Long id, String name, String description, Integer floorNumber,
                           boolean active, long doctorCount) {

        public static Response from(Department department, long doctorCount) {
            return new Response(department.getId(), department.getName(),
                    department.getDescription(), department.getFloorNumber(),
                    department.isActive(), doctorCount);
        }

        public static Response from(Department department) {
            return from(department, 0L);
        }
    }

    /** Minimal shape for embedding inside a doctor or patient response. */
    public record Summary(Long id, String name, Integer floorNumber) {

        public static Summary from(Department department) {
            return department == null ? null
                    : new Summary(department.getId(), department.getName(),
                            department.getFloorNumber());
        }
    }
}
