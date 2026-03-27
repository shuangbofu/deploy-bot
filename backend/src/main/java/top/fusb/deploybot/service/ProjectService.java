package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.ProjectConnectionTestResult;
import top.fusb.deploybot.dto.ProjectRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.GitAuthType;
import top.fusb.deploybot.model.ProjectEntity;
import top.fusb.deploybot.repo.ProjectRepository;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository repository;
    private final GitCredentialService gitCredentialService;

    public ProjectService(ProjectRepository repository, GitCredentialService gitCredentialService) {
        this.repository = repository;
        this.gitCredentialService = gitCredentialService;
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
     * 按当前表单里的仓库地址和认证方式，真实执行一次 git ls-remote 来验证连通性。
     */
    public ProjectConnectionTestResult testConnection(ProjectRequest request) {
        ProjectEntity project = new ProjectEntity();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setGitUrl(request.gitUrl());
        project.setGitAuthType(request.gitAuthType() == null ? GitAuthType.NONE : request.gitAuthType());
        project.setGitUsername(trimToNull(request.gitUsername()));
        project.setGitPassword(trimToNull(request.gitPassword()));
        project.setGitSshPrivateKey(trimToNull(request.gitSshPrivateKey()));
        project.setGitSshPublicKey(trimToNull(request.gitSshPublicKey()));
        project.setGitSshKnownHosts(trimToNull(request.gitSshKnownHosts()));

        try {
            Path tempDir = Files.createTempDirectory("deploybot-git-test-");
            try {
                GitCredentialService.GitProcessConfig processConfig = gitCredentialService.buildProcessConfig(project, tempDir);
                ProcessBuilder processBuilder = new ProcessBuilder(
                        gitCredentialService.getGitExecutable(),
                        "ls-remote",
                        "--heads",
                        processConfig.gitUrl()
                );
                processBuilder.directory(tempDir.toFile());
                processBuilder.redirectErrorStream(true);
                processBuilder.environment().putAll(processConfig.environment());
                processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");

                Process process = processBuilder.start();
                boolean finished = process.waitFor(Duration.ofSeconds(20).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new BusinessException(ErrorSubCode.PROJECT_GIT_CONNECTIVITY_FAILED, "连接 Git 仓库超时。");
                }

                String output;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    output = reader.lines().collect(Collectors.joining("\n"));
                }

                String summarizedOutput = summarizeOutput(output);
                if (process.exitValue() != 0) {
                    throw new BusinessException(ErrorSubCode.PROJECT_GIT_CONNECTIVITY_FAILED, summarizedOutput);
                }

                String message = summarizedOutput.isBlank() ? "Git 仓库连通性测试成功。" : "Git 仓库连通性测试成功。已获取到远端引用。";
                return new ProjectConnectionTestResult(
                        true,
                        message,
                        processConfig.gitUrl(),
                        project.getGitAuthType().name(),
                        summarizedOutput
                );
            } finally {
                deleteRecursively(tempDir);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorSubCode.PROJECT_GIT_CONNECTIVITY_FAILED, ex.getMessage(), ex);
        }
    }

    /**
     * 表单里未填写的字段统一落成 null，避免数据库里出现大量空字符串。
     */
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String summarizeOutput(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        return output.lines()
                .limit(12)
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(item -> {
                        try {
                            Files.deleteIfExists(item);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
