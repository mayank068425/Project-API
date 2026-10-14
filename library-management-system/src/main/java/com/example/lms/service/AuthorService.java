package com.example.lms.service;

import com.example.lms.dto.request.AuthorRequest;
import com.example.lms.dto.response.AuthorResponse;
import com.example.lms.dto.response.BookResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuthorService {

    AuthorResponse create(AuthorRequest request);

    AuthorResponse update(Long id, AuthorRequest request);

    void delete(Long id);

    AuthorResponse get(Long id);

    Page<AuthorResponse> search(String name, Pageable pageable);

    Page<BookResponse> booksByAuthor(Long authorId, Pageable pageable);
}
