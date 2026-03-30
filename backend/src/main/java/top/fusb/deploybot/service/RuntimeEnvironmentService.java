package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.RuntimeEnvironmentRequest;
import top.fusb.deploybot.dto.RuntimeEnvironmentDetection;
import top.fusb.deploybot.dto.RuntimeEnvironmentInstallRequest;
import top.fusb.deploybot.dto.RuntimeEnvironmentPreset;
import com.fasterxml.jackson.core.type.TypeReference;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.model.RuntimeEnvironmentEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentType;
import top.fusb.deploybot.repo.HostRepository;
import top.fusb.deploybot.repo.RuntimeEnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.stream.Stream;

@Service
public class RuntimeEnvironmentService {
    /**
     * 运行环境服务负责三件事：
     * 1. 管理运行环境元数据
     * 2. 检测主机已有环境
     * 3. 下载并安装预置环境
     */

    private static final Logger log = LoggerFactory.getLogger(RuntimeEnvironmentService.class);

    private final RuntimeEnvironmentRepository repository;
    private final HostRepository hostRepository;
    private final JsonMapper jsonMapper;
    private final SystemSettingsService systemSettingsService;
    private final HostService hostService;
    private final ResourceLoader resourceLoader;
    private final String presetsResourceLocation;

    public RuntimeEnvironmentService(
            RuntimeEnvironmentRepository repository,
            HostRepository hostRepository,
            JsonMapper jsonMapper,
            SystemSettingsService systemSettingsService,
            HostService hostService,
            ResourceLoader resourceLoader,
            @Value("${deploybot.runtime-environment-presets:classpath:runtime-environment-presets.json}") String presetsResourceLocation
    ) {
        this.repository = repository;
        this.hostRepository = hostRepository;
        this.jsonMapper = jsonMapper;
        this.systemSettingsService = systemSettingsService;
        this.hostService = hostService;
        this.resourceLoader = resourceLoader;
        this.presetsResourceLocation = presetsResourceLocation;
    }

    /**
     * 查询指定主机下的运行环境；未指定主机时返回全量。
     */
    public List<RuntimeEnvironmentEntity> findAll(Long hostId) {
        hostService.ensureLocalHost();
        return hostId == null
                ? repository.findAllByOrderByTypeAscNameAsc()
                : repository.findAllByHostIdOrderByTypeAscNameAsc(hostId);
    }

    /**
     * 查询启用状态的运行环境，主要供流水线下拉框使用。
     */
    public List<RuntimeEnvironmentEntity> findEnabledByType(RuntimeEnvironmentType type, Long hostId) {
        hostService.ensureLocalHost();
        return hostId == null
                ? repository.findByTypeAndEnabledTrueOrderByNameAsc(type)
                : repository.findByHostIdAndTypeAndEnabledTrueOrderByNameAsc(hostId, type);
    }

    public RuntimeEnvironmentEntity findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.RUNTIME_ENVIRONMENT_NOT_FOUND));
    }

    /**
     * 保存运行环境元信息，包括路径、激活脚本和附加环境变量。
     */
    public RuntimeEnvironmentEntity save(RuntimeEnvironmentRequest request, Long id) {
        RuntimeEnvironmentEntity entity = id == null ? new RuntimeEnvironmentEntity() : repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.RUNTIME_ENVIRONMENT_NOT_FOUND));
        HostEntity host = hostRepository.findById(request.hostId())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.HOST_NOT_FOUND));
        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setHost(host);
        entity.setVersion(request.version());
        entity.setHomePath(request.homePath());
        entity.setBinPath(request.binPath());
        entity.setActivationScript(request.activationScript());
        entity.setEnvironmentJson(request.environmentJson());
        entity.setEnabled(request.enabled() == null || request.enabled());
        return repository.save(entity);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * 自动检测本机可直接使用的 Java / Node / Maven 环境。
     */
    public List<RuntimeEnvironmentDetection> detectAvailable() {
        hostService.ensureLocalHost();
        return Stream.of(
                        detectJava(),
                        detectNode(),
                        detectMaven()
                )
                .filter(item -> item != null)
                .toList();
    }

    public List<RuntimeEnvironmentDetection> detectAvailable(Long hostId) {
        HostEntity host = hostId == null ? hostService.ensureLocalHost() : hostRepository.findById(hostId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.HOST_NOT_FOUND));
        if (host.getType() == HostType.LOCAL) {
            return detectAvailable();
        }
        return Stream.of(
                        detectRemoteVersion(host, RuntimeEnvironmentType.JAVA, "Java", "java -version 2>&1 | head -n 1", "echo \"$JAVA_HOME\"", "echo \"$JAVA_HOME/bin\""),
                        detectRemoteVersion(host, RuntimeEnvironmentType.NODE, "Node", "node -v 2>&1 | head -n 1", "echo \"$NODE_HOME\"", "echo \"$NODE_HOME/bin\""),
                        detectRemoteVersion(host, RuntimeEnvironmentType.MAVEN, "Maven", "mvn -version 2>&1 | head -n 1", "echo \"$MAVEN_HOME\"", "echo \"$MAVEN_HOME/bin\"")
                )
                .filter(item -> item != null)
                .toList();
    }

    public List<RuntimeEnvironmentPreset> listPresets(Long hostId) {
        HostEntity host = hostId == null ? hostService.ensureLocalHost() : hostRepository.findById(hostId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.HOST_NOT_FOUND));
        PlatformInfo platform = detectPlatform(host);
        Path installRoot = installRoot(host);
        Map<String, String> variables = buildPresetTemplateVariables(platform, installRoot);

        return loadPresetDefinitions().stream()
                .map(definition -> new RuntimeEnvironmentPreset(
                        definition.id(),
                        definition.name(),
                        definition.type(),
                        definition.version(),
                        definition.description(),
                        applyPresetTemplate(definition.downloadUrlTemplate(), variables),
                        applyPresetTemplate(definition.homePathTemplate(), variables),
                        applyPresetTemplate(definition.binPathTemplate(), variables)
                ))
                .toList();
    }

    public RuntimeEnvironmentEntity installPreset(RuntimeEnvironmentInstallRequest request) throws IOException, InterruptedException {
        HostEntity host = hostRepository.findById(request.hostId())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.HOST_NOT_FOUND));
        RuntimeEnvironmentPreset preset = listPresets(host.getId()).stream()
                .filter(item -> item.id().equals(request.presetId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PRESET_NOT_FOUND));

        log.info("Start installing preset {} ({}) on host {} ({})", preset.name(), preset.id(), host.getName(), host.getType());

        if (host.getType() == HostType.SSH) {
            RuntimeEnvironmentEntity entity = installPresetOnRemoteHost(host, preset);
            log.info("Preset {} installed on remote host {}", preset.name(), host.getName());
            return entity;
        }

        Path downloadsDir = installRoot(host).resolve("downloads");
        Path toolsDir = installRoot(host).resolve("tools");
        Files.createDirectories(downloadsDir);
        Files.createDirectories(toolsDir);

        String archiveName = preset.downloadUrl().substring(preset.downloadUrl().lastIndexOf('/') + 1);
        Path archiveFile = downloadsDir.resolve(archiveName.isBlank() ? UUID.randomUUID() + ".archive" : archiveName);

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(preset.downloadUrl())).GET().build();
        log.info("Downloading preset {} from {}", preset.name(), preset.downloadUrl());
        HttpResponse<Path> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofFile(archiveFile));
        if (response.statusCode() >= 400) {
            log.warn("Preset download failed for {} with HTTP {}", preset.name(), response.statusCode());
            throw new BusinessException(ErrorSubCode.PRESET_DOWNLOAD_FAILED, "HTTP 状态码：" + response.statusCode());
        }
        validateArchive(archiveFile);
        log.info("Preset {} archive validated: {}", preset.name(), archiveFile);

        Path extractDir = Files.createTempDirectory(installRoot(host), "extract-");
        Process process = new ProcessBuilder("tar", "-xf", archiveFile.toAbsolutePath().toString(), "-C", extractDir.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().reduce("", (left, right) -> left + right + "\n");
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("Preset extract failed for {}: {}", preset.name(), output);
            throw new BusinessException(ErrorSubCode.PRESET_EXTRACT_FAILED, output);
        }

        Path extractedRoot = Files.list(extractDir)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PRESET_EXTRACT_ROOT_MISSING));

        Path finalHome = Path.of(preset.homePath());
        deleteIfExists(finalHome);
        Files.createDirectories(finalHome.getParent());
        Files.move(extractedRoot, finalHome, StandardCopyOption.REPLACE_EXISTING);

        RuntimeEnvironmentRequest saveRequest = new RuntimeEnvironmentRequest(
                preset.name(),
                preset.type(),
                host.getId(),
                preset.version(),
                finalHome.toString(),
                preset.binPath(),
                null,
                jsonMapper.write(Map.of()),
                true
        );
        RuntimeEnvironmentEntity entity = save(saveRequest, null);
        log.info("Preset {} installed on local host {}", preset.name(), host.getName());
        return entity;
    }

    @Async
    public void installPresetAsync(RuntimeEnvironmentInstallRequest request) {
        try {
            installPreset(request);
        } catch (Exception ex) {
            log.warn("Preset install failed in background: presetId={}, hostId={}, reason={}",
                    request.presetId(), request.hostId(), ex.getMessage(), ex);
        }
    }

    private RuntimeEnvironmentEntity installPresetOnRemoteHost(HostEntity host, RuntimeEnvironmentPreset preset) {
        try {
            log.info("Installing preset {} on remote host {} via SSH", preset.name(), host.getName());
            String workspaceRoot = installRoot(host).toString();
            String script = """
                    set -e
                    WORKSPACE="%s"
                    DOWNLOADS_DIR="$WORKSPACE/downloads"
                    TOOLS_DIR="$WORKSPACE/tools"
                    ARCHIVE_FILE="$DOWNLOADS_DIR/%s"
                    FINAL_HOME="%s"
                    EXTRACT_DIR="$(mktemp -d "$WORKSPACE/extract-XXXXXX")"
                    mkdir -p "$DOWNLOADS_DIR" "$TOOLS_DIR"
                    rm -rf "$FINAL_HOME"
                    curl -fL --retry 2 --connect-timeout 20 "%s" -o "$ARCHIVE_FILE"
                    if ! tar -tf "$ARCHIVE_FILE" >/dev/null 2>&1; then
                      echo "下载结果不是有效压缩包，以下是返回内容预览："
                      head -n 20 "$ARCHIVE_FILE" || true
                      exit 1
                    fi
                    tar -xf "$ARCHIVE_FILE" -C "$EXTRACT_DIR"
                    EXTRACTED_ROOT="$(find "$EXTRACT_DIR" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
                    test -n "$EXTRACTED_ROOT"
                    mkdir -p "$(dirname "$FINAL_HOME")"
                    mv "$EXTRACTED_ROOT" "$FINAL_HOME"
                    """.formatted(
                    escapeShell(workspaceRoot),
                    escapeShell(preset.downloadUrl().substring(preset.downloadUrl().lastIndexOf('/') + 1)),
                    escapeShell(preset.homePath()),
                    escapeShell(preset.downloadUrl())
            );
            hostService.executeRemoteScript(host.getId(), script, 600);
        } catch (Exception ex) {
            log.warn("Remote preset install failed for {} on {}: {}", preset.name(), host.getName(), ex.getMessage(), ex);
            throw new BusinessException(ErrorSubCode.PRESET_REMOTE_INSTALL_FAILED, ex.getMessage(), ex);
        }

        RuntimeEnvironmentRequest saveRequest = new RuntimeEnvironmentRequest(
                preset.name(),
                preset.type(),
                host.getId(),
                preset.version(),
                preset.homePath(),
                preset.binPath(),
                null,
                jsonMapper.write(Map.of()),
                true
        );
        RuntimeEnvironmentEntity entity = save(saveRequest, null);
        log.info("Preset {} registered for remote host {}", preset.name(), host.getName());
        return entity;
    }

    private RuntimeEnvironmentDetection detectJava() {
        return detectFromCommand(
                RuntimeEnvironmentType.JAVA,
                "Java",
                List.of("java", "-version"),
                System.getenv("JAVA_HOME"),
                home -> home == null ? null : Path.of(home).resolve("bin").toString()
        );
    }

    private RuntimeEnvironmentDetection detectNode() {
        return detectFromCommand(
                RuntimeEnvironmentType.NODE,
                "Node",
                List.of("node", "-v"),
                System.getenv("NODE_HOME"),
                home -> home == null ? null : Path.of(home).resolve("bin").toString()
        );
    }

    private RuntimeEnvironmentDetection detectMaven() {
        return detectFromCommand(
                RuntimeEnvironmentType.MAVEN,
                "Maven",
                List.of("mvn", "-version"),
                System.getenv("MAVEN_HOME"),
                home -> home == null ? null : Path.of(home).resolve("bin").toString()
        );
    }

    private RuntimeEnvironmentDetection detectFromCommand(
            RuntimeEnvironmentType type,
            String name,
            List<String> command,
            String homePath,
            java.util.function.Function<String, String> binPathResolver
    ) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().findFirst().orElse("");
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }
            if (!isValidDetectedVersion(output)) {
                return null;
            }
            return new RuntimeEnvironmentDetection(
                    name + "（自动检测）",
                    type,
                    output.trim(),
                    homePath,
                    binPathResolver.apply(homePath)
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private RuntimeEnvironmentDetection detectRemoteVersion(
            HostEntity host,
            RuntimeEnvironmentType type,
            String name,
            String versionCommand,
            String homeCommand,
            String binCommand
    ) {
        try {
            String output = hostService.executeRemoteScript(
                    host.getId(),
                    "set +e\n" +
                            "VERSION=$(" + versionCommand + ")\n" +
                            "HOME_PATH=$(" + homeCommand + ")\n" +
                            "BIN_PATH=$(" + binCommand + ")\n" +
                            "printf '__DEPLOYBOT_VERSION__%s\\n' \"$VERSION\"\n" +
                            "printf '__DEPLOYBOT_HOME__%s\\n' \"$HOME_PATH\"\n" +
                            "printf '__DEPLOYBOT_BIN__%s\\n' \"$BIN_PATH\"\n",
                    8
            );
            String version = extractValue(output, "__DEPLOYBOT_VERSION__");
            if (!isValidDetectedVersion(version)) {
                return null;
            }
            String homePath = normalizeBlank(extractValue(output, "__DEPLOYBOT_HOME__"));
            String binPath = normalizeBlank(extractValue(output, "__DEPLOYBOT_BIN__"));
            return new RuntimeEnvironmentDetection(
                    name + "（自动检测）",
                    type,
                    version.trim(),
                    homePath,
                    binPath
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractValue(String output, String prefix) {
        return output.lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .findFirst()
                .orElse(null);
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isValidDetectedVersion(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        String normalized = version.trim().toLowerCase(Locale.ROOT);
        return !normalized.contains("command not found")
                && !normalized.contains("not recognized")
                && !normalized.contains("no such file or directory")
                && !normalized.contains("cannot find");
    }

    private Path installRoot(HostEntity host) {
        if (host != null && host.getWorkspaceRoot() != null && !host.getWorkspaceRoot().isBlank()) {
            return Path.of(host.getWorkspaceRoot().trim());
        }
        String workspaceRoot = systemSettingsService.get().getWorkspaceRoot();
        return Path.of(workspaceRoot == null || workspaceRoot.isBlank() ? "./runtime" : workspaceRoot.trim());
    }

    private void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.compareTo(left))
                    .forEach(item -> {
                        try {
                            Files.deleteIfExists(item);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
    }

    private void validateArchive(Path archiveFile) {
        try {
            Process process = new ProcessBuilder("tar", "-tf", archiveFile.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new BusinessException(
                        ErrorSubCode.PRESET_ARCHIVE_INVALID,
                        "返回内容预览：\n" + previewDownloadedFile(archiveFile)
                );
            }
        } catch (IOException | InterruptedException ex) {
            throw new BusinessException(ErrorSubCode.PRESET_ARCHIVE_VALIDATE_FAILED, ex.getMessage(), ex);
        }
    }

    private String previewDownloadedFile(Path archiveFile) {
        try (BufferedReader reader = Files.newBufferedReader(archiveFile, StandardCharsets.UTF_8)) {
            return reader.lines().limit(20).reduce("", (left, right) -> left + right + "\n");
        } catch (IOException ex) {
            return "无法读取下载内容预览：" + ex.getMessage();
        }
    }

    private String detectOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return "mac";
        }
        return "linux";
    }

    private String detectArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch") || arch.contains("arm64")) {
            return "aarch64";
        }
        return "x64";
    }

    private PlatformInfo detectPlatform(HostEntity host) {
        if (host == null || host.getType() == HostType.LOCAL) {
            return new PlatformInfo(detectOs(), detectArch());
        }
        try {
            String output = hostService.executeRemoteScript(
                    host.getId(),
                    "printf '__DEPLOYBOT_OS__%s\\n' \"$(uname -s)\"\n" +
                            "printf '__DEPLOYBOT_ARCH__%s\\n' \"$(uname -m)\"\n",
                    10
            );
            String os = normalizeRemoteOs(extractValue(output, "__DEPLOYBOT_OS__"));
            String arch = normalizeRemoteArch(extractValue(output, "__DEPLOYBOT_ARCH__"));
            return new PlatformInfo(os, arch);
        } catch (Exception ex) {
            return new PlatformInfo("linux", "x64");
        }
    }

    private String normalizeRemoteOs(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("darwin") || normalized.contains("mac")) {
            return "mac";
        }
        return "linux";
    }

    private String normalizeRemoteArch(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("aarch64") || normalized.contains("arm64")) {
            return "aarch64";
        }
        return "x64";
    }

    private String escapeShell(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 从 JSON 资源文件读取预置列表，后续新增版本时无需再修改 Java 代码。
     */
    private List<PresetDefinition> loadPresetDefinitions() {
        try {
            Resource resource = resourceLoader.getResource(presetsResourceLocation);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return jsonMapper.read(content, new TypeReference<List<PresetDefinition>>() {
            });
        } catch (IOException ex) {
            throw new BusinessException(ErrorSubCode.PRESET_CONFIG_READ_FAILED, ex.getMessage(), ex);
        }
    }

    /**
     * 构建模板变量，用于把预置 JSON 中的平台占位符渲染成最终下载地址和安装目录。
     */
    private Map<String, String> buildPresetTemplateVariables(PlatformInfo platform, Path installRoot) {
        Map<String, String> variables = new HashMap<>();
        variables.put("os", platform.os());
        variables.put("arch", platform.arch());
        variables.put("installRoot", installRoot.toString());
        variables.put("nodePlatform", "linux".equals(platform.os()) ? "linux" : "darwin");
        variables.put("nodeArch", "aarch64".equals(platform.arch()) ? "arm64" : "x64");
        variables.put("nodeArchiveExtension", "linux".equals(platform.os()) ? "tar.xz" : "tar.gz");
        return variables;
    }

    private String applyPresetTemplate(String template, Map<String, String> variables) {
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

    private record PlatformInfo(String os, String arch) {
    }

    /**
     * 运行环境预置定义。
     * 这一层只保存模板，不直接面向前端返回。
     */
    private record PresetDefinition(
            String id,
            String name,
            RuntimeEnvironmentType type,
            String version,
            String description,
            String downloadUrlTemplate,
            String homePathTemplate,
            String binPathTemplate
    ) {
    }
}
