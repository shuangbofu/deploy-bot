package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.dto.DeploymentRestrictionEvaluationResult;
import top.fusb.deploybot.dto.DeploymentRestrictionPolicyConfig;
import top.fusb.deploybot.dto.DeploymentRestrictionScopeType;
import top.fusb.deploybot.dto.DeploymentRestrictionWeeklyWindow;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.model.PipelineEntity;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 部署限制策略评估服务。
 */
@Service
@RequiredArgsConstructor
public class DeploymentRestrictionPolicyService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final SystemSettingsService systemSettingsService;

    /**
     * 判断当前流水线是否允许发起部署。
     *
     * @param pipeline 待部署流水线
     * @param now 当前时间
     * @return 允许或拒绝部署的评估结果
     */
    public DeploymentRestrictionEvaluationResult evaluate(PipelineEntity pipeline, LocalDateTime now) {
        List<DeploymentRestrictionPolicyConfig> policies = systemSettingsService.get().getDeploymentRestrictionPolicies().stream()
                .filter(policy -> Boolean.TRUE.equals(policy.enabled()))
                .filter(policy -> appliesTo(policy, pipeline))
                .toList();
        for (DeploymentRestrictionPolicyConfig policy : policies) {
            if (matchesWindow(policy, now)) {
                return deny(policy, "当前时间命中部署限制策略。");
            }
        }
        return DeploymentRestrictionEvaluationResult.pass();
    }

    private boolean appliesTo(DeploymentRestrictionPolicyConfig policy, PipelineEntity pipeline) {
        DeploymentRestrictionScopeType scopeType = policy.scopeType() == null ? DeploymentRestrictionScopeType.GLOBAL : policy.scopeType();
        if (scopeType == DeploymentRestrictionScopeType.GLOBAL) {
            return true;
        }
        List<Long> scopeIds = policy.scopeIds() == null ? List.of() : policy.scopeIds();
        if (pipeline == null || scopeIds.isEmpty()) {
            return false;
        }
        if (scopeType == DeploymentRestrictionScopeType.PIPELINE) {
            return scopeIds.contains(pipeline.getId());
        }
        return pipeline.getProject() != null && scopeIds.contains(pipeline.getProject().getId());
    }

    private boolean matchesWindow(DeploymentRestrictionPolicyConfig policy, LocalDateTime now) {
        return matchesWeeklyWindows(policy.weeklyWindows(), now);
    }

    private boolean matchesWeeklyWindows(List<DeploymentRestrictionWeeklyWindow> weeklyWindows, LocalDateTime now) {
        if (weeklyWindows == null || weeklyWindows.isEmpty()) {
            return true;
        }
        return weeklyWindows.stream().anyMatch(window -> matchesWeeklyWindow(window, now));
    }

    private boolean matchesWeeklyWindow(DeploymentRestrictionWeeklyWindow window, LocalDateTime now) {
        List<Integer> daysOfWeek = window.daysOfWeek() == null ? List.of() : window.daysOfWeek();
        int currentDayValue = DayOfWeek.from(now).getValue();
        if (!daysOfWeek.contains(currentDayValue)) {
            return false;
        }
        LocalTime startTime = parseTime(window.startTime());
        LocalTime endTime = parseTime(window.endTime());
        if (startTime == null || endTime == null) {
            return false;
        }
        LocalTime currentTime = now.toLocalTime();
        boolean timeMatched;
        if (startTime.equals(endTime)) {
            timeMatched = true;
        } else if (startTime.isBefore(endTime)) {
            timeMatched = !currentTime.isBefore(startTime) && currentTime.isBefore(endTime);
        } else {
            timeMatched = !currentTime.isBefore(startTime) || currentTime.isBefore(endTime);
        }
        return timeMatched && matchesMinuteWindow(window, now);
    }

    private boolean matchesMinuteWindow(DeploymentRestrictionWeeklyWindow window, LocalDateTime now) {
        Integer startMinute = window.startMinute();
        Integer endMinute = window.endMinute();
        if (startMinute == null && endMinute == null) {
            return true;
        }
        if (startMinute == null || endMinute == null || startMinute < 0 || startMinute > 59 || endMinute < 0 || endMinute > 59) {
            return false;
        }
        int currentMinute = now.getMinute();
        if (startMinute <= endMinute) {
            return currentMinute >= startMinute && currentMinute <= endMinute;
        }
        return currentMinute >= startMinute || currentMinute <= endMinute;
    }

    private LocalTime parseTime(String value) {
        if (TextKit.isBlank(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private DeploymentRestrictionEvaluationResult deny(DeploymentRestrictionPolicyConfig policy, String defaultReason) {
        String policyName = TextKit.isBlank(policy.name()) ? "部署限制策略" : policy.name().trim();
        String reason = TextKit.isBlank(policy.reason()) ? defaultReason : policy.reason().trim();
        return DeploymentRestrictionEvaluationResult.denied(policyName, reason);
    }
}
