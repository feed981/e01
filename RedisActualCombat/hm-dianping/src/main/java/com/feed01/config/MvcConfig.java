package com.feed01.config;

import com.feed01.utils.LoginInterceptor;
import com.feed01.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截顺序:1. 加order() 或是 2.照顺序写 也能达到相同效果
        // 拦所有请求 默认就是 .addPathPatterns("/**") 不加也没差
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);

        registry.addInterceptor(new LoginInterceptor())
                // 排除拦截，也就是可放行的接口
                .excludePathPatterns(
                        "/blog/hot", // 热点的博客
                        "/voucher/**", // 优惠券
                        "/shop/**", //店铺
                        "/shop-type/**", //店铺类型
                        "/upload/**", //上传通常是登入才能，但目前测试需要先放行
                        "/user/code",
                        "/user/login"
                        ,"/user/login-createTestuser" // 个人 新增用户压测用
                        ,"/blog/upload/old/of/follow" // 个人 推送关注
                ).order(1);
    }
}
