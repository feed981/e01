# 优惠券秒杀

# 全局唯一ID

当用户抢购时,就会生成订单并保存到tb_voucher_order这张表中,而订单表如果使用数据库自增ID就存在一些问题

1、id的规律性太明显 

用户能在订单列表看到详细的订单编号，所以今天id的规律性比如今天下一单订单号是10 ，隔天又下一单的订单号是100，用户就能轻易推测是这个商城昨天90单

2、受单表数据量的限制

单张表无法存储大量比如几千万的订单
所以必须要拆成多张表存储，但如何确认订单号不重复

## 全局ID生成器

是一种在分布式系统下用来生成全局唯一ID的工具,一般要满足下列特性:

1. 唯一性
2. 高可用
3. 高性能
4. 递增性
5. 安全性

## Redis实现全局唯一id

```bash
192.168.33.10:6379> INCR key
(integer) 1
192.168.33.10:6379> GET key
"1"
```

目标:

生成这样二进制的"数值" 不是字串
实际上可能是ex: 98575877469664562 然后转二进制

ID的组成部分:
- 符号位: 1bit,永远为0
- 时间戳: 31bit,以秒为单位,可以使用69年
- 序列号: 32bit,秒内的计数器,支持每秒产生2^32个不同ID


```java 
package com.hmdp.utils;

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

        // 序列号是32 bit 所以时间戳向左移32 来放序列号
        // 意味着 timestamp 的值将被乘以 2的32次方，并且其原来的低位部分(右边)会被填充为0 就可以放序列号， 一天的序列号也不可能超过32 bit 这边rediskey 有 获取当前日期 精确到天
        return timestamp << COUNT_BITS | increment;
    }
}

```

单元测试
```java 
package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests{
    
    @Resource
    private RedisIdWorker redisIdWorker;
    
    // 创建一个固定大小的线程池，最大线程数为500
    private ExecutorService es = Executors.newFixedThreadPool(500);

@Test
void testIdWorker() throws InterruptedException {
    // 创建一个 CountDownLatch，用于控制线程的同步
    // 这里设置为300，表示需要等待300个线程完成
    CountDownLatch countDownLatch = new CountDownLatch(300);

    // 定义一个任务，任务内容是生成ID并打印
    Runnable task = () -> {
        // 每个线程将生成100个ID
        for(int i = 0; i < 100; i++) {
            // 调用 redisIdWorker 的 nextId 方法生成一个新的 ID
            long id = redisIdWorker.nextId("order");
            // 打印生成的 ID
            System.out.println("id = " + id);
        }
        // 每个线程完成后，调用 countDown() 方法减少计数器的值
        countDownLatch.countDown();
    };

    // 记录开始时间
    long begin = System.currentTimeMillis();

    // 提交300个任务到线程池中执行
    for(int i = 0; i < 300; i++) {
        es.submit(task); // 线程池是异步执行任务
    }

    // 等待所有任务完成，直到计数器减到0
    countDownLatch.await();

    // 记录结束时间
    long end = System.currentTimeMillis();

    // 输出总耗时
    System.out.println("time = " + (end - begin));
  }
}
```

打开小算盘把 98575877469664562 贴上去看二进位
得到
BIN 0001 0101 1110 0011 0110 0011 1101 0000 0000 0000 0000 0111 0101 0011 0010

## 关于 << >> 位移运算符

- 二进制转换为十进制

8 位的二进制数 11010101
最前面的1代表 1 * 2的7次方 以此类推最后面的 1 * 2的0次方  全部加总得到 = 128+64+16+4+1 = 213
0 * 多少都是0

- 十进制转换为二进制

213 % 2 = 106..1 依序除下去取余数最后再从下到上：1,1,0,1,0,1,0,1 就会得到 8 位的二进制数 11010101

- 高位/低位

高位：存储重要信息（如时间戳），通常占据更左侧的位置。
低位：存储次要信息（如递增计数器），通常占据更右侧的位置。

- 100 << 5 

```
long l = 100 << 5; // 等于 100 * 2的五次方 = 3200

原始二进制（100）:      0000 0000 0000 0000 0000 0000 0110 0100
左移 5 位后:           0000 0000 0000 0000 0000 1100 1000 0000

接着将二进制转换为十进制
  1 * 2的10次方
+ 1 * 2的9次方
+ 1 * 2的6次方
--------------
= 1024+512+128
= 3200
```

- 100 << 5 | 2

十进制: 100<<5|2=100×32+2=3202

二进制
```
3200   11001000000
2      00000000010
    -----------------
       11001000010
```

- 100 >> 5

```
原始二进制（100）:      0000 0000 0000 0000 0000 0000 0110 0100
右移 5 位后:           0000 0000 0000 0000 0000 0000 0000 0011


着将二进制转换为十进制
  1 * 2的1次方
+ 1 * 2的0次方
--------------
= 3
```

## 还原 timestamp << 32 | increment 取得incr 跟时间戳

```java
long id = 98575877469664562L;
// 掩码为 0xFFFFFFFF（即二进制的 32 个 1），其值为 4294967295
long increment = id & 4294967295L; // 提取低32位
long timestamp = id >> 32; // 右移32位，获取高位 ，右移后低位的序列号就不存在了
System.out.println("increment = " + increment);
System.out.println("timestamp = " + timestamp);
```

## 总结

全局唯一ID生成策略:
- UUID
- Redis自增
- snowflake算法
- 数据库自增 (用另一张表管理自增ID)

Redis自增ID策略:
- 每天一个key,方便统计订单量
- ID构造是时间戳+计数器

