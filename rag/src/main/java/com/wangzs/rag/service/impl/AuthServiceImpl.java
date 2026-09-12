package com.wangzs.rag.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.mapper.UserMapper;
import com.wangzs.rag.model.dto.LoginRequest;
import com.wangzs.rag.model.dto.RegisterRequest;
import com.wangzs.rag.model.entity.User;
import com.wangzs.rag.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public Map<String, Object> login(LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null || user.getDeleted() == 1) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getStatus() == 2) {
            throw BizException.of(ErrorCode.USER_DISABLED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw BizException.of(ErrorCode.USER_PASSWORD_ERROR);
        }

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(RegisterRequest request) {
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
                        .eq(User::getDeleted, 0)
        );
        if (exist != null) {
            throw BizException.of(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setRole(2); // 普通用户
        user.setStatus(1);
        user.setDeleted(0);

        userMapper.insert(user);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    @Override
    public User getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
