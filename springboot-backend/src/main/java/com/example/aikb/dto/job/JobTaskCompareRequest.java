package com.example.aikb.dto.job;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record JobTaskCompareRequest(
        String userId,

        @NotEmpty(message = "对比任务不能为空")
        @Size(min = 2, max = 5, message = "一次需要对比 2 到 5 条求职分析记录")
        List<UUID> taskIds
) {
}
