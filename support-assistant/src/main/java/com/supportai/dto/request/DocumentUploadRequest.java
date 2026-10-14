package com.supportai.dto.request;

import com.supportai.entity.Classification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Upload or supersede a knowledge-base document.
 *
 * @param slug stable across versions. Uploading against an existing slug creates
 *             a new version and deletes the previous version's chunks — see
 *             {@code KbDocument} for why leaving them is the quiet failure of
 *             most RAG systems.
 */
public record DocumentUploadRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9-]{3,200}$",
                message = "Slug must be lowercase letters, digits and hyphens") String slug,
        @NotBlank @Size(max = 300) String title,
        @NotBlank String content,
        Classification classification,
        @Size(max = 100) String service,
        @Size(max = 300) String sourceFilename) {
}
