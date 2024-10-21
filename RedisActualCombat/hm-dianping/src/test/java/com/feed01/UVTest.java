package com.feed01;


import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Properties;

@SpringBootTest
public class UVTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void hyperLog(){
        String [] values = new String[1000];
        int j = 0;
        for (int i = 0 ;i < 1000000 ;i++){
            // 每1000条发送一次
            j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999){
                stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
            }
        }
        // 统计数量
        Long hl2 = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(hl2);
    }

    void used_memory(){
        Properties info = stringRedisTemplate.getRequiredConnectionFactory().getConnection().info("memory");
        System.out.println("Used Memory: " + info.getProperty("used_memory"));
    }
}
