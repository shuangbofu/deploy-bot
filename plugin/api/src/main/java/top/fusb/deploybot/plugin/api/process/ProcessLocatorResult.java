package top.fusb.deploybot.plugin.api.process;

/**
 * PID 发现结果。
 */
public record ProcessLocatorResult(
        Long pid,
        String sourceDescription,
        String diagnostics,
        String keyword
) {
}
