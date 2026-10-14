package com.example.lms.dto.response;

import com.example.lms.entity.Category;

public record CategoryResponse(Long id, String name, String description, boolean active) {

    public static CategoryResponse from(Category category) {
        return category == null ? null : new CategoryResponse(category.getId(),
                category.getName(), category.getDescription(), category.isActive());
    }
}
