package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.ProjectRequest;
import top.fusb.deploybot.model.ProjectEntity;
import top.fusb.deploybot.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    /**
     * 列出全部项目。
     */
    @GetMapping
    public List<ProjectEntity> list() {
        return service.findAll();
    }

    /**
     * 新建项目。
     */
    @PostMapping
    public ProjectEntity create(@Valid @RequestBody ProjectRequest request) {
        return service.save(request, null);
    }

    /**
     * 更新项目配置。
     */
    @PutMapping("/{id}")
    public ProjectEntity update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return service.save(request, id);
    }

    /**
     * 删除项目。
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
