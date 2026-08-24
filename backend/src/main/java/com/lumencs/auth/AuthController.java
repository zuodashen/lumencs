package com.lumencs.auth;

import com.lumencs.common.ApiResponse;
import com.lumencs.common.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public R<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.getUsername(), request.getPassword()));
    }

    /** Refresh Token 换新：access 过期后前端用 refresh 静默续期。 */
    @PostMapping("/refresh")
    public R<Map<String, String>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.getRefreshToken()));
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank
        private String refreshToken;
    }
}
