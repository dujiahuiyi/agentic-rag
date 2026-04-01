package org.dujia.agenticrag.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordUtils {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    private PasswordUtils() {
    }

    /**
     * 生成密文 (注册、修改密码时使用)
     *
     * @param rawPassword 用户输入的明文密码
     * @return 数据库要保存的 60 位密文
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return ENCODER.encode(rawPassword);
    }

    /**
     * 密码比对 (登录时使用)
     *
     * @param rawPassword     用户登录时输入的明文密码
     * @param encodedPassword 数据库中查出来的密文密码
     * @return true: 密码正确, false: 密码错误
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
