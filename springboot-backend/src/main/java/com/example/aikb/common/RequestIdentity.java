package com.example.aikb.common;

import com.example.aikb.exception.BusinessException;

/**
 * 请求身份参数校验与规范化。
 *
 * 生产环境默认从 JWT 读取 userId 与 department；旧调试模式才允许请求参数传入。
 * department 在个人学习版中表示学习方向，只作为用户与知识库元数据，不参与知识库授权。
 * Service 层统一调用这里，避免不同业务模块出现不同的空值判断和错误文案。
 */
public final class RequestIdentity {

    private RequestIdentity() {
    }

    public static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("用户 ID 不能为空");
        }
        return userId.trim();
    }

    public static String requireDepartment(String department) {
        if (department == null || department.isBlank()) {
            throw new BusinessException("部门不能为空");
        }
        return department.trim();
    }
}
