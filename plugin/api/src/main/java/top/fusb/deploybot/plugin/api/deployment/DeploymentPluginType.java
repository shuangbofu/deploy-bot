package top.fusb.deploybot.plugin.api.deployment;

/**
 * 描述部署插件所代表的部署单元类型。
 */
public enum DeploymentPluginType {

    /**
     * Java 后端服务，例如 Spring Boot Jar。
     */
    JAVA_BACKEND_SERVICE,

    /**
     * Node 构建后的静态站点，无需进程接管。
     */
    NODE_STATIC_SITE,

    /**
     * 由多个子部署单元组合而成的复合部署方案。
     */
    COMPOSITE
}
