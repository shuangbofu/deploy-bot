package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.dto.DashboardAnalytics;
import top.fusb.deploybot.dto.DashboardChartPoint;
import top.fusb.deploybot.dto.DashboardDeploymentSummary;
import top.fusb.deploybot.dto.DashboardMetricCard;
import top.fusb.deploybot.dto.DashboardQuery;
import top.fusb.deploybot.dto.DashboardRecentPoint;
import top.fusb.deploybot.dto.DashboardServiceSummary;
import top.fusb.deploybot.dto.DashboardStatsSummary;
import top.fusb.deploybot.dto.DashboardSummary;
import top.fusb.deploybot.dto.DashboardTrendItem;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.model.ServiceStatus;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.repo.HostRepository;
import top.fusb.deploybot.repo.PipelineRepository;
import top.fusb.deploybot.repo.ProjectRepository;
import top.fusb.deploybot.repo.ServiceRepository;
import top.fusb.deploybot.repo.TemplateRepository;
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
    private static final List<DeploymentStatus> ATTENTION_STATUSES = List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING, DeploymentStatus.FAILED);

    private final ProjectRepository projectRepository;
    private final TemplateRepository templateRepository;
    private final PipelineRepository pipelineRepository;
    private final DeploymentRepository deploymentRepository;
    private final HostService hostService;
    private final HostRepository hostRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;

    private enum DashboardGranularity {
        HOUR,
        DAY,
        WEEK,
        MONTH
    }

    private record DashboardRange(LocalDateTime startTime, LocalDateTime endTime, DashboardGranularity granularity) {
    }

    public DashboardSummary buildSummary() {
        AuthenticatedUser currentUser = requireCurrentUser();
        boolean admin = currentUser.isAdmin();
        DashboardStatsSummary stats = buildStats(currentUser, admin);
        List<DashboardTrendItem> trend = buildTrend(currentUser, admin);
        List<DashboardDeploymentSummary> latestDeployments = buildLatestDeployments(currentUser, admin);
        List<DashboardDeploymentSummary> attentionDeployments = buildAttentionDeployments(currentUser, admin);
        List<DashboardServiceSummary> services = admin
                ? serviceRepository.findTop6ByOrderByUpdatedAtDesc().stream().map(this::toDashboardServiceSummary).toList()
                : List.of();
        return new DashboardSummary(stats, trend, latestDeployments, attentionDeployments, services);
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

    private DashboardStatsSummary buildStats(AuthenticatedUser currentUser, boolean admin) {
        long deploymentCount = admin
                ? deploymentRepository.count()
                : deploymentRepository.countByTriggeredBy(currentUser.username());
        long runningDeployments = admin
                ? deploymentRepository.countByStatusIn(ACTIVE_STATUSES)
                : deploymentRepository.countByTriggeredByAndStatusIn(currentUser.username(), ACTIVE_STATUSES);
        long failedDeployments = admin
                ? deploymentRepository.countByStatus(DeploymentStatus.FAILED)
                : deploymentRepository.countByTriggeredByAndStatus(currentUser.username(), DeploymentStatus.FAILED);
        long successDeployments = admin
                ? deploymentRepository.countByStatus(DeploymentStatus.SUCCESS)
                : deploymentRepository.countByTriggeredByAndStatus(currentUser.username(), DeploymentStatus.SUCCESS);
        long finishedDeployments = admin
                ? deploymentRepository.countByStatusIn(FINISHED_STATUSES)
                : deploymentRepository.countByTriggeredByAndStatusIn(currentUser.username(), FINISHED_STATUSES);
        int successRate = finishedDeployments > 0 ? (int) Math.round(successDeployments * 100.0 / finishedDeployments) : 0;

        long hostCount = 0;
        if (admin) {
            hostService.ensureLocalHost();
            hostCount = hostRepository.count();
        }
        long serviceCount = admin ? serviceRepository.count() : 0;
        long runningServices = admin ? serviceRepository.countByStatus(ServiceStatus.RUNNING) : 0;
        long userCount = admin ? userRepository.count() : 0;

        return new DashboardStatsSummary(
                admin ? projectRepository.count() : 0,
                admin ? templateRepository.count() : 0,
                pipelineRepository.count(),
                deploymentCount,
                hostCount,
                serviceCount,
                userCount,
                runningServices,
                successRate,
                runningDeployments,
                failedDeployments
        );
    }

    private List<DashboardTrendItem> buildTrend(AuthenticatedUser currentUser, boolean admin) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);
        LocalDateTime startTime = startDate.atStartOfDay();
        List<DeploymentEntity> deployments = admin
                ? deploymentRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(startTime)
                : deploymentRepository.findByTriggeredByAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(currentUser.username(), startTime);
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            buckets.put(date, new long[]{0, 0});
        }
        for (DeploymentEntity deployment : deployments) {
            if (deployment.getCreatedAt() == null) {
                continue;
            }
            LocalDate date = deployment.getCreatedAt().toLocalDate();
            long[] bucket = buckets.get(date);
            if (bucket == null) {
                continue;
            }
            bucket[0] += 1;
            if (deployment.getStatus() == DeploymentStatus.SUCCESS) {
                bucket[1] += 1;
            }
        }
        List<DashboardTrendItem> result = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> entry : buckets.entrySet()) {
            LocalDate date = entry.getKey();
            long[] bucket = entry.getValue();
            result.add(new DashboardTrendItem(
                    date.toString(),
                    date.getMonthValue() + "/" + date.getDayOfMonth(),
                    bucket[0],
                    bucket[1]
            ));
        }
        return result;
    }

    private List<DashboardDeploymentSummary> buildLatestDeployments(AuthenticatedUser currentUser, boolean admin) {
        List<DeploymentEntity> deployments = admin
                ? deploymentRepository.findTop5ByOrderByCreatedAtDesc()
                : deploymentRepository.findTop5ByTriggeredByOrderByCreatedAtDesc(currentUser.username());
        return deployments.stream().map(this::toDashboardDeploymentSummary).toList();
    }

    private List<DashboardDeploymentSummary> buildAttentionDeployments(AuthenticatedUser currentUser, boolean admin) {
        List<DeploymentEntity> deployments = admin
                ? deploymentRepository.findTop5ByStatusInOrderByCreatedAtDesc(ATTENTION_STATUSES)
                : deploymentRepository.findTop5ByTriggeredByAndStatusInOrderByCreatedAtDesc(currentUser.username(), ATTENTION_STATUSES);
        return deployments.stream()
                .sorted(Comparator.comparing(this::attentionWeight).thenComparing(DeploymentEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDashboardDeploymentSummary)
                .toList();
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
        metrics.add(new DashboardMetricCard("deployments", "部署总数", String.valueOf(total), "次", "当前筛选范围"));
        metrics.add(new DashboardMetricCard("successRate", "成功率", String.valueOf(successRate), "%", "成功 / 已结束"));
        metrics.add(new DashboardMetricCard("failed", "失败数", String.valueOf(failed), "次", "需要关注"));
        metrics.add(new DashboardMetricCard("avgDuration", "平均耗时", formatDuration(avgSeconds), null, "已结束部署"));
        metrics.add(new DashboardMetricCard("running", "进行中", String.valueOf(running), "个", "等待或运行"));
        metrics.add(new DashboardMetricCard("stopped", "已停止", String.valueOf(stopped), "次", "人工停止"));
        long activePipelines = deployments.stream().map(this::resolvePipelineName).filter(Objects::nonNull).distinct().count();
        long activeProjects = deployments.stream().map(this::resolveProjectName).filter(Objects::nonNull).distinct().count();
        metrics.add(new DashboardMetricCard("activePipelines", "活跃流水线", String.valueOf(activePipelines), "条", "有部署记录"));
        metrics.add(new DashboardMetricCard("activeProjects", "活跃项目", String.valueOf(activeProjects), "个", "有部署记录"));
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
            bucket.compute("部署总数", (ignored, value) -> value == null ? 1 : value + 1);
            bucket.compute(statusLabel(deployment.getStatus()), (ignored, value) -> value == null ? 1 : value + 1);
        }
        List<DashboardChartPoint> result = new ArrayList<>();
        buckets.forEach((key, values) -> values.forEach((category, value) ->
                result.add(new DashboardChartPoint(key, trendLabel(key, range.granularity()), category, value, null))));
        return result;
    }

    private Map<String, Map<String, Long>> initTrendBuckets(DashboardRange range) {
        Map<String, Map<String, Long>> buckets = new LinkedHashMap<>();
        LocalDateTime cursor = truncateTime(range.startTime(), range.granularity());
        LocalDateTime end = truncateTime(range.endTime(), range.granularity()).plusSeconds(1);
        while (cursor.isBefore(end)) {
            Map<String, Long> values = new LinkedHashMap<>();
            values.put("部署总数", 0L);
            values.put("成功", 0L);
            values.put("失败", 0L);
            values.put("运行中", 0L);
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
                .map(entry -> new DashboardChartPoint(entry.getKey().name(), statusLabel(entry.getKey()), "状态", entry.getValue(), null))
                .toList();
    }

    private List<DashboardChartPoint> buildRanking(List<DeploymentEntity> deployments, Function<DeploymentEntity, String> resolver, String emptyLabel, int limit) {
        return deployments.stream()
                .collect(Collectors.groupingBy(item -> valueOrDefault(resolver.apply(item), emptyLabel), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new DashboardChartPoint(entry.getKey(), entry.getKey(), "部署次数", entry.getValue(), null))
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
        buckets.put("1 分钟内", 0L);
        buckets.put("1-5 分钟", 0L);
        buckets.put("5-15 分钟", 0L);
        buckets.put("15-30 分钟", 0L);
        buckets.put("30 分钟以上", 0L);
        deployments.stream().map(this::durationSeconds).filter(value -> value > 0).forEach(seconds -> {
            String key;
            if (seconds < 60) {
                key = "1 分钟内";
            } else if (seconds < 300) {
                key = "1-5 分钟";
            } else if (seconds < 900) {
                key = "5-15 分钟";
            } else if (seconds < 1800) {
                key = "15-30 分钟";
            } else {
                key = "30 分钟以上";
            }
            buckets.compute(key, (ignored, value) -> value == null ? 1 : value + 1);
        });
        return buckets.entrySet().stream()
                .map(entry -> new DashboardChartPoint(entry.getKey(), entry.getKey(), "部署次数", entry.getValue(), null))
                .toList();
    }

    private int attentionWeight(DeploymentEntity entity) {
        if (entity.getStatus() == DeploymentStatus.FAILED) {
            return 0;
        }
        if (entity.getStatus() == DeploymentStatus.RUNNING) {
            return 1;
        }
        return 2;
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

    private String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "-";
        }
        if (seconds < 60) {
            return seconds + " 秒";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        return (minutes / 60) + " 小时";
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

    private String statusLabel(DeploymentStatus status) {
        if (status == DeploymentStatus.SUCCESS) {
            return "成功";
        }
        if (status == DeploymentStatus.FAILED) {
            return "失败";
        }
        if (status == DeploymentStatus.RUNNING || status == DeploymentStatus.PENDING) {
            return "运行中";
        }
        if (status == DeploymentStatus.STOPPED) {
            return "已停止";
        }
        return "未知";
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

    private DashboardDeploymentSummary toDashboardDeploymentSummary(DeploymentEntity entity) {
        return new DashboardDeploymentSummary(
                entity.getId(),
                entity.getPipeline() == null ? null : entity.getPipeline().getName(),
                entity.getPipeline() != null && entity.getPipeline().getProject() != null ? entity.getPipeline().getProject().getName() : null,
                entity.getBranchName(),
                entity.getTriggeredBy(),
                resolveDisplayName(entity.getTriggeredBy()),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getProgressPercent(),
                entity.getProgressText()
        );
    }

    private DashboardServiceSummary toDashboardServiceSummary(ServiceEntity entity) {
        return new DashboardServiceSummary(
                entity.getId(),
                entity.getServiceName(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                entity.getPipeline() == null ? null : entity.getPipeline().getName(),
                entity.getPipeline() != null && entity.getPipeline().getTargetHost() != null ? entity.getPipeline().getTargetHost().getName() : "本机",
                entity.getUpdatedAt()
        );
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
