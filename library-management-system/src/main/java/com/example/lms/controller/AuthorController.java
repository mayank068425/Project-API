package com.example.lms.controller;

import com.example.lms.dto.request.AuthorRequest;
import com.example.lms.dto.response.ApiResponse;
import com.example.lms.dto.response.AuthorResponse;
import com.example.lms.dto.response.BookResponse;
import com.example.lms.dto.response.PageResponse;
import com.example.lms.service.AuthorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Function;

@RestController
@RequestMapping("/api/authors")
@Tag(name = "Authors")
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuthorResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                authorService.search(name, PageRequest.of(page, Math.min(size, 100))),
                Function.identity()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AuthorResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(authorService.get(id));
    }

    @GetMapping("/{id}/books")
    @Operation(summary = "Books by this author")
    public ApiResponse<PageResponse<BookResponse>> books(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                authorService.booksByAuthor(id, PageRequest.of(page, Math.min(size, 100))),
                Function.identity()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ApiResponse<AuthorResponse> create(@Valid @RequestBody AuthorRequest request) {
        return ApiResponse.ok(authorService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ApiResponse<AuthorResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody AuthorRequest request) {
        return ApiResponse.ok(authorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ApiResponse<String> delete(@PathVariable Long id) {
        authorService.delete(id);
        return ApiResponse.ok("Author removed");
    }
}
