package com.testingautomation.testautomation.repo;


import com.testingautomation.testautomation.model.Run;
import com.testingautomation.testautomation.pojo.RunFilterParams;

import java.util.List;
import java.util.Map;

public interface RunRepositoryCustom {

    /**
     * Execute a compound filter query with pagination/sorting.
     */
    List<Run> findByFilters(RunFilterParams params);

    /**
     * Count total matching documents for pagination header.
     */
    long countByFilters(RunFilterParams params);

    /**
     * Aggregate metadata for filter dropdowns:
     * - status counts { "PASSED": 12, "FAILED": 3, ... }
     * - available scenario types in this project/module
     * - list of createdBy users
     * - all distinct tags
     * - earliest and latest createdAt dates
     */
    Map<String, Object> aggregateFilterMeta(String projectId, String moduleId);
}