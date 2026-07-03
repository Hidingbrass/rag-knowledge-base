package com.example.aikb.dto.job;

import java.util.List;
import java.util.UUID;

public record JobTaskCompareResponse(
        UUID bestTaskId,
        int bestScore,
        double averageScore,
        List<String> commonMatchedSkills,
        List<String> commonMissingSkills,
        List<JobTaskCompareItem> items
) {
}
