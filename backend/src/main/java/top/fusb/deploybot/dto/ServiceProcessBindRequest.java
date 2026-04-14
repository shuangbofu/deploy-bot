package top.fusb.deploybot.dto;

/**
 * 手动绑定服务进程请求。
 */
public record ServiceProcessBindRequest(
        Long pid
) {
}
