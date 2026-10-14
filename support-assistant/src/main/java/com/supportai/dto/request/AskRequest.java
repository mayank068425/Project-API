package com.supportai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param service optional. Scoping to the service under investigation cuts out
 *                runbooks for unrelated systems, which are the most convincing
 *                wrong answers: correct procedures for the wrong component.
 */
public record AskRequest(
        @NotBlank(message = "A question is required") @Size(max = 4000) String question,
        @Size(max = 100) String service) {
}
