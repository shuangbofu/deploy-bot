package top.fusb.deploybot.kit;

/**
 * 数值归一化与边界收口工具。
 */
public final class NumberKit {

    private NumberKit() {
    }

    /**
     * 将可空整数归一化为非负值。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 非负整数结果
     */
    public static int nonNegativeOrDefault(Integer value, int defaultValue) {
        int normalized = value == null ? defaultValue : value;
        return Math.max(0, normalized);
    }
}
