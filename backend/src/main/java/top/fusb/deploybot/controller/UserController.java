package top.fusb.deploybot.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fusb.deploybot.dto.UserRequest;
import top.fusb.deploybot.model.UserEntity;
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

    @PostMapping
    public UserEntity create(@Valid @RequestBody UserRequest request) {
        return userService.save(request, null);
    }

    @PutMapping("/{id}")
    public UserEntity update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return userService.save(request, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
