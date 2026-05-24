package top.fusb.deploybot.notification.sender;

import org.springframework.stereotype.Component;
import top.fusb.deploybot.kit.JsonKit;
import top.fusb.deploybot.notification.model.NotificationChannelEntity;
import top.fusb.deploybot.notification.model.NotificationChannelType;
import top.fusb.deploybot.notification.model.NotificationTemplateMode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.Map;

@Component
public class FeishuNotificationSender implements NotificationSender {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public NotificationChannelType getType() {
        return NotificationChannelType.FEISHU;
    }

    @Override
    public NotificationSendResult send(NotificationChannelEntity channel, NotificationMessage message) throws Exception {
        if (channel.getWebhookConfig() == null) {
            throw new IllegalStateException("通知配置未绑定 Webhook 配置");
        }
        String webhookUrl = channel.getWebhookConfig().getWebhookUrl();
        String secret = channel.getWebhookConfig().getSecret();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (message.mode() == NotificationTemplateMode.FEISHU_CARD) {
            payload.put("msg_type", "interactive");
            payload.put("card", JsonKit.read(message.content(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            }));
        } else {
            payload.put("msg_type", "text");
            payload.put("content", Map.of("text", message.content()));
        }
        if (secret != null && !secret.isBlank()) {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            payload.put("timestamp", timestamp);
            payload.put("sign", sign(timestamp, secret.trim()));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl.trim()))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(JsonKit.writeCompact(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("飞书通知发送失败，HTTP 状态码：" + response.statusCode() + "，响应：" + response.body());
        }
        Map<String, Object> body = JsonKit.read(response.body(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
        });
        Object code = body.get("code");
        if (code instanceof Number number && number.intValue() != 0) {
            throw new IllegalStateException("飞书通知发送失败，code=" + number + "，msg=" + body.get("msg"));
        }
        return new NotificationSendResult(String.valueOf(body.getOrDefault("msg", "ok")));
    }

    /**
     * 飞书自定义机器人签名：timestamp + "\n" + secret 作为 HMAC key，对空消息做 SHA256，再 Base64。
     */
    private String sign(String timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
    }
}
