package com.example.lms.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberRequest(
        @Size(max = 150) String name,
        @Email(message = "Email must be valid") @Size(max = 150) String email,
        @Pattern(regexp = "^\\+?[0-9]{10,15}$",
                message = "Phone must be 10 to 15 digits") String phone,
        @Size(max = 300) String address,
        @Min(1) @Max(120) Integer membershipMonths) {
}
