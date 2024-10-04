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
        jedis = JedisConnectionFactory.getJedis();
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
        jedis.hset("user:5","name","bob");
        jedis.hset("user:5","age","321");
        Map<String,String> map = jedis.hgetAll("user:5");
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
