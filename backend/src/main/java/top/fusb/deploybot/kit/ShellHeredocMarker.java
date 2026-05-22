package top.fusb.deploybot.kit;

/**
 * 统一管理部署脚本里使用的 heredoc 标记，避免散落硬编码字符串。
 */
public enum ShellHeredocMarker {
    /** Git SSH 私钥文件内容的 heredoc 结束标记。 */
    GIT_PRIVATE_KEY("__DEPLOYBOT_GIT_PRIVATE_KEY__"),
    /** Git SSH 公钥文件内容的 heredoc 结束标记。 */
    GIT_PUBLIC_KEY("__DEPLOYBOT_GIT_PUBLIC_KEY__"),
    /** Git known_hosts 文件内容的 heredoc 结束标记。 */
    GIT_KNOWN_HOSTS("__DEPLOYBOT_GIT_KNOWN_HOSTS__"),
    /** Git SSH wrapper 脚本内容的 heredoc 结束标记。 */
    GIT_SSH_WRAPPER("__DEPLOYBOT_GIT_SSH_WRAPPER__");

    private final String marker;

    ShellHeredocMarker(String marker) {
        this.marker = marker;
    }

    /**
     * 返回当前枚举对应的 heredoc 标记文本。
     *
     * @return heredoc 标记字符串
     */
    public String marker() {
        return marker;
    }
}
