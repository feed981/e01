package com.feed01.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    
    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.33.10:6379").setPassword("qwe123");
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient2(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.33.10:6380").setPassword("qwe123");
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient3(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.33.10:6381").setPassword("qwe123");
        return Redisson.create(config);
    }
}
