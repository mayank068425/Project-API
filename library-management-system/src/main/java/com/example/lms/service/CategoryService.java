package com.example.lms.service;

import com.example.lms.dto.request.CategoryRequest;
import com.example.lms.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    /** Refused while books reference it, then deactivated rather than deleted. */
    void delete(Long id);

    CategoryResponse get(Long id);

    List<CategoryResponse> listAll();
}
