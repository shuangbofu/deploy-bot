package top.fusb.deploybot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fusb.deploybot.dto.ApiTokenCreateRequest;
import top.fusb.deploybot.dto.ApiTokenCreateResult;
import top.fusb.deploybot.dto.ApiTokenSummary;
import top.fusb.deploybot.dto.ApiTokenUpdateRequest;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.service.ApiTokenService;

import java.util.List;

/**
 * API Token 管理接口。
 */
@AdminOnly
@RestController
@RequestMapping("/api/api-tokens")
@RequiredArgsConstructor
public class ApiTokenController {
    private final ApiTokenService apiTokenService;

    @GetMapping
    public List<ApiTokenSummary> list() {
        return apiTokenService.findAll();
    }

    @PostMapping
    public ApiTokenCreateResult create(@Valid @RequestBody ApiTokenCreateRequest request) {
        return apiTokenService.create(request);
    }

    @PutMapping("/{id}")
    public ApiTokenSummary update(@PathVariable Long id, @RequestBody ApiTokenUpdateRequest request) {
        return apiTokenService.update(id, request);
    }

    @PostMapping("/{id}/revoke")
    public void revoke(@PathVariable Long id) {
        apiTokenService.revoke(id);
    }
}
