package com.example.aikb.repository;

import com.example.aikb.entity.JobResumeVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobResumeVersionRepository extends JpaRepository<JobResumeVersion, UUID> {
    List<JobResumeVersion> findByUserIdOrderByUpdatedAtDesc(String userId);

    Page<JobResumeVersion> findByUserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);
}
