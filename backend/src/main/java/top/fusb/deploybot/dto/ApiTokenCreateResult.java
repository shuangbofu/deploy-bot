package top.fusb.deploybot.dto;

/**
 * API Token 创建结果。明文 token 只会在创建时返回一次。
 */
public record ApiTokenCreateResult(
        String token,
        ApiTokenSummary summary
) {
}
