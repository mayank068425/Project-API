package com.example.lms.controller;

import com.example.lms.dto.request.BookRequest;
import com.example.lms.dto.response.ApiResponse;
import com.example.lms.dto.response.BookResponse;
import com.example.lms.dto.response.PageResponse;
import com.example.lms.service.BookService;
import com.example.lms.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/books")
@Tag(name = "Books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /**
     * Lists and filters the catalogue.
     *
     * <p>All filters share one endpoint rather than having {@code /by-category},
     * {@code /by-author} and {@code /by-publisher} beside it. Separate endpoints
     * cannot be combined, and the first request for "programming books from one
     * publisher" needs a fourth.
     */
    @GetMapping
    @Operation(summary = "List books, filtered by category, author, publisher or availability")
    public ApiResponse<PageResponse<BookResponse>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String publisher,
            @RequestParam(required = false) Boolean availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "title") String sortBy) {
        return ApiResponse.ok(PageResponse.from(
                bookService.search(null, categoryId, authorId, publisher, availableOnly,
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE),
                                Sort.by(sortBy))),
                Function.identity()));
    }

    @GetMapping("/search")
    @Operation(summary = "Free-text search across title, ISBN, publisher and author")
    public ApiResponse<PageResponse<BookResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                bookService.search(q, null, null, null, null,
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    @GetMapping("/available")
    public ApiResponse<PageResponse<BookResponse>> available(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                bookService.search(null, null, null, null, true,
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    @GetMapping("/issued")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ApiResponse<PageResponse<BookResponse>> issued(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                bookService.issuedBooks(
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(bookService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<BookResponse>> create(
            @Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.create(request);
        return ResponseEntity.created(java.net.URI.create("/api/books/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ApiResponse<BookResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody BookRequest request) {
        return ApiResponse.ok(bookService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Retire a book",
            description = "Soft delete, refused while copies are out. A hard delete would "
                    + "orphan every loan and fine referencing it.")
    public ApiResponse<String> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ApiResponse.ok("Book retired");
    }
}
