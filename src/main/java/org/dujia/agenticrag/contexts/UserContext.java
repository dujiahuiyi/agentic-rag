package org.dujia.agenticrag.contexts;

public class UserContext {
    private static final ThreadLocal<Long> USER_ID_THREAD_LOCAL = new ThreadLocal<>();

    public static Long get() {
        return USER_ID_THREAD_LOCAL.get();
    }

    public static void set(Long userId) {
        USER_ID_THREAD_LOCAL.set(userId);
    }

    public static void remove() {
        USER_ID_THREAD_LOCAL.remove();
    }
}
