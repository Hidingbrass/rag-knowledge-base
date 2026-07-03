package com.example.aikb.repository;

import com.example.aikb.entity.JobResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobResumeVersionRepository extends JpaRepository<JobResumeVersion, UUID> {
    List<JobResumeVersion> findByUserIdOrderByUpdatedAtDesc(String userId);
}
