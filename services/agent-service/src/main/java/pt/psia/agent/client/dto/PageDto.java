package pt.psia.agent.client.dto;

import java.util.List;

/**
 * A DTO for a page of items.
 */
public record PageDto<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
