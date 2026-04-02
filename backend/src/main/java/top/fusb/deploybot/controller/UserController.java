package top.fusb.deploybot.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.fusb.deploybot.dto.AvatarUploadResponse;
import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.dto.UserRequest;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.model.UserRole;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.service.UserService;

import java.util.List;

/**
 * 用户管理接口。
 */
@AdminOnly
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserEntity> list() {
        return userService.findAll();
    }

    @GetMapping("/page")
    public PageResult<UserEntity> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean enabled
    ) {
        return userService.findPage(page, pageSize, keyword, role, enabled);
    }

    @PostMapping
    public UserEntity create(@Valid @RequestBody UserRequest request) {
        return userService.save(request, null);
    }

    @PutMapping("/{id}")
    public UserEntity update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return userService.save(request, id);
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
    }

    @PostMapping("/avatar")
    public AvatarUploadResponse uploadAvatar(@RequestParam("file") MultipartFile file) {
        return new AvatarUploadResponse(userService.uploadAvatar(file));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
