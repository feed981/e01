# Jedis
https://github.com/redis/jedis

Jedis：适合小型项目或对性能要求不高的场景。

概述：Jedis 是一款经典的 Redis Java 客户端，提供全面的 Redis 命令支持。

优点：

- API 设计与 Redis 命令一致，易于上手。
- 支持 pipelining、事务、LUA 脚本、Redis Sentinel 和 Redis Cluster 等高级特性。
- 客户端轻量，便于集成。

缺点：

- 使用阻塞 I/O，方法调用为同步，可能导致性能瓶颈。
- 在多线程环境下非线程安全，需要使用连接池管理实例。
- 不支持读写分离，技术文档较少.

## 快速入门

https://github.com/feed981/e01/tree/feed01/Redis/jedis-demo

依赖
```xml 
      <!--jedis-->
      <dependency>
          <groupId>redis.clients</groupId>
          <artifactId>jedis</artifactId>
          <version>3.7.0</version>
      </dependency>
      <dependency>
          <groupId>org.junit.jupiter</groupId>
          <artifactId>junit-jupiter-api</artifactId>
          <version>5.7.0</version>
      </dependency>
```
测试
```java
package com.feed01.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

public class JedisTest {
    private Jedis jedis;

    @BeforeEach
    void setUp(){
        // 建立连线
        jedis = new Jedis("192.168.33.10" ,6379);
        // 设置密码
        jedis.auth("qwe123");
        // 选择库0
        jedis.select(0);
    }

    @Test
    void testString(){
        String result = jedis.set("name","aab");
        System.out.println("result = " + result);
        String name = jedis.get("name");
        System.out.println("name = " + name);
    }

    @Test
    void testHash(){
        jedis.hset("user:1","name","bob");
        jedis.hset("user:1","age","321");
        Map<String,String> map = jedis.hgetAll("user:1");
        System.out.println(map);
    }
    /**
     * 每次执行完一个测试用例，无论测试成功还是失败，都会调用 tearDown() 方法，
     * 确保 jedis 资源被正确释放。这种模式用于避免资源泄露，
     * 尤其是在使用外部资源（如 Redis 连接）时，保证连接不会在测试完成后依然占用资源。
     *
     * 这样可以保证每个测试的资源管理是独立的，不会因为前一个测试没有释放资源而影响到后面的测试。
     */
    @AfterEach
    void tearDown(){
        if(jedis != null){
            jedis.close();
        }
    }
}

```

## 总结

Jedis使用的基本步骤:
1. 引入依赖
2. 创建Jedis对象,建立连接
3. 使用Jedis,方法名与Redis命令一致
4. 释放资源

# Jedis连接池
jedis本身是线程不安全的,并且频繁的创建和销毁连接会有性能损耗,因此我们推荐大家使用Jedis连接池代替Jedis的直连方式。

连线池
```java 
package com.feed01.jedis.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisConnectionFactory {
    private static final JedisPool  jedisPool;

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

```

测试
```java
package com.feed01.test;

import com.feed01.jedis.util.JedisConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

public class JedisTest {
    private Jedis jedis;

    @BeforeEach
    void setUp(){
        // 1. 建立连线
//        jedis = new Jedis("192.168.33.10" ,6379);
        // 2. 连线池: 建立连线
        // 设置密码
        jedis.auth("qwe123");
        // 选择库0
        jedis.select(0);
    }
    @AfterEach
    void tearDown(){
        if(jedis != null){
            jedis.close();
        }
    }
}

```
jedis.close();释放资源: 进到close()方法: 当有连线池时会归还资源
```java    
// 当有连线池时会归还资源
public void close() {
        if (this.dataSource != null) {
            JedisPoolAbstract pool = this.dataSource;
            this.dataSource = null;
            if (this.isBroken()) {
                pool.returnBrokenResource(this);
            } else {
                pool.returnResource(this);
            }
        } else {
            super.close();
        }

    }
```


