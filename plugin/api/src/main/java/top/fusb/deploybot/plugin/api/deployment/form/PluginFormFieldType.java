package top.fusb.deploybot.plugin.api.deployment.form;

/**
 * 描述插件配置表单字段类型。
 */
public enum PluginFormFieldType {

    /**
     * 单行文本。
     */
    TEXT,

    /**
     * 多行脚本或说明文本。
     */
    TEXTAREA,

    /**
     * 需要代码编辑器展示的多行内容，例如 YAML 或 Shell。
     */
    CODE,

    /**
     * 布尔开关。
     */
    BOOLEAN,

    /**
     * 预定义选项。
     */
    SELECT
}
