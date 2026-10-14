package com.running.api.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        log.info("[REQ] {} {} | Params: {}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString() != null ? request.getQueryString() : "none");
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        long startTime = (long) request.getAttribute(START_TIME_ATTR);
        long duration = System.currentTimeMillis() - startTime;
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();

        if (ex != null) {
            log.error("[RESP] {} {} | Status: {} | Duration: {}ms | Error: {}",
                    method, uri, status, duration, ex.getMessage());
        } else if (status >= 500) {
            log.error("[RESP] {} {} | Status: {} | Duration: {}ms",
                    method, uri, status, duration);
        } else if (status >= 400) {
            log.warn("[RESP] {} {} | Status: {} | Duration: {}ms",
                    method, uri, status, duration);
        } else {
            log.info("[RESP] {} {} | Status: {} | Duration: {}ms",
                    method, uri, status, duration);
        }
    }
}
