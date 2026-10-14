package com.example.lms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthorRequest(
        @NotBlank(message = "Author name is required") @Size(max = 150) String name,
        @Size(max = 1000) String biography,
        @Size(max = 80) String nationality) {
}
