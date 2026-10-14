package com.example.lms.serviceimpl;

import com.example.lms.dto.request.CategoryRequest;
import com.example.lms.dto.response.CategoryResponse;
import com.example.lms.entity.Category;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.DuplicateResourceException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.BookRepository;
import com.example.lms.repository.CategoryRepository;
import com.example.lms.service.CategoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categories;
    private final BookRepository books;

    public CategoryServiceImpl(CategoryRepository categories, BookRepository books) {
        this.categories = categories;
        this.books = books;
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().strip();
        // Checked here for a readable message; the unique index is the actual
        // guarantee, because this check loses the race against a simultaneous
        // insert of the same name.
        if (categories.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A category named '" + name + "' already exists");
        }
        return CategoryResponse.from(categories.save(Category.builder()
                .name(name).description(request.description()).active(true).build()));
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = require(id);
        String name = request.name().strip();

        // Only a clash with a different row is a duplicate, or renaming a
        // category to its own current name would be rejected.
        categories.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new DuplicateResourceException("A category named '" + name
                        + "' already exists");
            }
        });

        category.setName(name);
        category.setDescription(request.description());
        return CategoryResponse.from(categories.save(category));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = require(id);

        long inUse = books.findByCategory(id, Pageable.ofSize(1)).getTotalElements();
        if (inUse > 0) {
            // Deleting would leave those books pointing at nothing and the
            // category filter would silently return fewer results than the
            // catalogue holds.
            throw new BusinessRuleException(inUse + " book(s) are in this category; "
                    + "move them before removing it");
        }

        category.setActive(false);
        categories.save(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return CategoryResponse.from(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categories.findByActiveTrueOrderByName().stream()
                .map(CategoryResponse::from).toList();
    }

    private Category require(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("category", id));
    }
}
