package top.fusb.deploybot.kit;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 时间格式化与时间范围转换工具。
 */
public final class TimeKit {
    private static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeKit() {
    }

    /**
     * 将时间格式化为统一的日期时间文本。
     *
     * @param value 待格式化的时间
     * @return 格式化后的时间字符串；如果时间为空则返回 {@code "-"}
     */
    public static String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "-";
        }
        return value.format(DEFAULT_DATE_TIME_FORMATTER);
    }

    /**
     * 计算两个时间点之间的持续时长，并格式化为中文文案。
     *
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     * @return 格式化后的耗时文案；如果任一时间为空则返回 {@code "-"}
     */
    public static String formatDuration(LocalDateTime startedAt, LocalDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return "-";
        }
        Duration duration = Duration.between(startedAt, finishedAt);
        long seconds = Math.max(0, duration.getSeconds());
        long minutes = seconds / 60;
        long remainSeconds = seconds % 60;
        return minutes > 0 ? minutes + "分" + remainSeconds + "秒" : remainSeconds + "秒";
    }

    /**
     * 将毫秒时间戳转换为当前系统时区下的 {@link LocalDateTime}。
     *
     * @param epochMillis 毫秒时间戳
     * @return 转换后的时间；如果时间戳为空则返回 {@code null}
     */
    public static LocalDateTime fromEpochMillis(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
