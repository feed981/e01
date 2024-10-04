package com.feed01.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.feed01.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     *  进到controller 之前登入效验
     *  redis
     */
    // TODO: 拦截所有路径，因为原本的拦截器只拦截登陆时的路径，如果用户登陆后都访问不需要登陆的路径，原拦截器就无法刷新token 存活时间
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        // 基于token 获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        // entries() 原本就会判断 空的话返回null
        // 判断用户是否存在
        if(userMap.isEmpty()){
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 将查询的Hash 转UserDTO
        // 存在，保存用户信息到 ThreadLocal
        UserHolder.saveUser(userDTO);

        // 更新 token 存活时间
        stringRedisTemplate.expire(tokenKey ,RedisConstants.CACHE_SHOP_TTL , TimeUnit.MINUTES);

//        log.info("preHandle user={}",UserHolder.getUser());
        // 放行

        return true;
    }

    //用户业务执行完毕，销毁用户信息避免内存泄漏
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
