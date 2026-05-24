package top.fusb.deploybot.plugin.api.deployment.form;

/**
 * 描述插件表单字段值最终写入的平台目标范围。
 */
public enum PluginFormFieldBindingScope {

    /**
     * 写入流水线的插件配置 JSON，由对应插件在变量组装或部署能力中自行消费。
     */
    PLUGIN_CONFIG,

    /**
     * 写入流水线默认变量池，由后续模板渲染与插件变量装配统一消费。
     */
    PIPELINE_VARIABLE
}
