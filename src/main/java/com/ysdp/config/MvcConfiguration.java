package com.ysdp.config;

import com.ysdp.utils.LoginInterceptor;
import com.ysdp.utils.RefulshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfiguration implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);//调整拦截器的执行顺序
        //token刷新拦截器
        registry.addInterceptor(new RefulshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);//调整拦截器的执行顺序
    }
}
