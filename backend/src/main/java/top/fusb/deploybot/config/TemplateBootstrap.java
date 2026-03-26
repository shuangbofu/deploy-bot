package top.fusb.deploybot.config;

import top.fusb.deploybot.dto.TemplateRequest;
import top.fusb.deploybot.repo.TemplateRepository;
import top.fusb.deploybot.service.JsonMapper;
import top.fusb.deploybot.service.TemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class TemplateBootstrap {

    private final TemplateRepository templateRepository;
    private final TemplateService templateService;
    private final JsonMapper jsonMapper;
    private final ResourceLoader resourceLoader;
    private final String templatesResourceLocation;

    public TemplateBootstrap(
            TemplateRepository templateRepository,
            TemplateService templateService,
            JsonMapper jsonMapper,
            ResourceLoader resourceLoader,
            @Value("${deploybot.default-templates:classpath:default-templates.json}") String templatesResourceLocation
    ) {
        this.templateRepository = templateRepository;
        this.templateService = templateService;
        this.jsonMapper = jsonMapper;
        this.resourceLoader = resourceLoader;
        this.templatesResourceLocation = templatesResourceLocation;
    }

    /**
     * 空库启动时自动导入一组可直接使用的默认模板。
     * 已有模板数据时不做覆盖，避免影响用户现有配置。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        if (templateRepository.count() > 0) {
            return;
        }

        loadTemplateRequests().forEach(request -> templateService.save(request, null));
    }

    private List<TemplateRequest> loadTemplateRequests() {
        try {
            Resource resource = resourceLoader.getResource(templatesResourceLocation);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return jsonMapper.read(content, new TypeReference<List<TemplateRequest>>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("读取默认模板配置失败：" + ex.getMessage(), ex);
        }
    }
}
