package com.example.aikb.repository;

import com.example.aikb.entity.JobGeneratedTask;
import com.example.aikb.entity.JobGeneratedTaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobGeneratedTaskRepository extends JpaRepository<JobGeneratedTask, UUID> {
    List<JobGeneratedTask> findByUserIdOrderByCreatedAtDesc(String userId);

    List<JobGeneratedTask> findByUserIdAndTaskTypeOrderByCreatedAtDesc(String userId, JobGeneratedTaskType taskType);
}
