package com.example.aikb.repository;

import com.example.aikb.entity.JobFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobFavoriteRepository extends JpaRepository<JobFavorite, UUID> {
    List<JobFavorite> findByUserIdOrderByCreatedAtDesc(String userId);

    Page<JobFavorite> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
