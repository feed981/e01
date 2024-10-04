package com.feed01;

import com.feed01.pojo.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

/**
 * {
 *     "@class": "com.feed01.pojo.User",
 *     "name": "鸟哥",
 *     "age": 32
 * }
 *
 * 存在问题:
 * 为了在反序列化时知道对象的类型,
 * JSON序列化器会将类的class类型写入json结果中
 * ,存入Redis,会带来额外的内存开销。
 *
 * 解决方式:
 * 使用 StringRedisTemplate
 * 为了节省内存空间,我们并不会使用JSON序列化器来处理value,
 * 而是统一使用String序列化器,要求只能存储String类型的key和value。
 * 当需要存储java对象时,手动完成对象的序列化和反序列化。
 *
 */

@SpringBootTest
class RedisDemoApplicationTests2 {

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Test
	void testString() {
		// 写入一条String数据
		stringRedisTemplate.opsForValue().set("name", "鸟哥");
		// 获取string数据
		Object name = stringRedisTemplate.opsForValue().get("name");
		System.out.println("name = " + name);
	}

	private static final ObjectMapper mapper = new ObjectMapper();
	@Test
	void testSaveUser() throws JsonProcessingException {
		// 创建对象
		User user = new User("鸟哥" ,32);
		// 手动序列化
		String json = mapper.writeValueAsString(user);
		// 写入数据
		stringRedisTemplate.opsForValue().set("user:2", json);

		// 获取数据
		String jsonUser = stringRedisTemplate.opsForValue().get("user:2");
		// 手动反序列化
		user = mapper.readValue(jsonUser ,User.class);
		System.out.println("user:2 = " + user);
	}

	@Test
	void testHash(){
		stringRedisTemplate.opsForHash().put("user:4" ,"name" , "鸟哥");
		stringRedisTemplate.opsForHash().put("user:4" ,"age" , "32");
		Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries("user:4");
		System.out.println("entries = " + entries);
	}
}