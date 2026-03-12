package com.testingautomation.testautomation.repo;

import com.testingautomation.testautomation.model.Run;
import com.testingautomation.testautomation.model.RunStatus;
import com.testingautomation.testautomation.model.ScenarioType;
import com.testingautomation.testautomation.pojo.RunFilterParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RunRepositoryImpl implements RunRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    @Override
    public List<Run> findByFilters(RunFilterParams params) {
        Query query = buildQuery(params);
        applySorting(query, params.getSort());
        applyPagination(query, params.getPage(), params.getSize());
        log.debug("Run filter query: {}", query);
        return mongoTemplate.find(query, Run.class);
    }

    @Override
    public long countByFilters(RunFilterParams params) {
        Query query = buildQuery(params);
        return mongoTemplate.count(query, Run.class);
    }

    @Override
    public Map<String, Object> aggregateFilterMeta(String projectId, String moduleId) {
        Map<String, Object> meta = new LinkedHashMap<>();

        Criteria base = Criteria.where("projectId").is(projectId)
                .and("moduleId").is(moduleId);

        // 1. Status counts
        meta.put("statusCounts", aggregateStatusCounts(base));

        // 2. Available scenario types
        meta.put("availableTypes", aggregateDistinctTypes(base));

        // 3. CreatedBy users
        meta.put("createdByUsers", aggregateDistinctField("createdBy", base));

        // 4. Distinct tags
        meta.put("availableTags", aggregateDistinctTags(base));

        // 5. Date range
        meta.put("dateRange", aggregateDateRange(base));

        return meta;
    }

    // ---------------------------------------------------------------
    // Query building
    // ---------------------------------------------------------------

    private Query buildQuery(RunFilterParams p) {
        List<Criteria> criteriaList = new ArrayList<>();

        // Required — always filter by project + module
        criteriaList.add(Criteria.where("projectId").is(p.getProjectId()));
        criteriaList.add(Criteria.where("moduleId").is(p.getModuleId()));

        // Status filter (multi-value OR)
        if (!CollectionUtils.isEmpty(p.getStatuses())) {
            List<String> statusNames = p.getStatuses().stream()
                    .map(RunStatus::name)
                    .collect(Collectors.toList());
            criteriaList.add(Criteria.where("status").in(statusNames));
        }

        // Scenario type filter — matches any scenario inside the run
        if (!CollectionUtils.isEmpty(p.getTypes())) {
            List<String> typeNames = p.getTypes().stream()
                    .map(ScenarioType::name)
                    .collect(Collectors.toList());
            criteriaList.add(Criteria.where("scenariosList.type").in(typeNames));
        }

        // CreatedBy filter
        if (StringUtils.hasText(p.getCreatedBy())) {
            criteriaList.add(Criteria.where("createdBy").is(p.getCreatedBy()));
        }

        // Date range filter
        if (p.getFrom() != null || p.getTo() != null) {
            Criteria dateCriteria = Criteria.where("createdAt");
            if (p.getFrom() != null) dateCriteria = dateCriteria.gte(p.getFrom());
            if (p.getTo() != null)   dateCriteria = dateCriteria.lte(p.getTo());
            criteriaList.add(dateCriteria);
        }

        // Tags filter — run must contain at least one of the requested tags
        if (!CollectionUtils.isEmpty(p.getTags())) {
            criteriaList.add(Criteria.where("tags").in(p.getTags()));
        }

        Query query = new Query();

        // Text search — uses Mongo $text index (runName + scenariosList.statement)
        if (StringUtils.hasText(p.getSearch())) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(p.getSearch()));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        return query;
    }

    // ---------------------------------------------------------------
    // Sorting
    // ---------------------------------------------------------------

    private void applySorting(Query query, String sort) {
        if (!StringUtils.hasText(sort)) {
            query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
            return;
        }
        boolean descending = sort.startsWith("-");
        String field = descending ? sort.substring(1) : sort;
        String mongoField = mapSortField(field);
        Sort.Direction direction = descending ? Sort.Direction.DESC : Sort.Direction.ASC;
        query.with(Sort.by(direction, mongoField));
    }

    private String mapSortField(String field) {
        return switch (field.toLowerCase()) {
            case "createdat"  -> "createdAt";
            case "updatedat"  -> "updatedAt";
            case "status"     -> "status";
            case "runname"    -> "runName";
            case "scenariocount" -> "scenarioCount";
            default           -> "createdAt";
        };
    }

    // ---------------------------------------------------------------
    // Pagination
    // ---------------------------------------------------------------

    private void applyPagination(Query query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100); // cap at 100
        query.skip((long) safePage * safeSize).limit(safeSize);
    }

    // ---------------------------------------------------------------
    // Aggregation helpers for filter meta
    // ---------------------------------------------------------------

    private Map<String, Long> aggregateStatusCounts(Criteria baseCriteria) {
        MatchOperation match = Aggregation.match(baseCriteria);
        GroupOperation group = Aggregation.group("status").count().as("count");
        ProjectionOperation project = Aggregation.project()
                .andExpression("_id").as("status")
                .and("count").as("count");

        Aggregation agg = Aggregation.newAggregation(match, group, project);
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "runs", Map.class);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map row : results.getMappedResults()) {
            String status = (String) row.get("status");
            Number count  = (Number) row.get("count");
            if (status != null) counts.put(status, count != null ? count.longValue() : 0L);
        }
        return counts;
    }

    private List<String> aggregateDistinctTypes(Criteria baseCriteria) {
        MatchOperation match = Aggregation.match(baseCriteria);
        UnwindOperation unwind = Aggregation.unwind("scenariosList");
        GroupOperation group = Aggregation.group("scenariosList.type");

        Aggregation agg = Aggregation.newAggregation(match, unwind, group);
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "runs", Map.class);

        return results.getMappedResults().stream()
                .map(m -> (String) m.get("_id"))
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> aggregateDistinctField(String field, Criteria baseCriteria) {
        MatchOperation match = Aggregation.match(baseCriteria);
        GroupOperation group = Aggregation.group(field);

        Aggregation agg = Aggregation.newAggregation(match, group);
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "runs", Map.class);

        return results.getMappedResults().stream()
                .map(m -> (String) m.get("_id"))
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> aggregateDistinctTags(Criteria baseCriteria) {
        MatchOperation match = Aggregation.match(baseCriteria);
        UnwindOperation unwind = Aggregation.unwind("tags");
        GroupOperation group = Aggregation.group("tags");

        Aggregation agg = Aggregation.newAggregation(match, unwind, group);
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "runs", Map.class);

        return results.getMappedResults().stream()
                .map(m -> (String) m.get("_id"))
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }

    private Map<String, Object> aggregateDateRange(Criteria baseCriteria) {
        MatchOperation match = Aggregation.match(baseCriteria);
        GroupOperation group = Aggregation.group()
                .min("createdAt").as("earliest")
                .max("createdAt").as("latest");

        Aggregation agg = Aggregation.newAggregation(match, group);
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "runs", Map.class);

        Map<String, Object> range = new LinkedHashMap<>();
        if (!results.getMappedResults().isEmpty()) {
            Map<?, ?> row = results.getMappedResults().get(0);
            range.put("earliest", row.get("earliest"));
            range.put("latest",   row.get("latest"));
        }
        return range;
    }
}