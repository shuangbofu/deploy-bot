package top.fusb.deploybot.dto;

import java.time.LocalDateTime;

/**
 * 统一接口错误响应。
 */
public record ApiErrorResponse(
        /** 错误发生时间。 */
        LocalDateTime timestamp,
        /** HTTP 状态码。 */
        int status,
        /** HTTP 错误名称。 */
        String error,
        /** 业务错误说明。 */
        String message,
        /** 当前请求路径。 */
        String path
) {
}
