package com.testingautomation.testautomation.repo;

import com.testingautomation.testautomation.model.TfProject;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TfProjectRepository extends MongoRepository<TfProject, String> {

    List<TfProject> findByUserIdOrderByCreatedAtDesc(String userId);

    List<TfProject> findAllByOrderByCreatedAtDesc();

    boolean existsByUserIdAndName(String userId, String name);
}