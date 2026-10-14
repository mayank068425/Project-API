package com.supportai.mapper;

import com.supportai.dto.response.ChunkResponse;
import com.supportai.dto.response.DocumentResponse;
import com.supportai.entity.KbDocument;
import com.supportai.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

    public DocumentResponse toResponse(KbDocument document) {
        if (document == null) {
            return null;
        }
        return new DocumentResponse(document.getId(), document.getSlug(), document.getTitle(),
                document.getVersionNumber(), document.isCurrentVersion(),
                document.getSupersedesId(), document.getClassification(), document.getService(),
                document.getSourceFilename(), document.getChunkCount(),
                // Exposed so an operator can check it against the model actually
                // configured. Vectors from different models are not comparable,
                // and a mismatch degrades silently rather than failing.
                document.getEmbeddingModel(), document.getEmbeddingDimension(),
                document.getUploadedBy(), document.getCreatedAt());
    }

    public ChunkResponse toResponse(RetrievedChunk retrieved) {
        if (retrieved == null) {
            return null;
        }
        return new ChunkResponse(
                retrieved.chunk().getId(),
                retrieved.citation(),
                retrieved.chunk().getBreadcrumb(),
                retrieved.chunk().getContent(),
                retrieved.chunk().isCompleteProcedure(),
                retrieved.chunk().getStepCount(),
                retrieved.literalMatch(),
                retrieved.fusedScore(),
                retrieved.contributions());
    }
}
