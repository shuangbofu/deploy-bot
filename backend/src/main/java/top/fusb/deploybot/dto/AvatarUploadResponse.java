package top.fusb.deploybot.dto;

/**
 * 头像上传结果。
 */
public record AvatarUploadResponse(
        /** 头像访问路径。 */
        String url
) {
}
