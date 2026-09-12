package com.wangzs.rag.controller.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.common.util.AuthUtil;
import com.wangzs.rag.model.dto.LoginRequest;
import com.wangzs.rag.model.dto.RegisterRequest;
import com.wangzs.rag.model.entity.User;
import com.wangzs.rag.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证 Controller（登录/注册）
 */
@Tag(name = "用户认证", description = "登录、注册、登出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Map<String, Object> data = authService.login(request);
        return ApiResult.success(data, "登录成功");
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResult<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResult.success(null, "注册成功");
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        StpUtil.logout();
        return ApiResult.success(null, "登出成功");
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me() {
        Long userId = AuthUtil.getLoginUserId();
        User user = authService.getCurrentUser(userId);
        Map<String, Object> data = Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole()
        );
        return ApiResult.success(data);
    }
}
