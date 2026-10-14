package com.example.lms.serviceimpl;

import com.example.lms.dto.request.AuthorRequest;
import com.example.lms.dto.response.AuthorResponse;
import com.example.lms.dto.response.BookResponse;
import com.example.lms.entity.Author;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.DuplicateResourceException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.mapper.BookMapper;
import com.example.lms.repository.AuthorRepository;
import com.example.lms.repository.BookRepository;
import com.example.lms.service.AuthorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorServiceImpl implements AuthorService {

    private final AuthorRepository authors;
    private final BookRepository books;
    private final BookMapper bookMapper;

    public AuthorServiceImpl(AuthorRepository authors, BookRepository books,
                             BookMapper bookMapper) {
        this.authors = authors;
        this.books = books;
        this.bookMapper = bookMapper;
    }

    @Override
    @Transactional
    public AuthorResponse create(AuthorRequest request) {
        String name = request.name().strip();
        if (authors.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("An author named '" + name + "' already exists");
        }
        return AuthorResponse.from(authors.save(Author.builder()
                .name(name).biography(request.biography())
                .nationality(request.nationality()).active(true).build()));
    }

    @Override
    @Transactional
    public AuthorResponse update(Long id, AuthorRequest request) {
        Author author = require(id);
        String name = request.name().strip();

        authors.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new DuplicateResourceException("An author named '" + name
                        + "' already exists");
            }
        });

        author.setName(name);
        author.setBiography(request.biography());
        author.setNationality(request.nationality());
        return AuthorResponse.from(authors.save(author));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Author author = require(id);

        long inUse = books.findByAuthor(id, Pageable.ofSize(1)).getTotalElements();
        if (inUse > 0) {
            throw new BusinessRuleException(inUse + " book(s) are by this author; "
                    + "reassign them before removing the record");
        }

        author.setActive(false);
        authors.save(author);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorResponse get(Long id) {
        return AuthorResponse.from(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuthorResponse> search(String name, Pageable pageable) {
        Page<Author> page = (name == null || name.isBlank())
                ? authors.findAll(pageable)
                : authors.findByNameContainingIgnoreCase(name.strip(), pageable);
        return page.map(AuthorResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookResponse> booksByAuthor(Long authorId, Pageable pageable) {
        require(authorId);
        return books.findByAuthor(authorId, pageable).map(bookMapper::toResponse);
    }

    private Author require(Long id) {
        return authors.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("author", id));
    }
}
