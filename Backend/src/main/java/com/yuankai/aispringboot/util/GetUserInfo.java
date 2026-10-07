package com.yuankai.aispringboot.util;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.yuankai.aispringboot.config.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从当前请求中获取登录用户信息。
 *
 * JwtAuthenticationFilter 验签通过后，把结果缓存到请求属性
 * {@link JwtAuthenticationFilter#JWT_USER_ATTR}，直接读取 —— 一次请求只验一次签。
 * 请求属性随请求销毁，不存在 ThreadLocal 泄漏问题（Tomcat 线程复用串号风险）。
 *
 * 非 Web 环境拿不到请求上下文时，走一次验签，行为保持不变。
 */
public class GetUserInfo {

    // 获取用户ID
    public static Long getUserId() {
        return getCurrentUser().getUserId();
    }

    // 获取用户名（token中的claim名为username）
    public static String getUserName() {
        return getCurrentUser().getUsername();
    }

    // 获取用户角色类型（token中的claim名为roleType，与JwtTokenUtil.generateToken保持一致）
    public static Integer getUserType() {
        return getCurrentUser().getRoleType();
    }

    /**
     * 一次性取回 userId / username / roleType。
     * 一次请求里同时需要多个字段时推荐用它，取值逻辑只走一遍。
     */
    public static JwtTokenUtil.TokenVerificationResult getCurrentUser() {
        // 1、优先读过滤器已验签的缓存结果（Web 请求场景，零验签开销）
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            Object cached = request.getAttribute(JwtAuthenticationFilter.JWT_USER_ATTR);
            if (cached instanceof JwtTokenUtil.TokenVerificationResult result) {
                return result;
            }
        }

        // 2、兜底：没有请求上下文（单元测试等）时才真正验签
        JwtTokenUtil.TokenVerificationResult result =
                JwtTokenUtil.validateToken(JwtTokenUtil.getCurrentToken());
        if (result == null) {
            throw new JWTVerificationException("Token 无效或已过期");
        }
        return result;
    }

    /**
     * 空安全取值：未登录（没有 token）或 token 无效/过期时返回 null，不抛异常。
     *
     * 使用场景：可以匿名访问、但登录后需要区分身份的接口。
     * 注意公开路径（SecurityConfig.PUBLIC_PATH）会被 JwtAuthenticationFilter.shouldNotFilter 整体跳过，
     * 请求属性里没有验签缓存，此时会走上面的兜底验签：
     * 携带合法 token 的登录用户仍能取到真实身份，完全匿名的请求则返回 null。
     */
    public static JwtTokenUtil.TokenVerificationResult getCurrentUserOrNull() {
        try {
            return getCurrentUser();
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    // 获取用户ID，未登录时返回 null
    public static Long getUserIdOrNull() {
        JwtTokenUtil.TokenVerificationResult result = getCurrentUserOrNull();
        return result == null ? null : result.getUserId();
    }

    // 获取用户角色类型，未登录时返回 null
    public static Integer getUserTypeOrNull() {
        JwtTokenUtil.TokenVerificationResult result = getCurrentUserOrNull();
        return result == null ? null : result.getRoleType();
    }
}
