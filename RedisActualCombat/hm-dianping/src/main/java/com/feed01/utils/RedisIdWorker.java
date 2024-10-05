package com.feed01.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1704067200L;
    // 序列号的位数
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     *
     * @param keyPrefix 业务前缀
     * @return
     */
    public long nextId(String keyPrefix){

//        符号位: 1bit,永远为0
//        时间戳: 31bit,以秒为单位,可以使用69年
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

//        序列号: 32bit,秒内的计数器,支持每秒产生2^32个不同ID
        // 获取当前日期 精确到天
        String yyyyMMdd = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 自增
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + yyyyMMdd);

        // 先左移再填充
        return timestamp << COUNT_BITS | increment;
    }
//    public static void main(String[] args) {
//        long l = 110 << 5; // 等于 110 * 2的五次方
//        System.out.println("l = " + l);
//        LocalDateTime localDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
//        long epochSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
//        System.out.println("epochSecond = " + epochSecond); // 1704067200

//        for(int i=0 ;i<31 ;i++){
//            System.out.print("0");
//        }
//    }
}
