package com.example.lms.service;

import com.example.lms.dto.request.BookRequest;
import com.example.lms.dto.response.BookResponse;
import com.example.lms.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookService {

    BookResponse create(BookRequest request);

    BookResponse update(Long id, BookRequest request);

    /**
     * Retires a book. Soft, and refused while copies are out.
     *
     * <p>A hard delete would orphan every loan and fine referencing it, and a
     * member disputing a charge for a since-removed book still deserves an
     * answer.
     */
    void delete(Long id);

    BookResponse get(Long id);

    /** One method behind every filter, because separate ones cannot be combined. */
    Page<BookResponse> search(String query, Long categoryId, Long authorId, String publisher,
                              Boolean availableOnly, Pageable pageable);

    Page<BookResponse> issuedBooks(Pageable pageable);

    /** For circulation, which needs the entity rather than a response. */
    Book requireBook(Long id);
}
