package top.fusb.deploybot.dto;

import java.util.List;

/**
 * 每周时间窗口。
 */
public record DeploymentRestrictionWeeklyWindow(
        /** 生效星期，取值 1-7，对应周一到周日。 */
        List<Integer> daysOfWeek,
        /** 时间段开始，格式 HH:mm。 */
        String startTime,
        /** 时间段结束，格式 HH:mm。 */
        String endTime,
        /** 每小时窗口开始分钟，空表示当前时间段内 0-59 分钟都命中。 */
        Integer startMinute,
        /** 每小时窗口结束分钟，空表示当前时间段内 0-59 分钟都命中。 */
        Integer endMinute
) {
}
