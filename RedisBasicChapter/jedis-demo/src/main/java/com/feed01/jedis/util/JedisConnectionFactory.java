package com.feed01.jedis.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * JedisPool:
 * jedis本身是线程不安全的,
 * 并且频繁的创建和销毁连接会有性能损耗,
 * 因此我们推荐大家使用Jedis连接池代替Jedis的直连方式。
 */
public class JedisConnectionFactory {
    private static final JedisPool jedisPool;

    static {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        // 最大连接数
        jedisPoolConfig.setMaxTotal(8);
        // 最大空闲连接
        jedisPoolConfig.setMaxIdle(8);
        // 最小空闲连接
        jedisPoolConfig.setMinIdle(0);
        // 连线等待时间
        jedisPoolConfig.setMaxWaitMillis(1000);

        jedisPool = new JedisPool(jedisPoolConfig ,"192.168.33.10" ,6379 ,1000 ,"qwe123");
    }

    public static Jedis getJedis(){
        return jedisPool.getResource();
    }
}
