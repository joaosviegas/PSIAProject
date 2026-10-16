package pt.psia.profile.web.dto;

import java.util.List;

/**
 * A DTO for a page of items.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {}
