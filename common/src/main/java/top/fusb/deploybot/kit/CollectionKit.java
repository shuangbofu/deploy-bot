package top.fusb.deploybot.kit;

import java.util.Collection;
import java.util.List;

/**
 * 集合相关的轻量工具，主要收口常见的去空、去重、排序场景。
 */
public final class CollectionKit {

    private CollectionKit() {
    }

    /**
     * 对字符串集合执行去空白、去重和升序排序。
     *
     * @param values 原始字符串集合
     * @return 处理后的不可变列表；如果入参为空或无有效内容则返回空列表
     */
    public static List<String> sortedDistinctNonBlankStrings(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(TextKit::trimToNull)
                .filter(item -> item != null)
                .distinct()
                .sorted(String::compareTo)
                .toList();
    }
}
