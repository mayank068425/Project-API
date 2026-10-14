package com.banking.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A stable page shape.
 *
 * <p>Spring's {@code Page} serialises with a structure that has changed between
 * Spring Data versions and leaks the internal {@code Pageable}. Mapping to this
 * record means the wire format is ours and does not move under a dependency bump.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                              int totalPages, boolean first, boolean last) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
