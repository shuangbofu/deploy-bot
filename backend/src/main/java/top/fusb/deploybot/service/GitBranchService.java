package top.fusb.deploybot.service;

import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.repo.PipelineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GitBranchService {

    private static final Logger log = LoggerFactory.getLogger(GitBranchService.class);

    private final PipelineRepository pipelineRepository;
    private final GitCredentialService gitCredentialService;

    public GitBranchService(PipelineRepository pipelineRepository, GitCredentialService gitCredentialService) {
        this.pipelineRepository = pipelineRepository;
        this.gitCredentialService = gitCredentialService;
    }

    public List<String> listBranches(Long pipelineId) {
        PipelineEntity pipeline = pipelineRepository.findById(pipelineId).orElseThrow();
        String sourceGitUrl = pipeline.getProject().getGitUrl();
        String defaultBranch = pipeline.getDefaultBranch();

        try {
            Path tempDir = Files.createTempDirectory("deploybot-git-branches-");
            GitCredentialService.GitProcessConfig processConfig = gitCredentialService.buildProcessConfig(pipeline.getProject(), tempDir);
            ProcessBuilder processBuilder = new ProcessBuilder(gitCredentialService.getGitExecutable(), "ls-remote", "--heads", processConfig.gitUrl())
                    .redirectErrorStream(true);
            processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
            processBuilder.environment().put("GIT_ASKPASS", "echo");
            processBuilder.environment().putAll(processConfig.environment());
            Process process = processBuilder.start();

            List<String> branches = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int refIndex = line.indexOf("refs/heads/");
                    if (refIndex >= 0) {
                        branches.add(line.substring(refIndex + "refs/heads/".length()).trim());
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
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
        }
    }
}
