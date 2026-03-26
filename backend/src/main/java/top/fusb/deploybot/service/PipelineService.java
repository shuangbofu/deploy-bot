package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.PipelineRequest;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentEntity;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.repo.HostRepository;
import top.fusb.deploybot.repo.RuntimeEnvironmentRepository;
import top.fusb.deploybot.repo.PipelineRepository;
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
    private final HostRepository hostRepository;
    private final HostService hostService;

    public PipelineService(
            PipelineRepository pipelineRepository,
            ProjectRepository projectRepository,
            TemplateRepository templateRepository,
            RuntimeEnvironmentRepository runtimeEnvironmentRepository,
            HostRepository hostRepository,
            HostService hostService
    ) {
        this.pipelineRepository = pipelineRepository;
        this.projectRepository = projectRepository;
        this.templateRepository = templateRepository;
        this.runtimeEnvironmentRepository = runtimeEnvironmentRepository;
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
        PipelineEntity entity = id == null ? new PipelineEntity() : pipelineRepository.findById(id).orElseThrow();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setProject(projectRepository.findById(request.projectId()).orElseThrow());
        entity.setTemplate(templateRepository.findById(request.templateId()).orElseThrow());
        HostEntity targetHost = hostRepository.findById(request.targetHostId()).orElseThrow();
        entity.setTargetHost(targetHost);
        entity.setDefaultBranch(request.defaultBranch());
        entity.setVariablesJson(request.variablesJson());
        entity.setJavaEnvironment(resolveEnvironment(request.javaEnvironmentId()));
        entity.setNodeEnvironment(resolveEnvironment(request.nodeEnvironmentId()));
        entity.setMavenEnvironment(resolveEnvironment(request.mavenEnvironmentId()));
        return pipelineRepository.save(entity);
    }

    public void delete(Long id) {
        pipelineRepository.deleteById(id);
    }

    /**
     * 当前执行模型约定所有构建都在本机完成，因此构建环境必须来自本机。
     */
    private RuntimeEnvironmentEntity resolveEnvironment(Long environmentId) {
        if (environmentId == null) {
            return null;
        }
        RuntimeEnvironmentEntity environment = runtimeEnvironmentRepository.findById(environmentId).orElseThrow();
        HostEntity localHost = hostService.ensureLocalHost();
        if (environment.getHost() == null || !environment.getHost().getId().equals(localHost.getId())) {
            throw new IllegalStateException("流水线构建环境只能选择本机下的运行环境。");
        }
        return environment;
    }
}
