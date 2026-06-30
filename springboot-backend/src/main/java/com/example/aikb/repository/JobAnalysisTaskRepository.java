package com.example.aikb.repository;

import com.example.aikb.entity.JobAnalysisTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobAnalysisTaskRepository extends JpaRepository<JobAnalysisTask, UUID> {
    List<JobAnalysisTask> findByUserIdOrderByCreatedAtDesc(String userId);
}
