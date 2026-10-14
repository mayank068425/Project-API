package com.example.lms.dto.response;

import com.example.lms.entity.Author;

public record AuthorResponse(Long id, String name, String biography, String nationality,
                             boolean active) {

    public static AuthorResponse from(Author author) {
        return author == null ? null : new AuthorResponse(author.getId(), author.getName(),
                author.getBiography(), author.getNationality(), author.isActive());
    }
}
