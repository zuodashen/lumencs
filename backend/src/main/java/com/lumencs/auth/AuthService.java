package com.lumencs.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumencs.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AdminUserMapper adminUserMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Map<String, String> login(String username, String password) {
        AdminUser user = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return tokenPair(username);
    }

    /** 用 refresh token 换取新的 access + refresh（双 token 轮换）。 */
    public Map<String, String> refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("缺少 refreshToken");
        }
        if (!jwtService.isType(refreshToken, JwtService.TYPE_REFRESH)) {
            throw new IllegalArgumentException("refreshToken 无效或已过期");
        }
        return tokenPair(jwtService.parseUsername(refreshToken));
    }

    private Map<String, String> tokenPair(String username) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("token", jwtService.issueAccess(username));
        result.put("refreshToken", jwtService.issueRefresh(username));
        result.put("username", username);
        return result;
    }
}
