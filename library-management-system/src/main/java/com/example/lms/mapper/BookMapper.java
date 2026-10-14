package com.example.lms.mapper;

import com.example.lms.dto.response.BookResponse;
import com.example.lms.entity.Book;
import org.springframework.stereotype.Component;

/**
 * Book entity to response.
 *
 * <p>Hand-written rather than generated. The mapping touches
 * {@code book.getAuthor().getName()}, which is a lazy association: this class
 * only works when the caller has already fetched it with a join, and a generated
 * mapper hides that requirement behind an annotation. Written out, the
 * dependency is visible, and the repository methods that must be used are named
 * in this comment rather than discovered through a LazyInitializationException.
 *
 * @see com.example.lms.repository.BookRepository#findByIdWithRelations
 * @see com.example.lms.repository.BookRepository#findAllWithRelations
 */
@Component
public class BookMapper {

    public BookResponse toResponse(Book book) {
        if (book == null) {
            return null;
        }
        return new BookResponse(
                book.getId(), book.getIsbn(), book.getTitle(),
                book.getAuthor().getId(), book.getAuthor().getName(),
                book.getCategory().getId(), book.getCategory().getName(),
                book.getPublisher(), book.getEdition(), book.getLanguage(),
                book.getShelfNumber(), book.getPublicationYear(),
                book.getTotalCopies(), book.getAvailableCopies(), book.getHeldCopies(),
                book.issuedCopies(), book.getReplacementCost(), book.getStatus(),
                book.isAvailableForLoan());
    }
}
