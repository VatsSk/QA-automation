package com.testingautomation.testautomation.repo;

import com.testingautomation.testautomation.model.TfUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// ─── User ──────────────────────────────────────────────────────────────────
@Repository
public interface TfUserRepository extends MongoRepository<TfUser, String> {

    Optional<TfUser> findByUsername(String username);

    Optional<TfUser> findByEmail(String email);

    Optional<TfUser> findByUsernameAndEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}

