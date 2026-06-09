package top.fusb.deploybot.model.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import top.fusb.deploybot.dto.DeploymentRestrictionPolicyConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 部署限制策略列表 JSON 字段转换器。
 */
public class DeploymentRestrictionPolicyListJsonConverter extends JsonAttributeConverter<List<DeploymentRestrictionPolicyConfig>> {
    private static final TypeReference<List<DeploymentRestrictionPolicyConfig>> TYPE_REFERENCE = new TypeReference<>() {
    };

    /**
     * 返回部署限制策略列表的 Jackson 类型。
     *
     * @return 部署限制策略列表类型引用
     */
    @Override
    protected TypeReference<List<DeploymentRestrictionPolicyConfig>> typeReference() {
        return TYPE_REFERENCE;
    }

    /**
     * 返回空部署限制策略列表。
     *
     * @return 空列表
     */
    @Override
    protected List<DeploymentRestrictionPolicyConfig> emptyValue() {
        return new ArrayList<>();
    }
}
