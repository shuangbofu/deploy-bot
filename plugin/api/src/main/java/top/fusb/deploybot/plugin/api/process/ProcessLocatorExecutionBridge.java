package top.fusb.deploybot.plugin.api.process;

/**
 * PID 发现插件与宿主环境之间的执行桥接接口。
 */
public interface ProcessLocatorExecutionBridge {

    /**
     * 在目标环境执行一段 shell 脚本。
     *
     * @param remoteHost 是否远程主机
     * @param script shell 脚本
     * @return 脚本输出
     */
    String runScript(boolean remoteHost, String script);
}
