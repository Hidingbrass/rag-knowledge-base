package com.example.aikb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP 鉴权策略配置。
 *
 * 默认关闭旧版身份参数兼容模式，所有业务 API 必须携带有效 JWT。
 * 只有本地联调或兼容旧测试时，才允许显式开启 legacy 模式。
 */
@ConfigurationProperties(prefix = "auth.security")
public record AuthSecurityProperties(
        boolean allowLegacyIdentityParameters
) {
}
