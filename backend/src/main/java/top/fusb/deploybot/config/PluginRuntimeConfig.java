package top.fusb.deploybot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.fusb.deploybot.plugin.runtime.DeploymentPluginRuntime;

/**
 * 注册部署插件运行时入口。
 */
@Configuration
public class PluginRuntimeConfig {

    /**
     * 注册部署插件运行时单例，供 backend 统一复用。
     *
     * @return 部署插件运行时
     */
    @Bean
    public DeploymentPluginRuntime deploymentPluginRuntime() {
        return new DeploymentPluginRuntime();
    }
}
