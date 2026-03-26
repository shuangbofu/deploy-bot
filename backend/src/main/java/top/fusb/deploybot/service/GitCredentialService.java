package top.fusb.deploybot.service;

import top.fusb.deploybot.model.GitAuthType;
import top.fusb.deploybot.model.ProjectEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;

@Service
public class GitCredentialService {

    public record GitProcessConfig(String gitUrl, Map<String, String> environment) {
    }

    private final SystemSettingsService systemSettingsService;

    public GitCredentialService(SystemSettingsService systemSettingsService) {
        this.systemSettingsService = systemSettingsService;
    }

    public GitProcessConfig buildProcessConfig(ProjectEntity project, Path tempDir) throws IOException {
        String gitUrl = project.getGitUrl();
        GitAuthType authType = project.getGitAuthType() == null ? GitAuthType.NONE : project.getGitAuthType();

        return switch (authType) {
            case BASIC -> new GitProcessConfig(applyBasicCredentials(gitUrl, project), Map.of());
            case SSH -> new GitProcessConfig(gitUrl, buildSshEnvironment(tempDir));
            case NONE -> new GitProcessConfig(gitUrl, Map.of());
        };
    }

    public String resolveGitUrl(ProjectEntity project) {
        String gitUrl = project.getGitUrl();
        GitAuthType authType = project.getGitAuthType() == null ? GitAuthType.NONE : project.getGitAuthType();
        if (authType != GitAuthType.BASIC) {
            return gitUrl;
        }
        return applyBasicCredentials(gitUrl, project);
    }

    public String getGitExecutable() {
        String gitExecutable = systemSettingsService.get().getGitExecutable();
        return gitExecutable == null || gitExecutable.isBlank() ? "git" : gitExecutable.trim();
    }

    private String applyBasicCredentials(String gitUrl, ProjectEntity project) {
        if (gitUrl == null || gitUrl.isBlank()) {
            return gitUrl;
        }
        if (!gitUrl.startsWith("http://") && !gitUrl.startsWith("https://")) {
            return gitUrl;
        }
        if (project.getGitPassword() == null || project.getGitPassword().isBlank()) {
            return gitUrl;
        }

        try {
            URI uri = URI.create(gitUrl);
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                return gitUrl;
            }
            String username = firstNonBlank(project.getGitUsername(), "git");
            String userInfo = encode(username) + ":" + encode(project.getGitPassword().trim());
            URI securedUri = new URI(
                    uri.getScheme(),
                    userInfo,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
            return securedUri.toString();
        } catch (Exception ex) {
            return gitUrl;
        }
    }

    private Map<String, String> buildSshEnvironment(Path tempDir) throws IOException {
        var settings = systemSettingsService.get();
        if (settings.getGitSshPrivateKey() == null || settings.getGitSshPrivateKey().isBlank()) {
            return Map.of();
        }

        Files.createDirectories(tempDir);
        Path privateKey = tempDir.resolve("id_deploybot");
        Path publicKey = tempDir.resolve("id_deploybot.pub");
        Path knownHosts = tempDir.resolve("known_hosts");

        Files.writeString(privateKey, normalizeKey(settings.getGitSshPrivateKey()), StandardCharsets.UTF_8);
        if (settings.getGitSshPublicKey() != null && !settings.getGitSshPublicKey().isBlank()) {
            Files.writeString(publicKey, normalizeKey(settings.getGitSshPublicKey()), StandardCharsets.UTF_8);
        }
        if (settings.getGitSshKnownHosts() != null && !settings.getGitSshKnownHosts().isBlank()) {
            Files.writeString(knownHosts, settings.getGitSshKnownHosts().trim() + "\n", StandardCharsets.UTF_8);
        }

        ensurePrivateKeyPermissions(privateKey);

        String sshCommand;
        if (Files.exists(knownHosts)) {
            sshCommand = "ssh -i \"" + privateKey.toAbsolutePath() + "\" -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=\"" + knownHosts.toAbsolutePath() + "\"";
        } else {
            sshCommand = "ssh -i \"" + privateKey.toAbsolutePath() + "\" -o IdentitiesOnly=yes -o StrictHostKeyChecking=no";
        }

        return Map.of("GIT_SSH_COMMAND", sshCommand);
    }

    private void ensurePrivateKeyPermissions(Path privateKey) {
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(privateKey, permissions);
        } catch (Exception ignored) {
        }
    }

    private String normalizeKey(String key) {
        String normalized = key.replace("\r\n", "\n").trim();
        return normalized.endsWith("\n") ? normalized : normalized + "\n";
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
