package com.testingautomation.testautomation.config;


import com.mongodb.client.MongoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
@EnableMongoAuditing
@RequiredArgsConstructor
public class MongoConfig {

    private final MongoTemplate mongoTemplate;

    @Value("${spring.data.mongodb.database:qadb}")
    private String databaseName;

    /**
     * Ensure the text index on runs exists for full-text search.
     * Spring Data's @TextIndexed handles field-level config,
     * but we ensure it's created at startup.
     *
     * Indexed fields:
     *   - runName         (weight 3 — primary search target)
     *   - scenariosList.statement (weight 1 — secondary)
     */
    @PostConstruct
    public void ensureIndexes() {
        try {
            ensureTextIndex();
            log.info("MongoDB indexes verified/created successfully");
        } catch (Exception e) {
            log.warn("Index creation warning (may already exist): {}", e.getMessage());
        }
    }

    private void ensureTextIndex() {
        Document indexKeys = new Document()
                .append("runName", "text")
                .append("scenariosList.statement", "text");

        Document indexOptions = new Document()
                .append("name", "run_text_search_idx")
                .append("weights", new Document()
                        .append("runName", 3)
                        .append("scenariosList.statement", 1));

        try {
            mongoTemplate.getDb()
                    .getCollection("runs")
                    .createIndex(indexKeys, new com.mongodb.client.model.IndexOptions()
                            .name("run_text_search_idx")
                            .weights(new Document()
                                    .append("runName", 3)
                                    .append("scenariosList.statement", 1)));
            log.debug("Text index on runs collection ensured");
        } catch (Exception e) {
            // Index already exists — that's fine
            log.debug("Text index already exists: {}", e.getMessage());
        }
    }
}