package top.fusb.deploybot.service;

import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.kit.ProcessKit;
import top.fusb.deploybot.model.SystemSettingsEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class SystemSshKeyService {

    private final SystemSettingsService systemSettingsService;

    public SystemSettingsEntity generateKeyPair() throws IOException, InterruptedException {
        return generateKeyPair("deploy-bot", true);
    }

    public SystemSettingsEntity generateHostKeyPair() throws IOException, InterruptedException {
        return generateKeyPair("deploy-bot-host", false);
    }

    private SystemSettingsEntity generateKeyPair(String comment, boolean gitKeyPair) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("deploybot-ssh-keygen-");
        Path keyPath = tempDir.resolve("id_ed25519");

        ProcessKit.ProcessResult result = ProcessKit.runAndCapture(ProcessKit.mergedBuilder(
                "ssh-keygen",
                "-t", "ed25519",
                "-N", "",
                "-C", comment,
                "-f", keyPath.toAbsolutePath().toString()
        ));
        if (result.exitCode() != 0) {
            throw new BusinessException(ErrorSubCode.SSH_KEY_GENERATE_FAILED, result.output());
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
