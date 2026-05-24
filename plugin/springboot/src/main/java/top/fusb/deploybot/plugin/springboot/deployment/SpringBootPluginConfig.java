package top.fusb.deploybot.plugin.springboot.deployment;

/**
 * Spring Boot 插件运行配置。
 * <p>
 * 平台只负责把插件配置 JSON 透传给插件 common 抽象层，具体字段含义由 Spring Boot 插件自己的配置类表达。
 * </p>
 *
 * @param springProfile Spring Profile，非空时会注入 {@code --spring.profiles.active}
 * @param runtimeConfigYaml application.yml 原文，非空时会写入目标主机并注入附加配置路径
 */
public record SpringBootPluginConfig(
        String springProfile,
        String runtimeConfigYaml
) {
}
