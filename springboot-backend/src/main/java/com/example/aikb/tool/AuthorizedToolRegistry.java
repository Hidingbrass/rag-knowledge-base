package com.example.aikb.tool;

import com.example.aikb.enums.ToolOperation;
import com.example.aikb.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 固定白名单注册表；模型输出不能动态创建工具或改变工具权限。 */
@Component
public class AuthorizedToolRegistry {

    private final Map<String, AuthorizedTool> tools;

    public AuthorizedToolRegistry(List<AuthorizedTool> registeredTools) {
        Map<String, AuthorizedTool> indexed = new HashMap<>();
        for (AuthorizedTool tool : registeredTools) {
            if (indexed.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("重复的授权工具: " + tool.name());
            }
        }
        this.tools = Map.copyOf(indexed);
    }

    public ToolExecutionResult execute(
            String toolName,
            ToolOperation requiredOperation,
            ToolExecutionContext context,
            JsonNode arguments
    ) {
        AuthorizedTool tool = tools.get(toolName);
        if (tool == null) {
            throw new BusinessException("工具未授权或不存在: " + toolName);
        }
        if (tool.operation() != requiredOperation) {
            throw new BusinessException("工具操作类型与授权不一致: " + toolName);
        }
        return tool.execute(context, arguments);
    }
}
