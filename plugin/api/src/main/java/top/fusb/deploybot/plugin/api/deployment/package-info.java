/**
 * 部署类型插件的核心契约。
 * <p>
 * 该包只保留最顶层、最稳定的模型：插件接口、上下文、计划与类型枚举。
 * 更细分的表单、流程、变量、模板与元信息模型分别放在各自子包中，
 * 避免 deployment 根包继续平铺过多定义类。
 * </p>
 */
package top.fusb.deploybot.plugin.api.deployment;
