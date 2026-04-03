package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.dto.PipelineHallSummary;
import top.fusb.deploybot.dto.PipelineRequest;
import top.fusb.deploybot.notification.dto.NotificationBinding;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.MavenSettingsEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentEntity;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.notification.repo.NotificationChannelRepository;
import top.fusb.deploybot.repo.HostRepository;
import top.fusb.deploybot.repo.MavenSettingsRepository;
import top.fusb.deploybot.repo.RuntimeEnvironmentRepository;
import top.fusb.deploybot.repo.PipelineRepository;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.repo.ProjectRepository;
import top.fusb.deploybot.repo.ServiceRepository;
import top.fusb.deploybot.repo.TemplateRepository;
import top.fusb.deploybot.repo.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PipelineService {

    private final PipelineRepository pipelineRepository;
    private final ProjectRepository projectRepository;
    private final TemplateRepository templateRepository;
    private final RuntimeEnvironmentRepository runtimeEnvironmentRepository;
    private final DeploymentRepository deploymentRepository;
    private final ServiceRepository serviceRepository;
    private final HostRepository hostRepository;
    private final MavenSettingsRepository mavenSettingsRepository;
    private final HostService hostService;
    private final NotificationChannelRepository notificationChannelRepository;
    private final JsonMapper jsonMapper;
    private final UserRepository userRepository;

    public PipelineService(
            PipelineRepository pipelineRepository,
            ProjectRepository projectRepository,
            TemplateRepository templateRepository,
            RuntimeEnvironmentRepository runtimeEnvironmentRepository,
            DeploymentRepository deploymentRepository,
            ServiceRepository serviceRepository,
            HostRepository hostRepository,
            MavenSettingsRepository mavenSettingsRepository,
            HostService hostService,
            NotificationChannelRepository notificationChannelRepository,
            JsonMapper jsonMapper,
            UserRepository userRepository
    ) {
        this.pipelineRepository = pipelineRepository;
        this.projectRepository = projectRepository;
        this.templateRepository = templateRepository;
        this.runtimeEnvironmentRepository = runtimeEnvironmentRepository;
        this.deploymentRepository = deploymentRepository;
        this.serviceRepository = serviceRepository;
        this.hostRepository = hostRepository;
        this.mavenSettingsRepository = mavenSettingsRepository;
        this.hostService = hostService;
        this.notificationChannelRepository = notificationChannelRepository;
        this.jsonMapper = jsonMapper;
        this.userRepository = userRepository;
    }

    public List<PipelineEntity> findAll() {
        return pipelineRepository.findAll();
    }

    public List<PipelineHallSummary> findHallSummaries() {
        return pipelineRepository.findAll(Sort.by(Sort.Order.desc("id"))).stream()
                .map(pipeline -> {
                    var latestDeployment = deploymentRepository.findFirstByPipelineIdOrderByCreatedAtDesc(pipeline.getId())
                            .map(this::enrichTriggeredByDisplayName)
                            .orElse(null);
                    Long latestDeploymentOrder = latestDeployment == null ? null : deploymentRepository.countByPipelineId(pipeline.getId());
                    return new PipelineHallSummary(
                            pipeline.getId(),
                            pipeline.getName(),
                            pipeline.getDescription(),
                            pipeline.getDefaultBranch(),
                            pipeline.getProject() == null ? null : pipeline.getProject().getName(),
                            pipeline.getTemplate() == null ? null : pipeline.getTemplate().getTemplateType(),
                            pipeline.getTagsJson(),
                            latestDeployment == null ? null : latestDeployment.getId(),
                            latestDeploymentOrder,
                            latestDeployment == null || latestDeployment.getStatus() == null ? null : latestDeployment.getStatus().name(),
                            latestDeployment == null ? null : latestDeployment.getBranchName(),
                            latestDeployment == null ? null : latestDeployment.getTriggeredBy(),
                            latestDeployment == null ? null : latestDeployment.getTriggeredByDisplayName(),
                            latestDeployment == null ? null : latestDeployment.getCreatedAt(),
                            latestDeployment == null ? null : latestDeployment.getStartedAt(),
                            latestDeployment == null ? null : latestDeployment.getFinishedAt(),
                            latestDeployment == null ? null : latestDeployment.getProgressPercent(),
                            latestDeployment == null ? null : latestDeployment.getProgressText()
                    );
                })
                .toList();
    }

    private top.fusb.deploybot.model.DeploymentEntity enrichTriggeredByDisplayName(top.fusb.deploybot.model.DeploymentEntity entity) {
        if (entity == null) {
            return null;
        }
        entity.setTriggeredByDisplayName(resolveDisplayName(entity.getTriggeredBy()));
        entity.setStoppedByDisplayName(resolveDisplayName(entity.getStoppedBy()));
        return entity;
    }

    private String resolveDisplayName(String username) {
        if (username == null || username.isBlank()) {
            return "-";
        }
        return userRepository.findByUsername(username)
                .map(item -> item.getDisplayName() == null || item.getDisplayName().isBlank() ? item.getUsername() : item.getDisplayName())
                .orElse(username);
    }

    public PageResult<PipelineEntity> findPage(
            int page,
            int pageSize,
            String keyword,
            Long projectId,
            Long templateId,
            Long hostId,
            List<String> tags
    ) {
        return PageResult.of(pipelineRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("defaultBranch")), pattern)
                ));
            }
            if (projectId != null) {
                predicates.add(cb.equal(root.get("project").get("id"), projectId));
            }
            if (templateId != null) {
                predicates.add(cb.equal(root.get("template").get("id"), templateId));
            }
            if (hostId != null) {
                predicates.add(cb.equal(root.get("targetHost").get("id"), hostId));
            }
            if (tags != null && !tags.isEmpty()) {
                for (String tag : tags) {
                    if (tag != null && !tag.isBlank()) {
                        predicates.add(cb.like(root.get("tagsJson"), "%" + "\"" + tag + "\"" + "%"));
                    }
                }
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, pageSize)), Sort.by(Sort.Order.desc("id")))));
    }

    public List<String> findAllTags() {
        return pipelineRepository.findAll().stream()
                .flatMap(item -> parseTags(item.getTagsJson()).stream())
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .sorted(String::compareTo)
                .toList();
    }

    /**
     * 流水线是“项目 + 模板 + 目标主机 + 默认变量”的聚合对象。
     */
    public PipelineEntity save(PipelineRequest request, Long id) {
        PipelineEntity entity = id == null ? new PipelineEntity() : pipelineRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PIPELINE_NOT_FOUND));
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setProject(projectRepository.findById(request.projectId())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PROJECT_NOT_FOUND)));
        entity.setTemplate(templateRepository.findById(request.templateId())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.TEMPLATE_NOT_FOUND)));
        HostEntity targetHost = hostRepository.findById(request.targetHostId())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.HOST_NOT_FOUND));
        entity.setTargetHost(targetHost);
        entity.setDefaultBranch(request.defaultBranch());
        entity.setVariablesJson(request.variablesJson());
        entity.setTagsJson(request.tagsJson());
        entity.setJavaEnvironment(resolveEnvironment(request.javaEnvironmentId()));
        entity.setNodeEnvironment(resolveEnvironment(request.nodeEnvironmentId()));
        RuntimeEnvironmentEntity mavenEnvironment = resolveEnvironment(request.mavenEnvironmentId());
        entity.setMavenEnvironment(mavenEnvironment);
        MavenSettingsEntity mavenSettings = resolveMavenSettings(request.mavenSettingsId());
        if (mavenSettings != null && (mavenEnvironment == null || mavenSettings.getRuntimeEnvironment() == null
                || !mavenSettings.getRuntimeEnvironment().getId().equals(mavenEnvironment.getId()))) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND, "请选择当前 Maven 环境下的 settings.xml 配置。");
        }
        entity.setMavenSettings(mavenSettings);
        entity.setRuntimeJavaEnvironment(resolveRuntimeJavaEnvironment(request.runtimeJavaEnvironmentId(), targetHost));
        entity.setApplicationName(normalizeText(request.applicationName()));
        entity.setSpringProfile(normalizeText(request.springProfile()));
        entity.setRuntimeConfigYaml(normalizeMultilineText(request.runtimeConfigYaml()));
        entity.setStartupKeyword(normalizeText(request.startupKeyword()));
        entity.setStartupTimeoutSeconds(normalizeStartupTimeout(request.startupTimeoutSeconds()));
        entity.setNotificationBindingsJson(normalizeNotificationBindings(request.notificationBindingsJson()));
        return pipelineRepository.save(entity);
    }

    private MavenSettingsEntity resolveMavenSettings(Long mavenSettingsId) {
        if (mavenSettingsId == null) {
            return null;
        }
        MavenSettingsEntity settings = mavenSettingsRepository.findById(mavenSettingsId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND));
        RuntimeEnvironmentEntity mavenEnvironment = settings.getRuntimeEnvironment();
        if (mavenEnvironment == null || mavenEnvironment.getType() != top.fusb.deploybot.model.RuntimeEnvironmentType.MAVEN) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND);
        }
        return settings;
    }

    @Transactional
    public void delete(Long id) {
        serviceRepository.deleteByPipelineId(id);
        deploymentRepository.detachPipeline(id);
        pipelineRepository.deleteById(id);
    }

    /**
     * 当前执行模型约定所有构建都在本机完成，因此构建环境必须来自本机。
     */
    private RuntimeEnvironmentEntity resolveEnvironment(Long environmentId) {
        if (environmentId == null) {
            return null;
        }
        RuntimeEnvironmentEntity environment = runtimeEnvironmentRepository.findById(environmentId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.RUNTIME_ENVIRONMENT_NOT_FOUND));
        HostEntity localHost = hostService.ensureLocalHost();
        if (environment.getHost() == null || !environment.getHost().getId().equals(localHost.getId())) {
            throw new BusinessException(ErrorSubCode.PIPELINE_ENV_MUST_BE_LOCAL);
        }
        return environment;
    }

    /**
     * 发布阶段的 Java 环境必须来自目标主机，避免把本机构建环境误用于远端运行。
     */
    private RuntimeEnvironmentEntity resolveRuntimeJavaEnvironment(Long environmentId, HostEntity targetHost) {
        if (environmentId == null) {
            return null;
        }
        RuntimeEnvironmentEntity environment = runtimeEnvironmentRepository.findById(environmentId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.RUNTIME_ENVIRONMENT_NOT_FOUND));
        if (environment.getType() != top.fusb.deploybot.model.RuntimeEnvironmentType.JAVA) {
            throw new BusinessException(ErrorSubCode.PIPELINE_RUNTIME_ENV_MUST_BE_JAVA);
        }
        if (targetHost == null || environment.getHost() == null || !environment.getHost().getId().equals(targetHost.getId())) {
            throw new BusinessException(ErrorSubCode.PIPELINE_RUNTIME_ENV_MUST_MATCH_TARGET_HOST);
        }
        return environment;
    }

    private String normalizeText(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeMultilineText(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content.replace("\r\n", "\n").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Integer normalizeStartupTimeout(Integer startupTimeoutSeconds) {
        if (startupTimeoutSeconds == null) {
            return null;
        }
        return Math.max(5, startupTimeoutSeconds);
    }

    private String normalizeNotificationBindings(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        List<NotificationBinding> bindings = jsonMapper.read(content, new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        List<NotificationBinding> normalizedBindings = bindings.stream()
                .filter(item -> item != null && item.notificationId() != null && item.eventType() != null)
                .toList();
        Set<String> uniquePairs = normalizedBindings.stream()
                .map(item -> item.notificationId() + ":" + item.eventType().name())
                .collect(Collectors.toSet());
        if (uniquePairs.size() != normalizedBindings.size()) {
            throw new BusinessException(ErrorSubCode.PIPELINE_NOTIFICATION_BINDING_INVALID);
        }
        Set<Long> ids = normalizedBindings.stream()
                .map(NotificationBinding::notificationId)
                .collect(Collectors.toSet());
        if (!ids.isEmpty() && notificationChannelRepository.findAllById(ids).size() != ids.size()) {
            throw new BusinessException(ErrorSubCode.PIPELINE_NOTIFICATION_BINDING_INVALID);
        }
        return jsonMapper.write(normalizedBindings);
    }

    private boolean matchesKeyword(PipelineEntity item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(item.getName(), normalized)
                || contains(item.getDescription(), normalized)
                || contains(item.getDefaultBranch(), normalized);
    }

    private boolean containsAllTags(PipelineEntity item, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return true;
        }
        List<String> currentTags = parseTags(item.getTagsJson());
        return tags.stream().allMatch(currentTags::contains);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private List<String> parseTags(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return jsonMapper.read(content, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
        });
    }
}
