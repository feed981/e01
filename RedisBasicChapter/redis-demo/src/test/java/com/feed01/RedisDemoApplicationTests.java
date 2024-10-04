package com.feed01;

import com.feed01.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 192.168.33.10:6379> KEYS *
 *  8) "\xac\xed\x00\x05t\x00\x04name"
 *  9) "name"
 *
 * 代码中set name "鸟哥"
 * 但在 redis get name 的时后 却没有把name 改成 "鸟哥"
 * 而是多出一个二进制序列化的key
 *
 * 存在问题:
 * 默认的序列化机制 key和value会是 JDK 序列化的二进制表示方式
 *
 * 解决方式:
 * 进行序列化的设置 com.feed01.redis.config.RedisConfig
 *
 */
@SpringBootTest
class RedisDemoApplicationTests {

	@Autowired
	private RedisTemplate<String ,Object> redisTemplate;

	@Test
	void testString() {
		// 写入一条String数据
		redisTemplate.opsForValue().set("name", "鸟哥");
		// 获取string数据
		Object name = redisTemplate.opsForValue().get("name");
		System.out.println("name = " + name);
	}

	@Test
	void testSaveUser(){
		redisTemplate.opsForValue().set("user:3",new User("鸟哥" ,32));
		Object o = redisTemplate.opsForValue().get("user:3");
		User user = (User) o ;
		System.out.println("o = " + o);
	}
}