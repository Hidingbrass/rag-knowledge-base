package com.example.aikb.tool;

import com.example.aikb.enums.ToolOperation;
import com.fasterxml.jackson.databind.JsonNode;

/** 只有显式注册的实现才能被聊天路由调用。 */
public interface AuthorizedTool {
    String name();

    ToolOperation operation();

    ToolExecutionResult execute(ToolExecutionContext context, JsonNode arguments);
}
