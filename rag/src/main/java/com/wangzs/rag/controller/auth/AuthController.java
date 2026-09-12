package com.wangzs.rag.controller.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.model.entity.User;
import com.wangzs.rag.mapper.UserMapper;
import com.wangzs.rag.util.PasswordUtil;
import com.wangzs.rag.model.dto.LoginRequest;
import com.wangzs.rag.model.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
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

    private final UserMapper userMapper;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null || user.getDeleted() == 1) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getStatus() == 2) {
            throw BizException.of(ErrorCode.USER_DISABLED);
        }

        if (!PasswordUtil.matches(request.getPassword(), user.getPassword())) {
            throw BizException.of(ErrorCode.USER_PASSWORD_ERROR);
        }

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        Map<String, Object> data = Map.of(
                "token", token,
                "userId", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole()
        );
        return ApiResult.success(data, "登录成功");
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResult<Void> register(@Valid @RequestBody RegisterRequest request) {
        User exist = userMapper.selectByUsername(request.getUsername());
        if (exist != null && exist.getDeleted() == 0) {
            throw BizException.of(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(PasswordUtil.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setRole(2); // 普通用户
        user.setStatus(1);
        user.setDeleted(0);

        userMapper.insert(user);
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
        Long userId = getUserId();
        User user = userMapper.selectById(userId);
        Map<String, Object> data = Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole()
        );
        return ApiResult.success(data);
    }

    private Long getUserId() {
        if (!StpUtil.isLogin()) {
            throw BizException.of(ErrorCode.USER_NOT_LOGIN);
        }
        Object loginId = StpUtil.getLoginId();
        return loginId instanceof Long ? (Long) loginId : Long.parseLong(loginId.toString());
    }
}
