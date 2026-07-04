package com.practice.mailsystem.auth;

import com.practice.mailsystem.common.exception.BusinessException;

public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 获取当前登录用户 ID，未登录时抛出 401 异常。
     */
    public static Long requireUserId() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(401, "未登录");
        }
        return user.userId();
    }

    /**
     * 获取当前登录用户 ID，未登录时返回 null（用于可选认证场景）。
     */
    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
