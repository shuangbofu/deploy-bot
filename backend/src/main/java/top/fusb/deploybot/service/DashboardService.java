package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.dto.DashboardAnalytics;
import top.fusb.deploybot.dto.DashboardChartPoint;
import top.fusb.deploybot.dto.DashboardMetricCard;
import top.fusb.deploybot.dto.DashboardQuery;
import top.fusb.deploybot.dto.DashboardRecentPoint;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.repo.UserRepository;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final List<DeploymentStatus> ACTIVE_STATUSES = List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING);
    private static final List<DeploymentStatus> FINISHED_STATUSES = List.of(DeploymentStatus.SUCCESS, DeploymentStatus.FAILED, DeploymentStatus.STOPPED);

    private final DeploymentRepository deploymentRepository;
    private final UserRepository userRepository;

    private enum DashboardGranularity {
        HOUR,
        DAY,
        WEEK,
        MONTH
    }

    private record DashboardRange(LocalDateTime startTime, LocalDateTime endTime, DashboardGranularity granularity) {
    }

    public DashboardAnalytics buildAnalytics(DashboardQuery query) {
        DashboardRange range = resolveRange(query);
        List<DeploymentEntity> deployments = deploymentRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(range.startTime())
                .stream()
                .filter(deployment -> deployment.getCreatedAt() != null && deployment.getCreatedAt().isBefore(range.endTime()))
                .filter(deployment -> matchesQuery(deployment, query))
                .toList();

        return new DashboardAnalytics(
                buildMetricCards(deployments),
                buildAnalyticsTrend(deployments, range),
                buildStatusDistribution(deployments),
                buildRanking(deployments, this::resolveProjectName, "未归属项目", 8),
                buildRanking(deployments, this::resolvePipelineName, "未命名流水线", 8),
                buildRanking(deployments, this::resolveTriggeredByName, "未知触发人", 8),
                buildRanking(deployments, this::resolveTemplateType, "未记录类型", 8),
                buildRanking(deployments, this::resolveHostName, "本机", 8),
                buildRecentPoints(deployments),
                buildDurationDistribution(deployments)
        );
    }

    private List<DeploymentEntity> loadDeployments(AuthenticatedUser currentUser, boolean admin, LocalDateTime startTime) {
        return admin
                ? deploymentRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(startTime)
                : deploymentRepository.findByTriggeredByAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(currentUser.username(), startTime);
    }

    private DashboardRange resolveRange(DashboardQuery query) {
        LocalDateTime now = LocalDateTime.now();
        String range = query == null || query.range() == null ? "30d" : query.range();
        LocalDateTime startTime = switch (range) {
            case "today" -> now.toLocalDate().atStartOfDay();
            case "7d" -> now.minusDays(6).toLocalDate().atStartOfDay();
            case "90d" -> now.minusDays(89).toLocalDate().atStartOfDay();
            default -> now.minusDays(29).toLocalDate().atStartOfDay();
        };
        DashboardGranularity granularity = resolveGranularity(query == null ? null : query.granularity(), range);
        return new DashboardRange(startTime, now.plusSeconds(1), granularity);
    }

    private DashboardGranularity resolveGranularity(String value, String range) {
        if ("hour".equals(value)) {
            return DashboardGranularity.HOUR;
        }
        if ("week".equals(value)) {
            return DashboardGranularity.WEEK;
        }
        if ("month".equals(value)) {
            return DashboardGranularity.MONTH;
        }
        if ("today".equals(range)) {
            return DashboardGranularity.HOUR;
        }
        return DashboardGranularity.DAY;
    }

    private boolean matchesQuery(DeploymentEntity deployment, DashboardQuery query) {
        if (query == null) {
            return true;
        }
        if (hasText(query.projectName()) && !query.projectName().equals(resolveProjectName(deployment))) {
            return false;
        }
        if (hasText(query.pipelineName()) && !query.pipelineName().equals(resolvePipelineName(deployment))) {
            return false;
        }
        if (hasText(query.triggeredBy()) && !query.triggeredBy().equals(deployment.getTriggeredBy())) {
            return false;
        }
        if (hasText(query.status())) {
            try {
                return deployment.getStatus() == DeploymentStatus.valueOf(query.status());
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return true;
    }

    private List<DashboardMetricCard> buildMetricCards(List<DeploymentEntity> deployments) {
        long total = deployments.size();
        long success = countStatus(deployments, DeploymentStatus.SUCCESS);
        long failed = countStatus(deployments, DeploymentStatus.FAILED);
        long running = deployments.stream().filter(item -> ACTIVE_STATUSES.contains(item.getStatus())).count();
        long stopped = countStatus(deployments, DeploymentStatus.STOPPED);
        long finished = deployments.stream().filter(item -> FINISHED_STATUSES.contains(item.getStatus())).count();
        long avgSeconds = Math.round(deployments.stream()
                .map(this::durationSeconds)
                .filter(value -> value > 0)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0));
        long successRate = finished > 0 ? Math.round(success * 100.0 / finished) : 0;

        List<DashboardMetricCard> metrics = new ArrayList<>();
        metrics.add(new DashboardMetricCard("deployments", String.valueOf(total)));
        metrics.add(new DashboardMetricCard("successRate", String.valueOf(successRate)));
        metrics.add(new DashboardMetricCard("failed", String.valueOf(failed)));
        metrics.add(new DashboardMetricCard("avgDuration", String.valueOf(avgSeconds)));
        metrics.add(new DashboardMetricCard("running", String.valueOf(running)));
        metrics.add(new DashboardMetricCard("stopped", String.valueOf(stopped)));
        long activePipelines = deployments.stream().map(this::resolvePipelineName).filter(Objects::nonNull).distinct().count();
        long activeProjects = deployments.stream().map(this::resolveProjectName).filter(Objects::nonNull).distinct().count();
        metrics.add(new DashboardMetricCard("activePipelines", String.valueOf(activePipelines)));
        metrics.add(new DashboardMetricCard("activeProjects", String.valueOf(activeProjects)));
        return metrics;
    }

    private List<DashboardChartPoint> buildAnalyticsTrend(List<DeploymentEntity> deployments, DashboardRange range) {
        Map<String, Map<String, Long>> buckets = initTrendBuckets(range);
        for (DeploymentEntity deployment : deployments) {
            String key = bucketKey(deployment.getCreatedAt(), range.granularity());
            Map<String, Long> bucket = buckets.get(key);
            if (bucket == null) {
                continue;
            }
            bucket.compute("total", (ignored, value) -> value == null ? 1 : value + 1);
            bucket.compute(statusCategory(deployment.getStatus()), (ignored, value) -> value == null ? 1 : value + 1);
        }
        List<DashboardChartPoint> result = new ArrayList<>();
        buckets.forEach((key, values) -> values.forEach((category, value) ->
                result.add(new DashboardChartPoint(key, trendLabel(key, range.granularity()), category, value))));
        return result;
    }

    private Map<String, Map<String, Long>> initTrendBuckets(DashboardRange range) {
        Map<String, Map<String, Long>> buckets = new LinkedHashMap<>();
        LocalDateTime cursor = truncateTime(range.startTime(), range.granularity());
        LocalDateTime end = truncateTime(range.endTime(), range.granularity()).plusSeconds(1);
        while (cursor.isBefore(end)) {
            Map<String, Long> values = new LinkedHashMap<>();
            values.put("total", 0L);
            values.put(DeploymentStatus.SUCCESS.name(), 0L);
            values.put(DeploymentStatus.FAILED.name(), 0L);
            values.put("ACTIVE", 0L);
            buckets.put(bucketKey(cursor, range.granularity()), values);
            cursor = switch (range.granularity()) {
                case HOUR -> cursor.plusHours(1);
                case WEEK -> cursor.plusWeeks(1);
                case MONTH -> cursor.plusMonths(1);
                default -> cursor.plusDays(1);
            };
        }
        return buckets;
    }

    private List<DashboardChartPoint> buildStatusDistribution(List<DeploymentEntity> deployments) {
        Map<DeploymentStatus, Long> counts = new EnumMap<>(DeploymentStatus.class);
        deployments.forEach(deployment -> counts.compute(deployment.getStatus(), (ignored, value) -> value == null ? 1 : value + 1));
        return counts.entrySet().stream()
                .map(entry -> new DashboardChartPoint(entry.getKey().name(), entry.getKey().name(), "status", entry.getValue()))
                .toList();
    }

    private List<DashboardChartPoint> buildRanking(List<DeploymentEntity> deployments, Function<DeploymentEntity, String> resolver, String emptyLabel, int limit) {
        return deployments.stream()
                .collect(Collectors.groupingBy(item -> valueOrDefault(resolver.apply(item), emptyLabel), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new DashboardChartPoint(entry.getKey(), entry.getKey(), "deployments", entry.getValue()))
                .toList();
    }

    private List<DashboardRecentPoint> buildRecentPoints(List<DeploymentEntity> deployments) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
        return deployments.stream()
                .sorted(Comparator.comparing(DeploymentEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(30)
                .map(deployment -> new DashboardRecentPoint(
                        deployment.getId(),
                        deployment.getCreatedAt() == null ? "-" : deployment.getCreatedAt().format(formatter),
                        valueOrDefault(resolvePipelineName(deployment), "未命名流水线"),
                        resolvePipelineName(deployment),
                        resolveProjectName(deployment),
                        deployment.getStatus(),
                        durationSeconds(deployment)
                ))
                .toList();
    }

    private List<DashboardChartPoint> buildDurationDistribution(List<DeploymentEntity> deployments) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("lt60", 0L);
        buckets.put("1to5m", 0L);
        buckets.put("5to15m", 0L);
        buckets.put("15to30m", 0L);
        buckets.put("gte30m", 0L);
        deployments.stream().map(this::durationSeconds).filter(value -> value > 0).forEach(seconds -> {
            String key;
            if (seconds < 60) {
                key = "lt60";
            } else if (seconds < 300) {
                key = "1to5m";
            } else if (seconds < 900) {
                key = "5to15m";
            } else if (seconds < 1800) {
                key = "15to30m";
            } else {
                key = "gte30m";
            }
            buckets.compute(key, (ignored, value) -> value == null ? 1 : value + 1);
        });
        return buckets.entrySet().stream()
                .map(entry -> new DashboardChartPoint(entry.getKey(), entry.getKey(), "deployments", entry.getValue()))
                .toList();
    }

    private long countStatus(List<DeploymentEntity> deployments, DeploymentStatus status) {
        return deployments.stream().filter(item -> item.getStatus() == status).count();
    }

    private long durationSeconds(DeploymentEntity deployment) {
        if (deployment.getStartedAt() == null || deployment.getFinishedAt() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(deployment.getStartedAt(), deployment.getFinishedAt()).toSeconds());
    }

    private String bucketKey(LocalDateTime time, DashboardGranularity granularity) {
        LocalDateTime normalized = truncateTime(time, granularity);
        return switch (granularity) {
            case HOUR -> normalized.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));
            case WEEK -> normalized.toLocalDate().toString();
            case MONTH -> normalized.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            default -> normalized.toLocalDate().toString();
        };
    }

    private String trendLabel(String key, DashboardGranularity granularity) {
        if (granularity == DashboardGranularity.HOUR) {
            return key.substring(Math.max(0, key.length() - 5));
        }
        if (granularity == DashboardGranularity.MONTH) {
            return key.replace("-", "/");
        }
        LocalDate date = LocalDate.parse(key);
        if (granularity == DashboardGranularity.WEEK) {
            return date.getMonthValue() + "/" + date.getDayOfMonth() + " 周";
        }
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    private LocalDateTime truncateTime(LocalDateTime time, DashboardGranularity granularity) {
        if (time == null) {
            return LocalDateTime.now();
        }
        return switch (granularity) {
            case HOUR -> time.withMinute(0).withSecond(0).withNano(0);
            case WEEK -> time.toLocalDate().minusDays(time.getDayOfWeek().getValue() - 1L).atStartOfDay();
            case MONTH -> time.toLocalDate().withDayOfMonth(1).atStartOfDay();
            default -> time.toLocalDate().atStartOfDay();
        };
    }

    private String statusCategory(DeploymentStatus status) {
        if (ACTIVE_STATUSES.contains(status)) {
            return "ACTIVE";
        }
        return status == null ? "UNKNOWN" : status.name();
    }

    private String resolveProjectName(DeploymentEntity deployment) {
        if (hasText(deployment.getProjectName())) {
            return deployment.getProjectName();
        }
        if (deployment.getPipeline() != null && deployment.getPipeline().getProject() != null) {
            return deployment.getPipeline().getProject().getName();
        }
        return null;
    }

    private String resolvePipelineName(DeploymentEntity deployment) {
        if (hasText(deployment.getPipelineName())) {
            return deployment.getPipelineName();
        }
        return deployment.getPipeline() == null ? null : deployment.getPipeline().getName();
    }

    private String resolveTriggeredByName(DeploymentEntity deployment) {
        String displayName = resolveDisplayName(deployment.getTriggeredBy());
        return hasText(displayName) ? displayName : deployment.getTriggeredBy();
    }

    private String resolveTemplateType(DeploymentEntity deployment) {
        if (deployment.getPipeline() == null) {
            return null;
        }
        if (hasText(deployment.getPipeline().getTemplateTypeSnapshot())) {
            return deployment.getPipeline().getTemplateTypeSnapshot();
        }
        if (deployment.getPipeline().getTemplate() != null) {
            return deployment.getPipeline().getTemplate().getTemplateType();
        }
        return deployment.getPipeline().getTemplatePluginId();
    }

    private String resolveHostName(DeploymentEntity deployment) {
        if (deployment.getPipeline() == null || deployment.getPipeline().getTargetHost() == null) {
            return null;
        }
        return deployment.getPipeline().getTargetHost().getName();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveDisplayName(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(username)
                .map(UserEntity::getDisplayName)
                .filter(value -> value != null && !value.isBlank())
                .orElse(username);
    }

    private AuthenticatedUser requireCurrentUser() {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        if (currentUser == null) {
            throw new IllegalStateException("当前请求缺少登录用户上下文。");
        }
        return currentUser;
    }
}
