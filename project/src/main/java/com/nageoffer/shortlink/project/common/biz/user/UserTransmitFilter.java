package com.nageoffer.shortlink.project.common.biz.user;

import com.alibaba.fastjson2.JSON;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.Objects;

/**
 * 用户信息传输过滤器
 *
 * @公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {
    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String requestURI = httpServletRequest.getRequestURI();
        if(!Objects.equals(requestURI, "/api/short-link/admin/v1/user/login")){
            String username = httpServletRequest.getHeader("username");//前端传来的是username和token redis中是username为键 token键的哈希结构中的键 根据这两个才能查出用户信息
            String token = httpServletRequest.getHeader("token");
            Object userInfoJsonStr = stringRedisTemplate.opsForHash().get("short-link:login:" + username, token);//返回的是string类型的json
            if(userInfoJsonStr != null) {
                UserInfoDTO userInfoDTO= JSON.parseObject(userInfoJsonStr.toString(), UserInfoDTO.class);
                UserContext.setUser(userInfoDTO);
            }
        }


        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            UserContext.removeUser();
        }
    }
}