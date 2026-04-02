package org.dujia.agenticrag.commons;

public class Common {
    //rabbitMQ
    public static final String UPLOAD_ROUTING_KEY = "kb.parse.routing";
    public static final String UPLOAD_EXCHANGE = "kb.exchange";
    public static final String UPLOAD_QUEUE = "kb.queue";
    // token
    public static final String TOKEN = "userId";
    public static final String NEW_TOKEN = "new-token";
    // 异常
    public static final String USER_ALREADY_EXISTS = "该用户已存在";
    public static final String TOKEN_ERROR = "token异常，请查看日志";
    public static final String UNAUTHORIZED = "未认证，请先登录";
    public static final String THE_FILE_CANNOT_BE_EMPTY = "文件不能为空";
    public static final String DOCUMENT_NOT_FOUND = "文档不存在";
    public static final String FAIL_TO_REGISTER = "注册失败";
    public static final String USER_NOT_FOUND = "用户不存在";
    public static final String INVALID_USERNAME_OR_PASSWORD = "用户名或密码错误";
    public static final String INVALID_SESSION_ID = "sessionId不合法";
    public static final String UNAUTHORIZED_ACCESS = "无权访问该会话";
}
