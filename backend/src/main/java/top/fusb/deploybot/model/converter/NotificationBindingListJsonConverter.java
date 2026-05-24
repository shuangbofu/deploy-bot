package top.fusb.deploybot.model.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import top.fusb.deploybot.notification.dto.NotificationBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知绑定列表 JSON 字段转换器。
 */
public class NotificationBindingListJsonConverter extends JsonAttributeConverter<List<NotificationBinding>> {
    private static final TypeReference<List<NotificationBinding>> TYPE_REFERENCE = new TypeReference<>() {
    };

    /**
     * 返回通知绑定列表的 Jackson 类型。
     *
     * @return 通知绑定列表类型引用
     */
    @Override
    protected TypeReference<List<NotificationBinding>> typeReference() {
        return TYPE_REFERENCE;
    }

    /**
     * 返回空通知绑定列表。
     *
     * @return 空列表
     */
    @Override
    protected List<NotificationBinding> emptyValue() {
        return new ArrayList<>();
    }
}
