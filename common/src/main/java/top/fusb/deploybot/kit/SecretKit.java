package top.fusb.deploybot.kit;

/**
 * 敏感字段合并工具。
 */
public final class SecretKit {

    private SecretKit() {
    }

    /**
     * 处理“空值表示保持现状，非空表示覆盖”的敏感字段更新逻辑。
     *
     * @param currentValue 当前已有值
     * @param requestValue 请求新值
     * @return 合并后的结果
     */
    public static String mergeOptionalSecret(String currentValue, String requestValue) {
        if (requestValue == null) {
            return currentValue;
        }
        return TextKit.trimToNull(requestValue);
    }
}
