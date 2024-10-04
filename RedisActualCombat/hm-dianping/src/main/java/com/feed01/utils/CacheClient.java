package com.feed01.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中
     * 并且可以设置TTL过期时间
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中
     * 并且可以设置逻辑过期时间,用于处理缓存击穿问题
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // 逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     *  根据指定的key查询缓存,并反序列化为指定类型,利用缓存空值的方式解决缓存穿透问题
     * @param keyPrefix rediskey 前缀
     * @param id
     * @param type 传了才能得知R是什么
     * @param dbFallback 查询函数
     * @param time TTL
     * @param unit TTL
     * @return
     * @param <R> 可能用于任一类型所以用泛型，跟返回一起
     * @param <ID> ID不一定是Long 或 int 所以用泛型ID
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix , ID id , Class<R> type , Function<ID ,R> dbFallback ,Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // redis 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 判断redis 是否存在
        if (StrUtil.isNotBlank(json)) {
            // 存在，直接返回信息
            return JSONUtil.toBean(json, type);
//          原本: return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中的缓存是否空值
        if (json != null) {
            return null;
        }

        // 不存在，查数据库
        R r = dbFallback.apply(id);
//      原本: Shop shop = getById(id);
        if (r == null) {
            // 缓存穿透用 ，不存在写入空值 避免访问数据库
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

            // 不存在，返回错误
            return null;
        }
        // 存在，存redis，返回信息
        this.set(key ,r ,time ,unit);
//      原本: stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return r;
    }


    /**
     * 根据指定的key查询缓存,并反序列化为指定类型,需要利用逻辑过期解决缓存击穿问题
     */
    // 线程池
    private static final ExecutorService CACHE_EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);
    public <R ,ID> R queryWithLogicExpire(
            String keyPrefix ,ID id ,Class<R> type ,String lockKeyPrefix ,Function<ID ,R> dbFallback ,Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // redis 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 判断redis 是否存在
        if(StrUtil.isBlank(json)){
            // 未命中
            return null;
        }

        // 命中，json反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject jsonObject = (JSONObject)redisData.getData();
        R r = JSONUtil.toBean(jsonObject, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判端是否过期
        // 未过期，返回店铺信息
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }

        // 过期，缓存重建
        String lockKey = lockKeyPrefix + id;
        // 获取互斥锁
        boolean isLock = trylock(lockKey);
        //  判断是否获取成功
        if(isLock){
            // 成功，开启新线程 缓存重建
            CACHE_EXECUTOR_SERVICE.submit(()->{
                try {
                    // 缓存重建，查DB 存redis
                    // 查DB
                    R r1 = dbFallback.apply(id);
                    // 存redis 逻辑过期时间
                    setWithLogicExpire(key, r1, time,  unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 失败，返回过期的店铺信息
        return r;
    }

    private boolean trylock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    // 释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
