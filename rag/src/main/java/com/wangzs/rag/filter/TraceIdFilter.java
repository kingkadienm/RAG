package com.wangzs.rag.filter;

import com.wangzs.rag.util.IdUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 请求级 TraceId 过滤器
 * - 入口生成或透传 traceId（X-Request-ID / X-Trace-Id）
 * - 写入 MDC，方便日志链路追踪
 * - 响应头返回 traceId
 */
@Slf4j
@Component
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String traceId = resolveTraceId(httpRequest);
        try {
            org.slf4j.MDC.put(TRACE_ID_KEY, traceId);
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            chain.doFilter(request, response);
        } finally {
            org.slf4j.MDC.remove(TRACE_ID_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        // 优先透传上游 traceId
        String incoming = request.getHeader(TRACE_ID_HEADER);
        if (StringUtils.hasText(incoming)) {
            return incoming;
        }
        incoming = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(incoming)) {
            return incoming;
        }
        // 生成新的 traceId
        return IdUtil.generateTraceId();
    }
}
