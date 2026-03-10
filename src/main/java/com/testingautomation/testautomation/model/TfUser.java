package com.testingautomation.testautomation.model;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "users")
public class TfUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String displayName;
    private String role = "user";           // user | admin
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── constructors ────────────────────────────────────────────
    public TfUser() {}

    public TfUser(String username, String email) {
        this.username = username;
        this.email    = email;
        this.createdAt = LocalDateTime.now();
    }

    // ── getters / setters ────────────────────────────────────────
    public String getId()           { return id; }
    public void   setId(String id)  { this.id = id; }

    public String getUsername()               { return username; }
    public void   setUsername(String username){ this.username = username; }

    public String getEmail()              { return email; }
    public void   setEmail(String email)  { this.email = email; }

    public String getDisplayName()                    { return displayName; }
    public void   setDisplayName(String displayName)  { this.displayName = displayName; }

    public String getRole()           { return role; }
    public void   setRole(String role){ this.role = role; }

    public LocalDateTime getCreatedAt()                   { return createdAt; }
    public void          setCreatedAt(LocalDateTime createdAt){ this.createdAt = createdAt; }
}
