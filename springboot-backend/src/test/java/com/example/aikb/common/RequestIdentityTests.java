package com.example.aikb.common;

import com.example.aikb.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdentityTests {

    @Test
    void requireUserIdShouldTrimValue() {
        assertThat(RequestIdentity.requireUserId(" user-1 ")).isEqualTo("user-1");
    }

    @Test
    void requireUserIdShouldRejectBlankValue() {
        assertThatThrownBy(() -> RequestIdentity.requireUserId(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户 ID 不能为空");
    }

    @Test
    void requireDepartmentShouldTrimValue() {
        assertThat(RequestIdentity.requireDepartment(" 研发部 ")).isEqualTo("研发部");
    }

    @Test
    void requireDepartmentShouldRejectBlankValue() {
        assertThatThrownBy(() -> RequestIdentity.requireDepartment(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门不能为空");
    }
}
