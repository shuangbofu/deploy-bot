/**
 * 定义部署插件可消费的标准化上下文模型。
 * <p>
 * 平台会先把流水线、模板、主机、运行时、变量等原始实体装配成这些中立对象，
 * 再交给插件完成类型判断、变量改写、PID 检测和启动判定，避免插件直接耦合 backend 实体。
 * </p>
 */
package top.fusb.deploybot.plugin.api.deployment.context;
