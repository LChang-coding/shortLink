package com.nageoffer.shortlink.admin.common.biz.user;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.nageoffer.shortlink.admin.common.convention.exception.ClientException;
import com.nageoffer.shortlink.admin.common.convention.result.Results; // 假设 Results 在此路径，请根据实际情况导入
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

import static com.nageoffer.shortlink.admin.common.enums.UserErrorCodeEnum.USER_TOKEN_FAIL;

/**
 * 用户信息传输过滤器
 */
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {
    private final StringRedisTemplate stringRedisTemplate;
    private static final List<String> IGNORE_URI = Lists.newArrayList(
            "/api/short-link/admin/v1/user/login",
            "/api/short-link/admin/v1/actual/user/has-username",
            "/api/short-link/admin/v1/title"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String requestURI = httpServletRequest.getRequestURI();

        // 将逻辑包裹在 try-catch 中
        try {
            if (!IGNORE_URI.contains(requestURI)) {
                String method = httpServletRequest.getMethod();
                if (!(Objects.equals(requestURI, "/api/short-link/admin/v1/user") && Objects.equals(method, "POST"))) {
                    String username = httpServletRequest.getHeader("username");
                    String token = httpServletRequest.getHeader("token");

                    if (!StrUtil.isAllNotBlank(username, token)) {
                        throw new ClientException(USER_TOKEN_FAIL);
                    }

                    Object userInfoJsonStr = null;
                    try {
                        userInfoJsonStr = stringRedisTemplate.opsForHash().get("short-link:login:" + username, token);
                        if (userInfoJsonStr == null) {
                            throw new ClientException(USER_TOKEN_FAIL);
                        }
                    } catch (Exception e) {
                        throw new ClientException(USER_TOKEN_FAIL);
                    }

                    UserInfoDTO userInfoDTO = JSON.parseObject(userInfoJsonStr.toString(), UserInfoDTO.class);
                    UserContext.setUser(userInfoDTO);
                }
            }
            // 只有验证通过才放行
            filterChain.doFilter(servletRequest, servletResponse);

        } catch (ClientException e) {
            // 如果捕获到认证异常，手动响应错误信息，不再继续执行 filterChain
            returnJson((HttpServletResponse) servletResponse, e);
        } finally {
            UserContext.removeUser();
        }
    }

    /**
     * 手动将异常信息转换成 JSON 写入响应
     */
    private void returnJson(HttpServletResponse response, ClientException exception) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        try (PrintWriter writer = response.getWriter()) {
            // 这里使用了你的 Results 工具类来包装异常，确保返回格式与全局异常处理一致
            // 假设你的 Results.failure 支持传入 exception 或 errorCode
            Object errorResponse = Results.failure(exception);
            writer.print(JSON.toJSONString(errorResponse));
        } catch (IOException e) {
            // log error
        }
    }
}
