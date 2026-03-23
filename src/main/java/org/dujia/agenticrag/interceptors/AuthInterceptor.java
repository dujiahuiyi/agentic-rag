package org.dujia.agenticrag.interceptors;

import cn.hutool.core.util.StrUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.commons.Common;
import org.dujia.agenticrag.contexts.UserContext;
import org.dujia.agenticrag.enums.ErrorCode;
import org.dujia.agenticrag.exceptions.BaseException;
import org.dujia.agenticrag.tools.JwtUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Date;

@Slf4j
@Configuration
public class AuthInterceptor implements HandlerInterceptor {

    // 自动刷新时间
    private static final long REFRESH_THRESHOLD_MS = 30 * 60 * 1000;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.remove();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (StrUtil.isBlank(token)) {
            log.warn("token为空");
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }

        try {
            Claims claims = JwtUtils.parseTokenToClaims(token);
            Long userId = claims.get(Common.TOKEN, Long.class);

            Date expiration = claims.getExpiration();
            long remainingTime = expiration.getTime() - System.currentTimeMillis();

            if (remainingTime > 0 && remainingTime < REFRESH_THRESHOLD_MS) {
                String newToken = JwtUtils.generateToken(userId);
                response.setHeader(Common.NEW_TOKEN, newToken);
                response.setHeader("Access-Control-Expose-Headers", Common.NEW_TOKEN);
                log.info("用户[{}]的Token即将过期，已自动颁发新Token", userId);
            }

            UserContext.set(userId);
            return true;
        } catch (Exception e) {
            log.warn("token异常");
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }
}
