package com.testingautomation.testautomation.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paginated response wrapper.
 * Used for GET /api/projects/{p}/modules/{m}/runs responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> results;
    private long totalCount;
    private int page;
    private int size;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
}
