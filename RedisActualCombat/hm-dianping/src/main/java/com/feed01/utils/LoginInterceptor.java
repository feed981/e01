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
public class LoginInterceptor implements HandlerInterceptor {

//    private StringRedisTemplate stringRedisTemplate;

    // 构造器注入
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    /**
     * 方式1
     * 进到controller 之前登入效验
     * session
     */
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 获取 session
//        HttpSession session = request.getSession();
//        // 获取session 中的用户
//        Object user = session.getAttribute("user");
//        // 判断用户是否存在
//        if(user == null){
//            // 不存在，拦截，返回401
//            response.setStatus(401);
//            return false;
//        }
//
//        // 存在，保存用户信息到 ThreadLocal
//        UserHolder.saveUser((UserDTO) user);
//
//        log.info("preHandle user={}",UserHolder.getUser());
//        // 放行
//
//        return true;
//    }

    /**
     * 方式2
     * 进到controller 之前登入效验
     * redis
     */
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 获取请求头中的token
//        String token = request.getHeader("authorization");
//        if(StrUtil.isBlank(token)){
//            // 不存在，拦截，返回401
//            response.setStatus(401);
//            return false;
//        }
//        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
//        // 基于token 获取redis中的用户
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
//        // entries() 原本就会判断 空的话返回null
//        // 判断用户是否存在
//        if(userMap.isEmpty()){
//            // 不存在，拦截，返回401
//            response.setStatus(401);
//            return false;
//        }
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        // 将查询的Hash 转UserDTO
//        // 存在，保存用户信息到 ThreadLocal
//        UserHolder.saveUser(userDTO);
//
//        // 更新 token 存活时间
//        stringRedisTemplate.expire(tokenKey ,RedisConstants.CACHE_SHOP_TTL , TimeUnit.MINUTES);
//
////        log.info("preHandle user={}",UserHolder.getUser());
//        // 放行
//
//        return true;
//    }

    /**
     *   方式2 优化
     *   增加RefreshTokenInterceptor 当做第一个拦截器会去拦截所有路径
     *   因为原本的拦截器 LoginInterceptor 只拦截登陆时的路径
     *   如果用户登陆后都访问不需要登陆的路径，就不会触发这个拦截器导致无法刷新token 存活时间
     *   token 挂了时后用户再去访问需要登录的路径就会异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(UserHolder.getUser() == null){
            // 不存在，拦截，返回401
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
