package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.ProjectRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.GitAuthType;
import top.fusb.deploybot.model.ProjectEntity;
import top.fusb.deploybot.repo.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    public List<ProjectEntity> findAll() {
        return repository.findAll();
    }

    /**
     * 项目负责保存仓库接入信息，因此这里会同时处理名称、描述与 Git 认证配置。
     */
    public ProjectEntity save(ProjectRequest request, Long id) {
        ProjectEntity entity = id == null ? new ProjectEntity() : repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PROJECT_NOT_FOUND));
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setGitUrl(request.gitUrl());
        entity.setGitAuthType(request.gitAuthType() == null ? GitAuthType.NONE : request.gitAuthType());
        entity.setGitUsername(trimToNull(request.gitUsername()));
        entity.setGitPassword(trimToNull(request.gitPassword()));
        entity.setGitSshPrivateKey(trimToNull(request.gitSshPrivateKey()));
        entity.setGitSshPublicKey(trimToNull(request.gitSshPublicKey()));
        entity.setGitSshKnownHosts(trimToNull(request.gitSshKnownHosts()));
        return repository.save(entity);
    }

    public void delete(Long id) {
        repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PROJECT_NOT_FOUND));
        repository.deleteById(id);
    }

    /**
     * 表单里未填写的字段统一落成 null，避免数据库里出现大量空字符串。
     */
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
