package com.supportai.controller;

import com.supportai.dto.request.DocumentUploadRequest;
import com.supportai.dto.response.ApiResponse;
import com.supportai.dto.response.ChunkResponse;
import com.supportai.dto.response.DocumentResponse;
import com.supportai.dto.response.PageResponse;
import com.supportai.service.KnowledgeService;
import com.supportai.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/knowledge")
@Tag(name = "Knowledge base")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/documents")
    @PreAuthorize("hasAnyRole('LEAD','ADMIN')")
    @Operation(summary = "Upload or supersede a document",
            description = "Uploading against an existing slug creates a new version and "
                    + "DELETES the previous version's chunks. Leaving them means the "
                    + "assistant keeps answering from a procedure somebody corrected, with "
                    + "a citation that resolves to a real document.")
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @Valid @RequestBody DocumentUploadRequest request) {
        DocumentResponse created = knowledgeService.upload(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created,
                created.chunkCount() + " chunks indexed with " + created.embeddingModel()));
    }

    @GetMapping("/documents")
    @Operation(summary = "Documents you are cleared to see")
    public ApiResponse<PageResponse<DocumentResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                knowledgeService.list(
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<DocumentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeService.get(id));
    }

    @GetMapping("/documents/slug/{slug}/versions")
    @Operation(summary = "Version history for a document")
    public ApiResponse<List<DocumentResponse>> versions(@PathVariable String slug) {
        return ApiResponse.ok(knowledgeService.versionsOf(slug));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Hybrid search.
     *
     * <p>Works with the assistant disabled — this is the fallback that makes the
     * assistant an accelerator rather than a dependency.
     */
    @GetMapping("/search")
    @Operation(summary = "Search runbooks",
            description = "Semantic, full-text and literal token matching, fused on rank. "
                    + "Works with the assistant disabled. Results scoped to your clearance "
                    + "inside each query, not filtered afterwards.")
    public ApiResponse<List<ChunkResponse>> search(
            @RequestParam String q,
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "8") int topK) {
        List<ChunkResponse> results = knowledgeService.search(q, service, topK);
        long partial = results.stream().filter(c -> !c.completeProcedure()).count();
        return ApiResponse.ok(results, partial == 0 ? null
                : partial + " result(s) contain numbered steps that may not be the full "
                        + "procedure; open the document before acting on them.");
    }
}
