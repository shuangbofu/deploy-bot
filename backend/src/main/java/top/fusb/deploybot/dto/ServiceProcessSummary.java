package top.fusb.deploybot.dto;

/**
 * 可绑定到服务的进程摘要。
 */
public record ServiceProcessSummary(
        Long pid,
        String command,
        String commandLine
) {
}
