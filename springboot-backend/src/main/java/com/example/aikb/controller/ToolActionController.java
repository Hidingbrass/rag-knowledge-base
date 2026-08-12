package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.tool.ConfirmToolActionRequest;
import com.example.aikb.dto.tool.ToolActionResponse;
import com.example.aikb.security.AuthenticatedUser;
import com.example.aikb.service.ToolActionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.example.aikb.common.CurrentUserIdentity.departmentOrRequestParam;
import static com.example.aikb.common.CurrentUserIdentity.userIdOrRequestParam;

@RestController
@RequestMapping("/api/chat/tool-actions")
public class ToolActionController {
    private final ToolActionService toolActionService;

    public ToolActionController(ToolActionService toolActionService) {
        this.toolActionService = toolActionService;
    }

    @PostMapping("/{actionId}/confirm")
    public ApiResponse<ToolActionResponse> confirm(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID actionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @Valid @RequestBody ConfirmToolActionRequest request
    ) {
        return ApiResponse.ok(toolActionService.confirm(
                actionId,
                userIdOrRequestParam(currentUser, userId),
                departmentOrRequestParam(currentUser, department),
                request.confirmation()
        ));
    }
}
