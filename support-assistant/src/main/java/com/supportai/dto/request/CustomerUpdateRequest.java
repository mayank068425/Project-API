package com.supportai.dto.request;

import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(@Size(max = 200) String audience) {
}
