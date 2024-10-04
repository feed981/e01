# SpringDataRedis

https://spring.io/projects/spring-data-redis

## RedisTemplate快速入门

https://start.spring.io/

依赖
```xml
		<!--redis依赖-->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-redis</artifactId>
		</dependency>
		<!--common-pool-->
		<dependency>
			<groupId>org.apache.commons</groupId>
			<artifactId>commons-pool2</artifactId>
		</dependency>
```

设置
```yml
spring:
  redis:
    host: 192.168.33.10 #指定redis所在的host
    port: 6379  #指定redis的端口
    password: qwe123  #设置redis密码
    lettuce:
      pool:
        max-active: 8 #最大连接数
        max-idle: 8 #最大空闲数
        min-idle: 0 #最小空闲数
        max-wait: 100ms #连接等待时间
```
测试
```java 
package com.feed01.redis_demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class RedisDemoApplicationTests {

	@Autowired
	private RedisTemplate redisTemplate;

	@Test
	void testString() {
		// 写入一条String数据
        /**
         * 补充: 可以在这句断点在这看下 (默认的序列化机制)
		 * JdkSerializationRedisSerializer
         */
        redisTemplate.opsForValue().set("name", "鸟哥");
		// 获取string数据
		Object name = redisTemplate.opsForValue().get("name");
		System.out.println("name = " + name);
	}
}
```

## 总结

SpringDataRedis的使用步骤:
1. spring-boot-starter-data-redis
2. 在application.yml配置Redis信息
3. 注入Redistemplate_


## 存在的问题

默认的序列化机制 key和value会是 JDK 序列化的二进制表示方式

# 默认的序列化机制

从上面可以看到我们已经 set name "鸟哥"
但在 redis get name 的时后 却没有把name 改成 "鸟哥"
而是多出一个二进制序列化的key
```bash
192.168.33.10:6379> GET name
"aab"
192.168.33.10:6379> KEYS *
 8) "\xac\xed\x00\x05t\x00\x04name"
 9) "name"
```

Spring Data Redis 中的 RedisTemplate 在默认情况下使用 JdkSerializationRedisSerializer，

它会将 Java 对象序列化为字节数组（基于 JDK 的序列化机制），

因此你会看到类似 \xac\xed\x00\x05t\x00\x04name 这样的格式。这是 JDK 序列化的二进制表示方式。

## RedisTemplate RedisSerializer

依赖
```xml 
		<!--Jackson依赖，不引入springmvc的情况下，设置序列化器需要-->
		<dependency>
			<groupId>com.fasterxml.jackson.core</groupId>
			<artifactId>jackson-databind</artifactId>
		</dependency>

		<!--Lombok-->
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<version>1.18.30</version>
			<scope>provided</scope>
		</dependency>
```

序列化设置
```java 
package com.feed01.redis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory){
        // 创建 RedisTemplate 对象
        RedisTemplate<String ,Object> redisTemplate = new RedisTemplate<>();
        //设置连接工厂
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = new GenericJackson2JsonRedisSerializer();
        // 设置key 的序列化
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        // 设置value 的序列化
        redisTemplate.setValueSerializer(jsonRedisSerializer);
        redisTemplate.setHashKeySerializer(jsonRedisSerializer);

        return redisTemplate;
    }
}

```

pojo
```java
package com.feed01.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String name;
    private Integer age;
}
```

测试
```java 
package com.feed01;

import com.feed01.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class RedisDemoApplicationTests {

	@Autowired
	private RedisTemplate<String ,Object> redisTemplate;

	@Test
	void testString() {
		// 写入一条String数据
		redisTemplate.opsForValue().set("name", "六七");
		// 获取string数据
		Object name = redisTemplate.opsForValue().get("name");
		System.out.println("name = " + name);
	}

	@Test
	void testSaveUser(){
		redisTemplate.opsForValue().set("user:1",new User("鸟哥" ,32));
		User o = (User) redisTemplate.opsForValue().get("user:1");
		System.out.println("o = " + o);
	}
}
```

## 存在的问题

为了在反序列化时知道对象的类型,JSON序列化器会将类的class类型写入json结果中,存入Redis,会带来额外的内存开销。

```json
// 查看redis key: user:1

{
    "@class": "com.feed01.pojo.User",
    "name": "鸟哥",
    "age": 32
}
```

## 如何解决

为了节省内存空间,我们并不会使用JSON序列化器来处理value,而是统一使用String序列化器,要求只能存储String类型的key和value。当需要存储java对象时,手动完成对象的序列化和反序列化。

# StringRedisTemplate

```java
package com.feed01;

import com.feed01.pojo.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

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
```

# RedisTemplate的两种序列化实践方案:

方案一:
1. 自定义Redistemplate
2. 修改RedisTemplate的序列化器为GenericJackson2JsonRedisSerializer

方案二:
1. 使用StringRedistemplate
2. 写入Redis时,手动把对象序列化为JSON
3. 读取Redis时,手动把读取到的JSON反序列化为对象
