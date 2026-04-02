package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.dto.TemplateRequest;
import top.fusb.deploybot.model.TemplateEntity;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.service.TemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AdminOnly
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService service;

    public TemplateController(TemplateService service) {
        this.service = service;
    }

    /**
     * 列出全部模板。
     */
    @GetMapping
    public List<TemplateEntity> list() {
        return service.findAll();
    }

    @GetMapping("/page")
    public PageResult<TemplateEntity> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) Boolean monitorProcess
    ) {
        return service.findPage(page, pageSize, keyword, templateType, monitorProcess);
    }

    /**
     * 新建模板。
     */
    @PostMapping
    public TemplateEntity create(@Valid @RequestBody TemplateRequest request) {
        return service.save(request, null);
    }

    /**
     * 更新模板。
     */
    @PutMapping("/{id}")
    public TemplateEntity update(@PathVariable Long id, @Valid @RequestBody TemplateRequest request) {
        return service.save(request, id);
    }

    /**
     * 删除模板。
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
