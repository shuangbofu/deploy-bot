package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.PipelineRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentEntity;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.repo.HostRepository;
import top.fusb.deploybot.repo.RuntimeEnvironmentRepository;
import top.fusb.deploybot.repo.PipelineRepository;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.repo.ProjectRepository;
import top.fusb.deploybot.repo.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PipelineService {

    private final PipelineRepository pipelineRepository;
    private final ProjectRepository projectRepository;
    private final TemplateRepository templateRepository;
    private final RuntimeEnvironmentRepository runtimeEnvironmentRepository;
    private final DeploymentRepository deploymentRepository;
    private final HostRepository hostRepository;
    private final HostService hostService;

    public PipelineService(
            PipelineRepository pipelineRepository,
            ProjectRepository projectRepository,
            TemplateRepository templateRepository,
            RuntimeEnvironmentRepository runtimeEnvironmentRepository,
            DeploymentRepository deploymentRepository,
            HostRepository hostRepository,
            HostService hostService
    ) {
        this.pipelineRepository = pipelineRepository;
        this.projectRepository = projectRepository;
        this.templateRepository = templateRepository;
        this.runtimeEnvironmentRepository = runtimeEnvironmentRepository;
        this.deploymentRepository = deploymentRepository;
        this.hostRepository = hostRepository;
        this.hostService = hostService;
    }

    public List<PipelineEntity> findAll() {
        return pipelineRepository.findAll();
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
        entity.setJavaEnvironment(resolveEnvironment(request.javaEnvironmentId()));
        entity.setNodeEnvironment(resolveEnvironment(request.nodeEnvironmentId()));
        entity.setMavenEnvironment(resolveEnvironment(request.mavenEnvironmentId()));
        return pipelineRepository.save(entity);
    }

    public void delete(Long id) {
        long deploymentCount = deploymentRepository.countByPipelineId(id);
        if (deploymentCount > 0) {
            throw new BusinessException(ErrorSubCode.PIPELINE_HAS_DEPLOYMENTS);
        }
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
}
