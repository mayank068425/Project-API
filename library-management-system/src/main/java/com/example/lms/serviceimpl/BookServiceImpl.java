package com.example.lms.serviceimpl;

import com.example.lms.dto.request.BookRequest;
import com.example.lms.dto.response.BookResponse;
import com.example.lms.entity.Book;
import com.example.lms.entity.BookStatus;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.DuplicateResourceException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.mapper.BookMapper;
import com.example.lms.repository.AuthorRepository;
import com.example.lms.repository.BookRepository;
import com.example.lms.repository.CategoryRepository;
import com.example.lms.service.AuditService;
import com.example.lms.service.BookService;
import com.example.lms.validation.BookValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookServiceImpl implements BookService {

    private static final Logger log = LoggerFactory.getLogger(BookServiceImpl.class);

    private final BookRepository books;
    private final AuthorRepository authors;
    private final CategoryRepository categories;
    private final BookMapper mapper;
    private final BookValidator validator;
    private final AuditService audit;

    public BookServiceImpl(BookRepository books, AuthorRepository authors,
                           CategoryRepository categories, BookMapper mapper,
                           BookValidator validator, AuditService audit) {
        this.books = books;
        this.authors = authors;
        this.categories = categories;
        this.mapper = mapper;
        this.validator = validator;
        this.audit = audit;
    }

    @Override
    @Transactional
    public BookResponse create(BookRequest request) {
        validator.validateForCreate(request);

        String isbn = normaliseIsbn(request.isbn());
        if (!validator.isChecksumValid(isbn)) {
            // The DTO pattern confirms the shape; this confirms the number could
            // have been issued. A transposed pair passes the pattern and fails
            // here, which is the realistic error when someone types an ISBN off a
            // copyright page.
            throw new BusinessRuleException("That ISBN's check digit is wrong. "
                    + "Check the digits and try again.");
        }
        if (books.existsByIsbn(isbn)) {
            throw new DuplicateResourceException("A book with ISBN " + isbn + " already exists");
        }

        Book book = Book.builder()
                .isbn(isbn)
                .title(request.title().strip())
                .author(authors.findById(request.authorId()).orElseThrow(
                        () -> ResourceNotFoundException.of("author", request.authorId())))
                .category(categories.findById(request.categoryId()).orElseThrow(
                        () -> ResourceNotFoundException.of("category", request.categoryId())))
                .publisher(request.publisher())
                .edition(request.edition())
                .language(request.language() == null ? "English" : request.language())
                .shelfNumber(request.shelfNumber())
                .publicationYear(request.publicationYear())
                .totalCopies(request.totalCopies())
                // A new title starts entirely on the shelf. Setting these
                // independently is how the counters begin life disagreeing.
                .availableCopies(request.totalCopies())
                .heldCopies(0)
                .replacementCost(request.replacementCost())
                .status(BookStatus.ACTIVE)
                .build();
        books.save(book);

        audit.record(AuditService.BOOK_ADDED, "Book", isbn,
                request.title() + ", " + request.totalCopies() + " copies");
        return mapper.toResponse(book);
    }

    @Override
    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = books.findByIdWithRelations(id)
                .orElseThrow(() -> ResourceNotFoundException.of("book", id));
        validator.validateForUpdate(request, book);

        String before = summarise(book);

        if (request.title() != null) {
            book.setTitle(request.title().strip());
        }
        if (request.authorId() != null) {
            book.setAuthor(authors.findById(request.authorId()).orElseThrow(
                    () -> ResourceNotFoundException.of("author", request.authorId())));
        }
        if (request.categoryId() != null) {
            book.setCategory(categories.findById(request.categoryId()).orElseThrow(
                    () -> ResourceNotFoundException.of("category", request.categoryId())));
        }
        if (request.publisher() != null) {
            book.setPublisher(request.publisher());
        }
        if (request.edition() != null) {
            book.setEdition(request.edition());
        }
        if (request.language() != null) {
            book.setLanguage(request.language());
        }
        if (request.shelfNumber() != null) {
            book.setShelfNumber(request.shelfNumber());
        }
        if (request.publicationYear() != null) {
            book.setPublicationYear(request.publicationYear());
        }
        if (request.replacementCost() != null) {
            book.setReplacementCost(request.replacementCost());
        }
        if (request.totalCopies() != null) {
            // Refuses to cut stock below what is currently out.
            book.adjustTotalCopies(request.totalCopies());
        }
        if (request.status() != null) {
            book.setStatus(BookStatus.valueOf(request.status().toUpperCase()));
        }

        books.save(book);
        audit.recordChange(AuditService.BOOK_UPDATED, "Book", book.getIsbn(),
                before, summarise(book));
        return mapper.toResponse(book);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Book book = books.findByIdWithRelations(id)
                .orElseThrow(() -> ResourceNotFoundException.of("book", id));

        if (book.issuedCopies() > 0) {
            throw new BusinessRuleException("\"" + book.getTitle() + "\" has "
                    + book.issuedCopies() + " copies on loan or held and cannot be removed");
        }

        // Retired, not deleted. A hard delete orphans every loan and fine that
        // references it, and a member disputing a charge for a since-removed book
        // still deserves an answer.
        book.setStatus(BookStatus.RETIRED);
        book.setAvailableCopies(0);
        books.save(book);

        audit.record(AuditService.BOOK_DELETED, "Book", book.getIsbn(), book.getTitle());
        log.info("retired book {} ({})", book.getTitle(), book.getIsbn());
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse get(Long id) {
        return mapper.toResponse(books.findByIdWithRelations(id)
                .orElseThrow(() -> ResourceNotFoundException.of("book", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookResponse> search(String query, Long categoryId, Long authorId,
                                     String publisher, Boolean availableOnly,
                                     Pageable pageable) {
        Page<Book> page;
        if (query != null && !query.isBlank()) {
            page = books.search(query.strip(), pageable);
        } else if (categoryId != null) {
            page = books.findByCategory(categoryId, pageable);
        } else if (authorId != null) {
            page = books.findByAuthor(authorId, pageable);
        } else if (publisher != null && !publisher.isBlank()) {
            page = books.findByPublisher(publisher.strip(), pageable);
        } else if (Boolean.TRUE.equals(availableOnly)) {
            page = books.findAvailable(pageable);
        } else {
            page = books.findAllWithRelations(pageable);
        }
        return page.map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookResponse> issuedBooks(Pageable pageable) {
        return books.findWithCopiesOut(pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Book requireBook(Long id) {
        return books.findByIdWithRelations(id)
                .orElseThrow(() -> ResourceNotFoundException.of("book", id));
    }

    /** Hyphens and spaces are presentation; the stored form is digits only. */
    static String normaliseIsbn(String isbn) {
        return isbn.replaceAll("[- ]", "").toUpperCase();
    }

    private String summarise(Book book) {
        return "title=" + book.getTitle()
                + ", author=" + book.getAuthor().getName()
                + ", category=" + book.getCategory().getName()
                + ", copies=" + book.getTotalCopies()
                + ", status=" + book.getStatus();
    }
}
