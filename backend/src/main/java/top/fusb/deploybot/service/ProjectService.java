package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.ProjectConnectionTestResult;
import top.fusb.deploybot.dto.ProjectRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.GitAuthType;
import top.fusb.deploybot.model.ProjectEntity;
import top.fusb.deploybot.repo.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

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

        log.info(
                "开始测试项目仓库连通性：project='{}', gitUrl='{}', gitAuthType='{}'.",
                project.getName(),
                project.getGitUrl(),
                project.getGitAuthType()
        );

        try {
            Path tempDir = Files.createTempDirectory("deploybot-git-test-");
            try {
                GitCredentialService.GitProcessConfig processConfig = gitCredentialService.buildProcessConfig(project, tempDir);
                log.info(
                        "项目仓库连通性测试已生成 Git 进程配置：project='{}', resolvedGitUrl='{}', hasSshCommand={}.",
                        project.getName(),
                        processConfig.gitUrl(),
                        processConfig.environment().containsKey("GIT_SSH_COMMAND")
                );
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
                log.info(
                        "执行 Git 连通性测试命令：project='{}', command='{}', workdir='{}'.",
                        project.getName(),
                        String.join(" ", processBuilder.command()),
                        tempDir.toAbsolutePath().normalize()
                );

                Process process = processBuilder.start();
                boolean finished = process.waitFor(Duration.ofSeconds(20).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("项目仓库连通性测试超时：project='{}', timeoutSeconds=20。", project.getName());
                    throw new BusinessException(ErrorSubCode.PROJECT_GIT_CONNECTIVITY_FAILED, "连接 Git 仓库超时。");
                }

                String output;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    output = reader.lines().collect(Collectors.joining("\n"));
                }

                String summarizedOutput = summarizeOutput(output, 20);
                log.info(
                        "项目仓库连通性测试结束：project='{}', exitCode={}, output='{}'.",
                        project.getName(),
                        process.exitValue(),
                        summarizedOutput
                );
                if (process.exitValue() != 0) {
                    logGitSshDiagnosticsIfNecessary(project, processConfig);
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
            log.warn(
                    "项目仓库连通性测试失败：project='{}', gitUrl='{}', reason='{}'.",
                    project.getName(),
                    project.getGitUrl(),
                    ex.getMessage()
            );
            throw ex;
        } catch (Exception ex) {
            log.error(
                    "项目仓库连通性测试发生未预期异常：project='{}', gitUrl='{}'.",
                    project.getName(),
                    project.getGitUrl(),
                    ex
            );
            throw new BusinessException(ErrorSubCode.PROJECT_GIT_CONNECTIVITY_FAILED, ex.getMessage(), ex);
        }
    }

    /**
     * 表单里未填写的字段统一落成 null，避免数据库里出现大量空字符串。
     */
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String summarizeOutput(String output, int maxLines) {
        if (output == null || output.isBlank()) {
            return "";
        }
        return output.lines()
                .limit(Math.max(1, maxLines))
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private void logGitSshDiagnosticsIfNecessary(ProjectEntity project, GitCredentialService.GitProcessConfig processConfig) {
        if (project.getGitAuthType() != GitAuthType.SSH) {
            return;
        }
        String sshCommand = processConfig.environment().get("GIT_SSH_COMMAND");
        if (sshCommand == null || sshCommand.isBlank()) {
            log.warn("项目 '{}' 使用 SSH 认证，但本次测试未生成 GIT_SSH_COMMAND。", project.getName());
            return;
        }

        SshTarget sshTarget = resolveSshTarget(processConfig.gitUrl());
        if (sshTarget == null || sshTarget.host() == null || sshTarget.host().isBlank()) {
            log.warn("项目 '{}' 无法从 Git 地址 '{}' 中解析 SSH 目标。", project.getName(), processConfig.gitUrl());
            return;
        }

        try {
            String remoteUser = sshTarget.username() == null || sshTarget.username().isBlank() ? "git" : sshTarget.username();
            String remoteHost = remoteUser + "@" + sshTarget.host();
            String portArgument = sshTarget.port() == null ? "" : " -p " + sshTarget.port();
            String diagnosticCommand = sshCommand + portArgument + " -vvv -T " + remoteHost;
            ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-lc", diagnosticCommand);
            builder.redirectErrorStream(true);
            builder.environment().putAll(processConfig.environment());
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");

            log.info(
                    "项目 '{}' Git SSH 诊断开始：sshHost='{}', sshPort='{}', sshUser='{}', command='{}'.",
                    project.getName(),
                    sshTarget.host(),
                    sshTarget.port(),
                    remoteUser,
                    diagnosticCommand
            );

            Process process = builder.start();
            boolean finished = process.waitFor(Duration.ofSeconds(15).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("项目 '{}' Git SSH 诊断超时。", project.getName());
                return;
            }

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            log.info(
                    "项目 '{}' Git SSH 诊断结束：exitCode={}, output='{}'.",
                    project.getName(),
                    process.exitValue(),
                    summarizeOutput(output, 80)
            );
        } catch (Exception ex) {
            log.warn("项目 '{}' Git SSH 诊断执行失败：{}", project.getName(), ex.getMessage(), ex);
        }
    }

    private SshTarget resolveSshTarget(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) {
            return null;
        }
        if (gitUrl.startsWith("ssh://")) {
            try {
                java.net.URI uri = java.net.URI.create(gitUrl);
                return new SshTarget(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : null, uri.getUserInfo());
            } catch (Exception ignored) {
                return null;
            }
        }
        if (gitUrl.startsWith("git@")) {
            int atIndex = gitUrl.indexOf('@');
            int colonIndex = gitUrl.indexOf(':', atIndex + 1);
            if (colonIndex > atIndex) {
                return new SshTarget(gitUrl.substring(atIndex + 1, colonIndex), null, gitUrl.substring(0, atIndex));
            }
        }
        return null;
    }

    private record SshTarget(String host, Integer port, String username) {
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
