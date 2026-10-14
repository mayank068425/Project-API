package com.hms.dto;

import com.hms.entity.Doctor;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class DoctorDto {

    private DoctorDto() {
    }

    public record CreateRequest(
            @NotBlank(message = "Doctor name is required") @Size(max = 150) String name,
            @NotBlank(message = "Specialization is required") @Size(max = 100)
            String specialization,
            @NotNull(message = "Experience is required")
            @Min(value = 0, message = "Experience cannot be negative")
            @Max(value = 70, message = "Experience above 70 years looks like a typo")
            Integer experienceYears,
            @Size(max = 200) String qualification,
            @Pattern(regexp = "^[6-9][0-9]{9}$",
                    message = "Mobile number must be 10 digits starting 6-9") String mobile,
            @NotBlank @Email(message = "Email must be valid") @Size(max = 150) String email,
            // Greater than zero, not merely non-negative: a fee of zero is either
            // a data entry slip or a policy decision that belongs somewhere the
            // billing system can see.
            @NotNull @DecimalMin(value = "0.01", message = "Consultation fee must be "
                    + "greater than zero")
            @Digits(integer = 8, fraction = 2) BigDecimal consultationFee,
            @NotNull(message = "Department is required") Long departmentId) {
    }

    public record UpdateRequest(
            @Size(max = 150) String name,
            @Size(max = 100) String specialization,
            @Min(0) @Max(70) Integer experienceYears,
            @Size(max = 200) String qualification,
            @Pattern(regexp = "^[6-9][0-9]{9}$") String mobile,
            @Email @Size(max = 150) String email,
            @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal consultationFee,
            Long departmentId) {
    }

    public record Response(Long id, String name, String specialization, Integer experienceYears,
                           String qualification, String mobile, String email,
                           BigDecimal consultationFee, DepartmentDto.Summary department,
                           boolean active, long patientCount) {

        /**
         * Maps a doctor, with its department already loaded.
         *
         * <p>The caller is responsible for having fetched the department — via a
         * join fetch — before calling this. Touching a lazy association here,
         * outside the transaction, is a LazyInitializationException, and with
         * {@code open-in-view} off that failure is loud rather than a silent
         * extra query.
         */
        public static Response from(Doctor doctor, long patientCount) {
            return new Response(doctor.getId(), doctor.getName(), doctor.getSpecialization(),
                    doctor.getExperienceYears(), doctor.getQualification(), doctor.getMobile(),
                    doctor.getEmail(), doctor.getConsultationFee(),
                    DepartmentDto.Summary.from(doctor.getDepartment()),
                    doctor.isActive(), patientCount);
        }

        public static Response from(Doctor doctor) {
            return from(doctor, 0L);
        }
    }

    public record Summary(Long id, String name, String specialization) {

        public static Summary from(Doctor doctor) {
            return doctor == null ? null
                    : new Summary(doctor.getId(), doctor.getName(), doctor.getSpecialization());
        }
    }
}
