package top.fusb.deploybot.service;

import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.kit.ProcessKit;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.model.ProjectEntity;
import top.fusb.deploybot.repo.PipelineRepository;
import top.fusb.deploybot.repo.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GitBranchService {

    private static final Logger log = LoggerFactory.getLogger(GitBranchService.class);

    private final PipelineRepository pipelineRepository;
    private final ProjectRepository projectRepository;
    private final GitCredentialService gitCredentialService;

    /**
     * 按流水线读取远端分支，并把流水线当前默认分支排在最前面。
     *
     * @param pipelineId 流水线 ID
     * @return 可用于部署或编辑的远端分支列表；读取失败时返回当前默认分支
     */
    public List<String> listBranches(Long pipelineId) {
        PipelineEntity pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PIPELINE_NOT_FOUND));
        return listBranches(pipeline.getProject(), pipeline.getDefaultBranch());
    }

    /**
     * 按项目读取远端分支，供新建或编辑流水线时选择默认分支。
     *
     * @param projectId 项目 ID
     * @param preferredBranch 需要额外保留并优先展示的分支，例如当前表单里的默认分支
     * @return 可用于默认分支下拉的远端分支列表；读取失败时返回优先分支
     */
    public List<String> listProjectBranches(Long projectId, String preferredBranch) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PROJECT_NOT_FOUND));
        return listBranches(project, preferredBranch);
    }

    private List<String> listBranches(ProjectEntity project, String preferredBranch) {
        String sourceGitUrl = project.getGitUrl();
        String defaultBranch = preferredBranch == null || preferredBranch.isBlank() ? "main" : preferredBranch.trim();
        Path tempDir = null;

        try {
            tempDir = Files.createTempDirectory("deploybot-git-branches-");
            GitCredentialService.GitProcessConfig processConfig = gitCredentialService.buildProcessConfig(project, tempDir);
            ProcessBuilder processBuilder = ProcessKit.mergedBuilder(gitCredentialService.getGitExecutable(), "ls-remote", "--heads", processConfig.gitUrl());
            processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
            processBuilder.environment().put("GIT_ASKPASS", "echo");
            processBuilder.environment().putAll(processConfig.environment());
            ProcessKit.ProcessResult result = ProcessKit.runAndCapture(processBuilder);

            List<String> branches = new ArrayList<>();
            if (result.output() != null) {
                for (String line : result.output().split("\\R")) {
                    int refIndex = line.indexOf("refs/heads/");
                    if (refIndex >= 0) {
                        branches.add(line.substring(refIndex + "refs/heads/".length()).trim());
                    }
                }
            }

            if (result.exitCode() != 0) {
                log.warn("Unable to load branches from git repository {}, fallback to default branch {}", sourceGitUrl, defaultBranch);
                return List.of(defaultBranch);
            }

            Set<String> uniqueBranches = new LinkedHashSet<>(branches);
            uniqueBranches.add(defaultBranch);

            return uniqueBranches.stream()
                    .sorted(Comparator.comparing((String branch) -> !branch.equals(defaultBranch))
                            .thenComparing(String::compareToIgnoreCase))
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to query branches from git repository {}, fallback to default branch {}", sourceGitUrl, defaultBranch, ex);
            return List.of(defaultBranch);
        } finally {
            deleteRecursively(tempDir);
        }
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
