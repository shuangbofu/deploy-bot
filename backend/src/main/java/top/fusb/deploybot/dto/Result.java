package top.fusb.deploybot.dto;

import java.time.LocalDateTime;

/**
 * 统一的接口响应包装，前端通过 success/message/data 三段式消费接口结果。
 */
public record Result<T>(
        /** 请求是否处理成功。 */
        boolean success,
        /** 结果码，方便前后端做统一分支处理。 */
        String code,
        /** 大类结果说明。 */
        String message,
        /** 细分错误编号，便于排查具体失败场景。 */
        String subCode,
        /** 细分错误说明。 */
        String subMessage,
        /** 实际返回数据。 */
        T data,
        /** 响应生成时间。 */
        LocalDateTime timestamp
) {

    public static <T> Result<T> success(T data) {
        return new Result<>(true, "OK", "操作成功", null, null, data, LocalDateTime.now());
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(true, "OK", message, null, null, data, LocalDateTime.now());
    }

    public static <T> Result<T> failure(String code, String message) {
        return new Result<>(false, code, message, null, null, null, LocalDateTime.now());
    }

    public static <T> Result<T> failure(String code, String message, String subCode, String subMessage) {
        return new Result<>(false, code, message, subCode, subMessage, null, LocalDateTime.now());
    }
}
