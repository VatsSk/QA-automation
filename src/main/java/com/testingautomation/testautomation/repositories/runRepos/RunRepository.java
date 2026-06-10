package com.testingautomation.testautomation.repositories.runRepos;

import com.testingautomation.testautomation.entities.Run;
import com.testingautomation.testautomation.enums.RunStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunRepository extends MongoRepository<Run, String>, RunRepositoryCustom {

    Run getRunById(String id);

    List<Run> findByProjectIdAndModuleIdOrderByCreatedAtDesc(String projectId, String moduleId);

    long countByProjectIdAndModuleId(String projectId, String moduleId);

    long countByProjectIdAndModuleIdAndStatus(String projectId, String moduleId, RunStatus status);

    void deleteAllByModuleId(String moduleId);

    void deleteAllByProjectId(String projectId);
    @Query(
            value = "{ 'moduleId': ?0 ,'status': {$ne: 'RUNNING'}}",
            fields = "{ '_id': 1}"
    )
    List<Run> getRunsByModuleId(String id);
    @Query(
            value = "{'createdBy': ?0,'status': {$ne : 'RUNNING'}}",
            fields = "{'_id': 1,'runName': 1}"
    )
    List<Run> getAllRunsByCreatedBy(String createdBy);

    @Query("{ '_id' : { $in : ?0 } }")
    @Update("{ '$set' : { 'bulkRun' : ?1,'status': ?2} }")
    void updateBulkRunAndStatusForRuns(List<String> runIds,boolean value,RunStatus status);
    @Query("{ '_id' : { $in : ?0 } }")
    @Update("{ '$set' : { 'bulkRun' : ?1} }")
    void updateBulkRunForRuns(List<String> runIds,boolean value);
}