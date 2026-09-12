package com.wangzs.rag.common.util;

import cn.dev33.satoken.stp.StpUtil;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;

/**
 * 认证工具类
 */
public class AuthUtil {

    private AuthUtil() {
    }

    /**
     * 获取当前登录用户 ID（统一入口，避免各 Controller 重复实现）
     */
    public static Long getLoginUserId() {
        if (!StpUtil.isLogin()) {
            throw BizException.of(ErrorCode.PARAM_ERROR);
        }
        Object loginId = StpUtil.getLoginId();
        return loginId instanceof Long ? (Long) loginId : Long.parseLong(loginId.toString());
    }
}
