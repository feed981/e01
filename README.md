
# UV统计


# UV PV
首先我们搞懂两个概念:

## Unique Visitor(UV)

也叫独立访客量,是指通过互联网访问、浏览这个网页的自然人。1天内同一个用户多次访问该网站,只记录1次。

## Page View(PV)

也叫页面访问量或点击量,用户每访问网站的一个页面,记录1次PV,用户多次打开页面,则记录多次PV。往往用来衡量网站的流量。

UV统计在服务端做会比较麻烦,因为要判断该用户是否已经统计过了,需要将统计过的用户信息保存。但是如果每个访问的用户都保存到Redis中,数据量会非常恐怖。

# HyperLogLog的用法
Hyperloglog(HLL)是从Loglog算法派生的概率算法,用于确定非常大的集合的基数,而不需要存储其所有值。

相关算法原理大家可以参考: 
https://juejin.cn/post/6844903785744056333#heading-0

Redis中的HLL是基于string结构实现的,单个HLL的内存永远小于16kb,内存占用低的令人发指!作为代价,其测量结果是概率性的,有小于0.81%的误差。不过对于UV统计来说,这完全可以忽略。

```bash
# 加入
192.168.33.10:6379> PFADD hl1 e1 e2 e3 e4 e5 e6
(integer) 1

# 得到数量
192.168.33.10:6379> PFCOUNT hl1
(integer) 6

# 重复加入
192.168.33.10:6379> PFADD hl1 e1 e2 e3 e4 e5 e6
(integer) 0
192.168.33.10:6379> PFADD hl1 e1 e2 e3 e4 e5 e6
(integer) 0

# 得到数量
192.168.33.10:6379> PFCOUNT hl1
(integer) 6

```
# 测试百万数据的统计


```bash 
# 内存
192.168.33.10:6379> INFO MEMORY
# Memory
used_memory:1582440
```
单元测试
每1000条放到数组后发送一次
```java 
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
               
                // PFADD hl2 数组
                stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
            }
        }
        // 统计数量 PFCOUNT hl2
        Long hl2 = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(hl2);
    }

    // 内存
    void used_memory(){
        Properties info = stringRedisTemplate.getRequiredConnectionFactory().getConnection().info("memory");
        System.out.println("Used Memory: " + info.getProperty("used_memory"));
    }
}
```
