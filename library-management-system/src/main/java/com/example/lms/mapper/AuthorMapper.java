package com.example.lms.mapper;

import com.example.lms.dto.response.AuthorResponse;
import com.example.lms.dto.response.CategoryResponse;
import com.example.lms.entity.Author;
import com.example.lms.entity.Category;
import org.springframework.stereotype.Component;

/**
 * Author and category mapping.
 *
 * <p>Category lives here rather than in its own class because both are two-field
 * lookups with identical shape; a separate CategoryMapper would be a file
 * containing one method and no decisions.
 */
@Component
public class AuthorMapper {

    public AuthorResponse toResponse(Author author) {
        return AuthorResponse.from(author);
    }

    public CategoryResponse toResponse(Category category) {
        return CategoryResponse.from(category);
    }
}
