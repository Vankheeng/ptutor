package com.ptutor.backend.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * API-facing page metadata. This keeps Spring Data's Page implementation out
 * of the HTTP response contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<?> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
