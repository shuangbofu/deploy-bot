package top.fusb.deploybot.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fusb.deploybot.dto.LoginRequest;
import top.fusb.deploybot.dto.LoginResponse;
import top.fusb.deploybot.dto.UserProfile;
import top.fusb.deploybot.dto.ChangePasswordRequest;
import top.fusb.deploybot.service.AuthService;

/**
 * 登录鉴权接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserProfile me() {
        return authService.currentUserProfile();
    }

    @PostMapping("/logout")
    public void logout() {
        authService.logout();
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
    }
}
