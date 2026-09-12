package com.wangzs.rag.service;

import com.wangzs.rag.model.dto.LoginRequest;
import com.wangzs.rag.model.dto.RegisterRequest;
import com.wangzs.rag.model.entity.User;

import java.util.Map;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录，返回 token 和用户信息
     */
    Map<String, Object> login(LoginRequest request);

    /**
     * 用户注册
     */
    User register(RegisterRequest request);

    /**
     * 获取当前用户信息
     */
    User getCurrentUser(Long userId);
}
