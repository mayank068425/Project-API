package com.hms.dto;

import com.hms.entity.BloodGroup;
import com.hms.entity.Gender;
import com.hms.entity.Patient;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class PatientDto {

    private PatientDto() {
    }

    /**
     * @param dateOfBirth the stored fact. Note there is no {@code age} field on
     *                    the request: accepting one would let a caller submit an
     *                    age that contradicts the date of birth, and the system
     *                    would then have two answers to the same question.
     */
    public record CreateRequest(
            @NotBlank(message = "First name is required") @Size(max = 80) String firstName,
            @NotBlank(message = "Last name is required") @Size(max = 80) String lastName,
            @NotNull(message = "Date of birth is required")
            @Past(message = "Date of birth must be in the past")
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dateOfBirth,
            @NotNull(message = "Gender is required") Gender gender,
            @NotNull(message = "Blood group is required") BloodGroup bloodGroup,
            @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$",
                    message = "Mobile number must be 10 digits starting 6-9") String phone,
            @Email(message = "Email must be valid") @Size(max = 150) String email,
            @Size(max = 300) String address,
            @Size(max = 300) String disease,
            Long doctorId,
            Long departmentId) {
    }

    public record UpdateRequest(
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Past @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dateOfBirth,
            Gender gender,
            BloodGroup bloodGroup,
            @Pattern(regexp = "^[6-9][0-9]{9}$") String phone,
            @Email @Size(max = 150) String email,
            @Size(max = 300) String address,
            @Size(max = 300) String disease,
            Long doctorId,
            Long departmentId) {
    }

    /**
     * @param age derived from the date of birth every time this is built, so it
     *            is correct on the day it is read rather than on the day the
     *            patient registered
     */
    public record Response(Long id, String firstName, String lastName, String fullName,
                           LocalDate dateOfBirth, int age, boolean minor, Gender gender,
                           BloodGroup bloodGroup, String bloodGroupLabel, String phone,
                           String email, String address, String disease,
                           DoctorDto.Summary doctor, DepartmentDto.Summary department,
                           boolean active) {

        public static Response from(Patient patient) {
            return new Response(patient.getId(), patient.getFirstName(), patient.getLastName(),
                    patient.getFullName(), patient.getDateOfBirth(),
                    patient.getAge(), patient.isMinor(),
                    patient.getGender(), patient.getBloodGroup(),
                    patient.getBloodGroup() == null ? null : patient.getBloodGroup().getLabel(),
                    patient.getPhone(), patient.getEmail(), patient.getAddress(),
                    patient.getDisease(),
                    DoctorDto.Summary.from(patient.getDoctor()),
                    DepartmentDto.Summary.from(patient.getDepartment()),
                    patient.isActive());
        }
    }
}
