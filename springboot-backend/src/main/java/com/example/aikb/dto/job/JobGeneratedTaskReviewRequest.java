package com.example.aikb.dto.job;

import com.example.aikb.entity.JobReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JobGeneratedTaskReviewRequest(
        String userId,
        @NotNull JobReviewStatus status,
        @Size(max = 1000) String comment
) {
}
