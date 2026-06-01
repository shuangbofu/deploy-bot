package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.kit.ProcessKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.repo.DeploymentRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台生成部署 Git 差异。
 * 差异只用于详情页查看，不能阻塞构建、发布和启动判定主流程。
 */
@Service
@RequiredArgsConstructor
public class DeploymentGitDiffAsyncService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentGitDiffAsyncService.class);

    private final DeploymentRepository deploymentRepository;
    private final GitCredentialService gitCredentialService;

    @Async
    public void generateAsync(Long deploymentId) {
        if (deploymentId == null) {
            return;
        }
        deploymentRepository.findById(deploymentId).ifPresent(this::generate);
    }

    private void generate(DeploymentEntity deployment) {
        if (deployment.getPipeline() == null || deployment.getPipeline().getProject() == null || TextKit.isBlank(deployment.getCommitSha())) {
            return;
        }
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("currentCommit", deployment.getCommitSha());
        snapshot.put("currentShortCommit", shortCommit(deployment.getCommitSha()));
        snapshot.put("branch", deployment.getBranchName());
        deploymentRepository.findFirstByPipelineIdAndStatusAndIdLessThanAndCommitShaIsNotNullOrderByCreatedAtDesc(
                deployment.getPipeline().getId(),
                DeploymentStatus.SUCCESS,
                deployment.getId()
        ).ifPresentOrElse(previous -> {
            snapshot.put("previousDeploymentId", previous.getId());
            snapshot.put("previousCommit", previous.getCommitSha());
            snapshot.put("previousShortCommit", shortCommit(previous.getCommitSha()));
            fillDiffFromTemporaryRepo(deployment, previous.getCommitSha(), snapshot);
        }, () -> snapshot.put("message", "没有可对比的上一次成功部署。"));
        deployment.setGitDiffSnapshot(snapshot);
        deploymentRepository.save(deployment);
    }

    private void fillDiffFromTemporaryRepo(DeploymentEntity deployment, String previousSha, Map<String, Object> snapshot) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("deploybot-git-diff-");
            GitCredentialService.GitProcessConfig processConfig = gitCredentialService.buildProcessConfig(
                    deployment.getPipeline().getProject(),
                    tempDir
            );
            Path repoDir = tempDir.resolve("repo");
            runGit(tempDir, processConfig.environment(), "clone", "--filter=blob:none", "--no-checkout", processConfig.gitUrl(), repoDir.toString());
            fetchBranchHistory(repoDir, processConfig.environment(), deployment.getBranchName());
            fetchCommitIfMissing(repoDir, processConfig.environment(), deployment.getCommitSha());
            fetchCommitIfMissing(repoDir, processConfig.environment(), previousSha);
            String range = previousSha + ".." + deployment.getCommitSha();
            List<Map<String, Object>> commits = parseGitCommits(runGit(repoDir, Map.of(), "log", "--date=iso-strict", "--pretty=format:%H%x1f%h%x1f%an%x1f%ae%x1f%ad%x1f%s", range));
            snapshot.put("commits", commits);
            snapshot.put("commitCount", commits.size());
            snapshot.put("files", parseGitFiles(runGit(repoDir, Map.of(), "diff", "--name-status", range)));
            snapshot.put("stat", runGitBestEffort(repoDir, Map.of(), "diff", "--stat", "--summary", range));
        } catch (Exception ex) {
            snapshot.put("message", "部署差异生成失败，可能是浅历史、rebase、force push 或仓库暂时不可访问。");
            snapshot.put("error", ex.getMessage());
            log.warn("部署 {} 后台生成 Git 差异失败：{}", deployment.getId(), ex.getMessage());
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private void fetchBranchHistory(Path repoDir, Map<String, String> environment, String branchName) throws IOException, InterruptedException {
        if (TextKit.isBlank(branchName)) {
            return;
        }
        String branch = branchName.trim();
        try {
            runGit(repoDir, environment, "fetch", "--depth=500", "origin", branch);
            runGit(repoDir, environment, "branch", "deploybot-diff-base", "FETCH_HEAD");
        } catch (Exception ex) {
            log.warn("部署差异拉取分支历史失败，branch={}，原因={}", branch, ex.getMessage());
        }
    }

    private void fetchCommitIfMissing(Path repoDir, Map<String, String> environment, String commitSha) throws IOException, InterruptedException {
        if (TextKit.isBlank(commitSha)) {
            return;
        }
        if (commitExists(repoDir, commitSha)) {
            return;
        }
        runGit(repoDir, environment, "fetch", "--depth=500", "origin", commitSha);
    }

    private boolean commitExists(Path repoDir, String commitSha) {
        try {
            runGit(repoDir, Map.of(), "cat-file", "-e", commitSha + "^{commit}");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<Map<String, Object>> parseGitCommits(String output) {
        List<Map<String, Object>> commits = new ArrayList<>();
        if (TextKit.isBlank(output)) {
            return commits;
        }
        output.lines().filter(TextKit::isNotBlank).forEach(line -> {
            String[] parts = line.split("\\u001f", -1);
            Map<String, Object> item = new HashMap<>();
            item.put("sha", parts.length > 0 ? parts[0] : "");
            item.put("shortSha", parts.length > 1 ? parts[1] : shortCommit(parts.length > 0 ? parts[0] : ""));
            item.put("authorName", parts.length > 2 ? parts[2] : "");
            item.put("authorEmail", parts.length > 3 ? parts[3] : "");
            item.put("date", parts.length > 4 ? parts[4] : "");
            item.put("message", parts.length > 5 ? parts[5] : "");
            commits.add(item);
        });
        return commits;
    }

    private List<Map<String, Object>> parseGitFiles(String output) {
        List<Map<String, Object>> files = new ArrayList<>();
        if (TextKit.isBlank(output)) {
            return files;
        }
        output.lines().filter(TextKit::isNotBlank).forEach(line -> {
            String[] parts = line.split("\\t", -1);
            Map<String, Object> item = new HashMap<>();
            item.put("status", parts.length > 0 ? parts[0] : "");
            item.put("path", parts.length > 1 ? parts[1] : "");
            if (parts.length > 2) {
                item.put("newPath", parts[2]);
            }
            files.add(item);
        });
        return files;
    }

    private String shortCommit(String sha) {
        if (TextKit.isBlank(sha)) {
            return "";
        }
        String text = sha.trim();
        return text.length() <= 8 ? text : text.substring(0, 8);
    }

    private String runGit(Path directory, Map<String, String> environment, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(gitCredentialService.getGitExecutable());
        command.addAll(List.of(args));
        ProcessBuilder processBuilder = ProcessKit.mergedBuilder(command)
                .directory(directory.toFile());
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().putAll(environment);
        processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
        processBuilder.environment().put("GIT_ASKPASS", "echo");
        Process process = processBuilder.start();
        String output;
        try (InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " exited with code " + exitCode + ": " + output);
        }
        return output == null ? "" : output.trim();
    }

    private String runGitBestEffort(Path directory, Map<String, String> environment, String... args) {
        try {
            return runGit(directory, environment, args);
        } catch (Exception ex) {
            log.warn("执行可选 Git 命令失败：git {}，原因={}", String.join(" ", args), ex.getMessage());
            return "";
        }
    }

    private void deleteDirectory(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ex) {
                    log.warn("清理 Git 差异临时目录失败：{} -> {}", item, ex.getMessage());
                }
            });
        } catch (Exception ex) {
            log.warn("清理 Git 差异临时目录失败：{} -> {}", path, ex.getMessage());
        }
    }
}
