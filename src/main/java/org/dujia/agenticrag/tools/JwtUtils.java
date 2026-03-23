package org.dujia.agenticrag.tools;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.commons.Common;
import org.dujia.agenticrag.enums.ErrorCode;
import org.dujia.agenticrag.exceptions.BaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtils {

    private static String SECRET_STRING;
    private static Long EXPIRATION_TIME;
    private static SecretKey SECRET_KEY;

    @Value("${jwt.secret}")
    private String secretString;
    @Value("${jwt.expire}")
    private Long expirationTime;

    @PostConstruct
    private void init() {
        SECRET_STRING = this.secretString;
        EXPIRATION_TIME = this.expirationTime;
        SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token
     *
     * @param userId 用户的唯一标识
     * @return 生成的 token 字符串
     */
    public static String generateToken(Long userId) {
        Date now = new Date();
        Date expirationTime = new Date(now.getTime() + EXPIRATION_TIME);
        return Jwts.builder()
                .claim(Common.TOKEN, userId)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .setExpiration(expirationTime)
                .setIssuedAt(now)
                .compact();
    }

    /**
     * 解析 Token 并返回完整的 Claims (载荷信息)
     */
    public static Claims parseTokenToClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            log.error("Token解析失败: {}", e.getMessage());
            throw new BaseException(ErrorCode.TOKEN_ERROR);
        }
    }
}
