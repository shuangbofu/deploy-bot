package top.fusb.deploybot.service;

import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.SystemSettingsEntity;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

@Service
public class SystemSshKeyService {

    private final SystemSettingsService systemSettingsService;

    public SystemSshKeyService(SystemSettingsService systemSettingsService) {
        this.systemSettingsService = systemSettingsService;
    }

    public SystemSettingsEntity generateKeyPair() throws IOException, InterruptedException {
        return generateKeyPair("deploy-bot", true);
    }

    public SystemSettingsEntity generateHostKeyPair() throws IOException, InterruptedException {
        return generateKeyPair("deploy-bot-host", false);
    }

    private SystemSettingsEntity generateKeyPair(String comment, boolean gitKeyPair) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("deploybot-ssh-keygen-");
        Path keyPath = tempDir.resolve("id_ed25519");

        Process process = new ProcessBuilder(
                "ssh-keygen",
                "-t", "ed25519",
                "-N", "",
                "-C", comment,
                "-f", keyPath.toAbsolutePath().toString()
        ).redirectErrorStream(true).start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new BusinessException(ErrorSubCode.SSH_KEY_GENERATE_FAILED, output);
        }

        String privateKey = Files.readString(keyPath, StandardCharsets.UTF_8);
        String publicKey = Files.readString(keyPath.resolveSibling("id_ed25519.pub"), StandardCharsets.UTF_8);

        SystemSettingsEntity settings = systemSettingsService.get();
        if (gitKeyPair) {
            settings.setGitSshPrivateKey(privateKey);
            settings.setGitSshPublicKey(publicKey);
        } else {
            settings.setHostSshPrivateKey(privateKey);
            settings.setHostSshPublicKey(publicKey);
        }
        return systemSettingsService.saveEntity(settings);
    }
}
