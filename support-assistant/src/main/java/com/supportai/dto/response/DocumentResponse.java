package com.supportai.dto.response;

import com.supportai.entity.Classification;

import java.time.Instant;

public record DocumentResponse(Long id, String slug, String title, int versionNumber,
                               boolean currentVersion, Long supersedesId,
                               Classification classification, String service,
                               String sourceFilename, int chunkCount,
                               String embeddingModel, Integer embeddingDimension,
                               String uploadedBy, Instant createdAt) {
}
