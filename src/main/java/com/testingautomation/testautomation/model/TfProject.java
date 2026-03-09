package com.testingautomation.testautomation.model;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "tf_projects")
public class TfProject {

    @Id
    private String id;

    @Indexed
    private String userId;          // references TfUser._id

    private String name;
    private String description;
    private String baseUrl;         // runner URL: http://host:port (where /run-auth lives)

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── constructors ────────────────────────────────────────────
    public TfProject() {}

    // ── getters / setters ────────────────────────────────────────
    public String getId()           { return id; }
    public void   setId(String id)  { this.id = id; }

    public String getUserId()              { return userId; }
    public void   setUserId(String userId) { this.userId = userId; }

    public String getName()           { return name; }
    public void   setName(String name){ this.name = name; }

    public String getDescription()                    { return description; }
    public void   setDescription(String description)  { this.description = description; }

    public String getBaseUrl()              { return baseUrl; }
    public void   setBaseUrl(String baseUrl){ this.baseUrl = baseUrl; }

    public LocalDateTime getCreatedAt()                   { return createdAt; }
    public void          setCreatedAt(LocalDateTime v)    { this.createdAt = v; }

    public LocalDateTime getUpdatedAt()                   { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime v)    { this.updatedAt = v; }
}