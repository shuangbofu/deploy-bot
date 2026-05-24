package top.fusb.deploybot.dto;

/**
 * 脚本执行前由平台或插件注入的 Shell 变量说明。
 *
 * @param key 变量名，不带 $ 前缀
 * @param expression 脚本里直接使用的表达式
 * @param description 变量含义
 * @param stage 变量可用阶段，BUILD / DEPLOY / ALL
 * @param source 变量来源，PLATFORM / RUNTIME / PLUGIN
 * @param contextKey 对应的平台上下文 key；无对应上下文时为空
 */
public record ShellVariableSummary(
        String key,
        String expression,
        String description,
        String stage,
        String source,
        String contextKey
) {
}
