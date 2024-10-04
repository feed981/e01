# 添加商户缓存

1. 首次访问时将数据库查到的商户跟商品类型相关信息先存到redis ，这样访问时命中redis时就不会再去访问数据库

商户

```java 
package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 判断redis 是否存在
        if(StrUtil.isNotBlank(shopJson)){
        // 存在，直接返回信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // 不存在，查数据库
        Shop shop = getById(id);
        if(shop == null){
            // 不存在，返回错误
            return Result.fail("店铺不存在");
        }
        // 存在，存redis，返回信息
        stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(shop));

        return Result.ok(shop);
    }
}

```

商品类型

```java 
package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(key, 0, -1); // 获取所有值
        List<ShopType> typeList = new ArrayList<>();

        if (shopTypeList != null && !shopTypeList.isEmpty()) {
            // 存在，直接返回信息
            for (String json : shopTypeList) {
                ShopType shopType = JSONUtil.toBean(json, ShopType.class);
                typeList.add(shopType);
            }
            // 返回商品类型
            return Result.ok(typeList);
        }

    // 不存在，查数据库
        typeList = query().orderByAsc("sort").list(); // 直接赋值
        if (typeList == null || typeList.isEmpty()) { // 使用 || 进行检查
            return Result.fail("无商品类型信息为空！");
        }

    // 清空 shopTypeList 并添加新数据
        shopTypeList.clear();
        for (ShopType bean : typeList) {
            String str = JSONUtil.toJsonStr(bean);
            shopTypeList.add(str);
        }

    // 写入redis缓存, 有顺序只能RPUSH
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypeList);

    // 返回商品类型
        return Result.ok(typeList);
    }
}
```
# 缓存更新

内存淘汰: 几乎是没再理他

超时剃除: 设置存活时间

主动更新:

1. (可控较高)Cache Aside Pattern
(自己写代码)由缓存的调用者,在更新数据库的同时更新缓存

2. Read/Write Through Pattern
缓存与数据库整合为一个服务,由服务来维护一致性。调用者调用该服务,无需关心缓存一致性问题。

3. Write Behind Caching Pattern
调用者只操作缓存,由其他线程异步的将缓存数据持久化到数据库,保证最终一致。

## Cache Aside Pattern

操作缓存和数据库时有三个问题需要考虑:
1. 删除缓存还是更新缓存?
    - 更新缓存:每次更新数据库都更新缓存,无效写操作较多 (更新过程中没有任何人来查询也就是写多读少)
    - (胜出) 删除缓存:更新数据库时让缓存失效,查询时再更新缓存 (只会删一次)
2. 如何保证缓存与数据库的操作的同时成功或失败?
    - 单体系统,将缓存与数据库操作放在一个事务
    - 分布式系统,利用TCC等分布式事务方案
3. 先操作缓存还是先操作数据库?
    - 先删除缓存,再操作数据库
    - 先操作数据库,再删除缓存

##  先删除缓存,再操作数据库

1. 一般情况
    - 线程1: 删除缓存 v=10 -> 更新数据库 v=20
    - 线程2: 查询缓存未命中，查数据库 -> 写入缓存 v=20
    - 结果: 缓存 v=20 ,数据库 v=20 一致

2. 异常情况 (常发生)
    - 线程1: 删除缓存 v=10 
    - 线程2: 查询缓存未命中，查数据库 -> 写入缓存 v=10
    - 线程1: 更新数据库 v=20

结果: 缓存 v=10 ,数据库 v=20 不一致

## (胜出) 先操作数据库,再删除缓存

1. 一般情况
    - 线程1: 更新数据库 v=20 -> 删除缓存 v=10 
    - 线程2: 查询缓存未命中，查数据库 -> 写入缓存 v=20
    - 结果: 缓存 v=20 ,数据库 v=20 一致

2. 异常情况 (几率很低)
    - 线程1: 查询缓存未命中(刚好TTL存活时间结束)，查数据库 
    - 线程2: 更新数据库 v=20 -> 删除缓存 v=10 
    - 线程1: 写入缓存 v=20 但没缓存

通常缓存写入速度远快于数据库写入所以几乎不可能发生这请况

## 总结

缓存更新策略的最佳实践方案:
1. 低一致性需求:使用Redis自带的内存淘汰机制
2. 高一致性需求:主动更新,并以超时剔除作为兜底方案
    - 读操作:
        - 缓存命中则直接返回
        - 缓存未命中则查询数据库,并写入缓存,设定超时时间
        
    - 写操作:
        - 先写数据库,然后再删除缓存
        - 要确保数据库与缓存操作的原子性

# 实现商铺缓存与数据库的双写一致

给查询商铺的缓存添加超时剔除和主动更新的策略

修改ShopController中的业务逻辑,满足下面的需求:
1. 根据id查询店铺时,如果缓存未命中,则查询数据库,将数据库结果写入缓存,并设置超时时间
2. 根据id修改店铺时,先修改数据库,再删除缓存

```java 
package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 判断redis 是否存在
        if(StrUtil.isNotBlank(shopJson)){
        // 存在，直接返回信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // 不存在，查数据库
        Shop shop = getById(id);
        if(shop == null){
            // 不存在，返回错误
            return Result.fail("店铺不存在");
        }
        // 存在，存redis，返回信息
        //TODO: 1、设置超时时间
        stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(shop)  ,CACHE_SHOP_TTL , TimeUnit.MINUTES);

        return Result.ok(shop);
    }

    //TODO: 2、先修改数据库,再删除缓存
    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}

```

测试修改

PUT http://localhost:8080/api/shop
```json 
{
    "area": "",
    "openHours": "10:00-22:00",
    "sold": 4215,
    "address": "29",
    "comments": 3035,
    "avgPrice": 80,
    "score": 37,
    "name": "102茶餐厅",
    "typeId": 1,
    "id": 1
}
```
console
```
2024-09-19 16:06:27.104 DEBUG 12004 --- [nio-8081-exec-7] com.hmdp.mapper.ShopMapper.updateById    : ==>  Preparing: UPDATE tb_shop SET name=?, type_id=?, area=?, address=?, avg_price=?, sold=?, comments=?, score=?, open_hours=? WHERE id=?
2024-09-19 16:06:27.107 DEBUG 12004 --- [nio-8081-exec-7] com.hmdp.mapper.ShopMapper.updateById    : ==> Parameters: 102茶餐厅(String), 1(Long), (String), 29(String), 80(Long), 4215(Integer), 3035(Integer), 37(Integer), 10:00-22:00(String), 1(Long)
```

```sql 
select * from tb_shop ts ;
```

# 缓存穿透

缓存穿透是指客户端请求的数据在缓存中和数据库中都不存在,这样缓存永远不会生效,这些请求都会打到数据库。
常见的解决方案有两种:
1. 缓存空对象
- 优点:实现简单,维护方便
- 缺点:
    - 额外的内存消耗
    - 可能造成短期的不一致
    
2. 布隆过滤
- 优点:内存占用较少,没有多余key
- 缺点:
    - 实现复杂
    - 存在误判可能


## 编码解决商铺查询的缓存穿透问题

1. 访问时查数据库，不存在则需要存空值到redis
2. 访问时多一个查看redis是否为空值的动作


```java 
 @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 判断redis 是否存在
        if(StrUtil.isNotBlank(shopJson)){
        // 存在，直接返回信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // TODO: 2、判断命中的缓存是否空值
        if(shopJson != null){
            return Result.fail("店铺信息不存在");
        }

        // 不存在，查数据库
        Shop shop = getById(id);
        if(shop == null){
            // TODO: 1、缓存穿透用 ，不存在，写入空值 避免访问数据库
            stringRedisTemplate.opsForValue().set(key ,"" ,RedisConstants.CACHE_NULL_TTL , TimeUnit.MINUTES);

            // 不存在，返回错误
            return Result.fail("店铺不存在");
        }
        // 存在，存redis，返回信息
        stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(shop)  ,CACHE_SHOP_TTL , TimeUnit.MINUTES);

        return Result.ok(shop);
    }

```

http://localhost:8080/api/shop/0
```
{"success":false,"errorMsg":"店铺不存在"}
```

console
```
2024-09-19 16:34:40.905 DEBUG 3316 --- [nio-8081-exec-2] com.hmdp.mapper.ShopMapper.selectById    : ==>  Preparing: SELECT id,name,type_id,images,area,address,x,y,avg_price,sold,comments,score,open_hours,create_time,update_time FROM tb_shop WHERE id=?
```
清空console 重查一次
http://localhost:8080/api/shop/0
```
{"success":false,"errorMsg":"店铺信息不存在"}
```
## 测试步驟
1. 先砍redis cache:shop:0 
2. 访问 http://localhost:8080/api/shop/0 第一次时 console 应该可以看到数据库查询语句的log 
3. 查完会将数据存到缓存，看下redis cache:shop:0是否为空白 
4. 接着清除console 
5. 访问多次看console 是否为空白
6. console 如果不为空白有查询语句代表没查redis 还是跑去访问数据库了

## 总结

缓存穿透产生的原因是什么?

用户请求的数据在缓存中和数据库中都不存在,不断发起这样的请求,给数据库带来巨大压力

缓存穿透的解决方案有哪些?
- 缓存null值 (但这种是属于被动防守)
- 布隆过滤
- 增强id的复杂度,避免被猜测id规律
- 做好数据的基础格式校验
- 加强用户权限校验
- 做好热点参数的限流


# 缓存击穿


缓存击穿问题也叫热点Key问题,就是一个被高并发访问并且缓存重建业务较复杂的key突然失效了,无数的请求访问会在瞬间给数据库带来巨大的冲击。

常见的解决方案有两种:
- 互斥锁
- 逻辑过期

## 互斥锁

由拿到锁的线程1执行，但其他线程需要等待线程1完成，因为等待所以拿到的肯定是最新的(一致性)

优点
- 没有额外的内存消耗
- 保证一致性
- 实现简单

缺点
- 线程需要等待,性能受影响
- 可能有死锁风险


## 逻辑过期
因为部份热点缓存TTL过期造成所以不加上TTL，而是存在value里面(ex: expire)，线程1另外开个线程2执行时间较长的查询跟写入，其他线程没拿到锁就会先返回舊数据 
```
KEY
heina:user:1 
VALUE
{name:"Jack", age:21, expire:152141223)
```

优点
- 线程无需等待,性能较好

缺点
- 不保证一致性
- 有额外内存消耗
- 实现复杂


# 利用互斥锁解决缓存击穿

## setnx

SETNX "SET if Not exists"
仅在指定的键不存在时，才设置该键的值。

1. 先到虚拟机的redis操作
2. 获取锁 SETNX key value
3. 释放锁 DEL key
   
```bash
[vagrant@localhost ~]$ docker exec -it my-redis bash
root@26f85fd018ed:/data# redis-cli -h 192.168.33.10 -p 6379

192.168.33.10:6379> AUTH qwe123
OK
192.168.33.10:6379> HELP SETNX

  SETNX key value
  summary: Set the string value of a key only when the key doesn't exist.
  since: 1.0.0
  group: string

# 互斥锁
192.168.33.10:6379> SETNX lock 1
(integer) 1
192.168.33.10:6379> GET lock
"1"
192.168.33.10:6379> SETNX lock 2
(integer) 0
192.168.33.10:6379> GET lock
"1"

# 释放锁
192.168.33.10:6379> DEL lock
(integer) 1
192.168.33.10:6379> SETNX lock 2
(integer) 1
192.168.33.10:6379> GET lock
"2"

# 避免不明原因未释放锁，通常会加上TTL
192.168.33.10:6379> SET lock 2 NX EX 10
OK
192.168.33.10:6379> TTL lock
(integer) 8
192.168.33.10:6379> GET lock
"2"
```

## 加上互斥锁

```java 
package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);

        // 缓存击穿
        Shop shop = queryWithMutex(id);

        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    // 缓存击穿
    public Shop queryWithMutex(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 判断redis 是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 存在，直接返回信息
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中的缓存是否空值
        if(shopJson != null){
//            return Result.fail("店铺信息不存在");
            return null;
        }
        Shop shop = null;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            // 实现缓存重建
            // TODO: 1、获取互斥锁
            boolean isLock = trylock(lockKey);
            // TODO: 2、判断是否获取成功
            if(!isLock){// 没取到锁的需要等取到锁的那个线程，避免一堆人同时访问数据库造成压力过大，所以才会休眠遞迴
                // TODO: 3、失败 休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 不存在，查数据库
            shop = getById(id);

            // TODO: 5、模拟重建超时
            Thread.sleep(200);
            if(shop == null){
                // 缓存穿透用 ，不存在写入空值 避免访问数据库
                stringRedisTemplate.opsForValue().set(key ,"" ,RedisConstants.CACHE_NULL_TTL , TimeUnit.MINUTES);

                // 不存在，返回错误
                return null;
            }
            // 存在，存redis，返回信息
            stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(shop)  ,CACHE_SHOP_TTL , TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // TODO: 4、释放锁
            unlock(lockKey);
        }

        return shop;
    }

    //缓存穿透
    public Shop queryWithPassThrough(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 判断redis 是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 存在，直接返回信息
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // TODO: 2、判断命中的缓存是否空值
        if(shopJson != null){
//            return Result.fail("店铺信息不存在");
            return null;
        }

        // 不存在，查数据库
        Shop shop = getById(id);
        if(shop == null){
            // TODO: 1、缓存穿透用 ，不存在写入空值 避免访问数据库
            stringRedisTemplate.opsForValue().set(key ,"" ,RedisConstants.CACHE_NULL_TTL , TimeUnit.MINUTES);

            // 不存在，返回错误
            return null;
        }
        // 存在，存redis，返回信息
        stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(shop)  ,CACHE_SHOP_TTL , TimeUnit.MINUTES);
        return shop;
    }

    // 互斥锁
    private boolean trylock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    // 释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}

```
## 性能压测 压力测试 Apache JMeter安装使用
https://www.youtube.com/watch?v=6Uk8wx5BjzU
https://jmeter.apache.org/download_jmeter.cgi

开启JMeter: C:\apache-jmeter-5.6.3\bin\jmeter.bat

语言设置: JMeter/Options/Choose Language/Chinese

线程组、HTTP请求、监听响应结果

## 执行JMeter

测之前记得先砍redis

console 数据库只触发一次，代表互斥锁是成功的

# 利用逻辑过期解决缓存击穿

## 单元测试: 封装逻辑过期时间 建议2

方式1: Shop 继承 RedisData
```java 
@Data
public class RedisData {
    private LocalDateTime expireTime;
}

// Shop 继承 RedisData
public class Shop implements Serializable {}
```

方式2: 把Shop 丢到 Object data
```java
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
```

## 测试
1. 这边将店铺信息丢到RedisData的 Object data
2. set 一个逻辑过期时间替代redis 的存活时间

```java
package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void saveShop2Redis(Long id ,Long expireSeconds){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 店铺信息
        Shop shop = getById(id);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(redisData));
    }

}

```
```java 
package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Test
    void testSaveShop(){
        shopService.saveShop2Redis(1L ,10L);
    }

}

```

查看redis 数据是否有逻辑过期时间


## 逻辑过期解决缓存击穿

```java 
package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);

        // 互斥锁: 解决缓存击穿
//        Shop shop = queryWithMutex(id);

        // 逻辑过期: 解决缓存击穿
        Shop shop = queryWithLogicExpire(id);

        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    // 线程池
    private static final ExecutorService CACHE_EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);

    // 逻辑过期: 解决缓存击穿，不需要判断缓存穿透
    public Shop queryWithLogicExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 判断redis 是否存在
        if(StrUtil.isBlank(shopJson)){
            // 未命中
            return null;
        }

        // TODO: 1、命中，json反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject jsonObject = (JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(jsonObject, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判端是否过期
        // TODO: 2、未过期，返回店铺信息
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }

        // TODO: 3、过期，缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        // 获取互斥锁
        boolean isLock = trylock(lockKey);
        //  判断是否获取成功
        if(isLock){
            // TODO: 4、成功，开启新线程 缓存重建
            CACHE_EXECUTOR_SERVICE.submit(()->{
                try {
                    // 缓存重建，查DB 存redis
                    this.saveShop2Redis(id ,RedisConstants.LOCK_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // TODO: 5、失败，返回过期的店铺信息
        return shop;
    }


    // 互斥锁
    private boolean trylock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    // 释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id ,Long expireSeconds) throws InterruptedException {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 延迟测试
        Thread.sleep(200);
        // 店铺信息
        Shop shop = getById(id);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(redisData));
    }
}

```

## 执行JMeter

查看redis 可以看到因为没有设置TTL 而是改用逻辑过期时间所以key还在，但逻辑上来说这个key已经过期了所以该缓存重建了

测试目标:
1. 高并发时会不会大家都在重建缓存
2. 缓存一致性

测之前DB先改资料让他跟缓存不一致

console 数据库只触发一次，代表逻辑过期是成功的，并发是安全的

查看JMeter响应数据可以看到 Thread.sleep(200); 两百毫秒所以大概隔一秒就会重建了并且前后是会有一致性

## 测试步驟
1、用单元测试先插入逻辑过期时间
2、查看redis 逻辑过期时间是否更新 
3、到DB更新栏位 ex:102茶餐厅 改成 103茶餐厅 
4、JMeter 压测设定1秒100次 ，HTTP请求 http localhost 8081  /shop/1
5、访问第一次时 console 应该可以看到数据库查询语句的log 查完会将数据存到缓存，看下redis 是否更新 
6、查看JMeter 取样结果的响应数据 应该在隔一秒会看到数据改变

# 封装Redis工具类

缓存工具封装
基于String Redistemplate封装一个缓存工具类,满足下列需求:

## 方法1: 将任意Java对象序列化
将任意Java对象序列化为json并存储在string类型的key中,并且可以设置TTL过期时间

```java
package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    
public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
}
```

## 方法2: 设置逻辑过期时间
将任意Java对象序列化为json并存储在string类型的key中,并且可以设置逻辑过期时间,用于处理缓存击穿问题

```java
package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
        
public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // 逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
}
```


## 方法3: 缓存空值解决缓存穿透
根据指定的key查询缓存,并反序列化为指定类型,利用缓存空值的方式解决缓存穿透问题

工具类
```java 
package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

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
    
    
public <R, ID> R queryWithPassThrough(
            String keyPrefix , ID id , Class<R> type , Function<ID ,R> dbFallback ,Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // redis 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 判断redis 是否存在
        if (StrUtil.isNotBlank(json)) {
            // 存在，直接返回信息
            return JSONUtil.toBean(json, type);
        }

        // 判断命中的缓存是否空值
        if (json != null) {
            return null;
        }

        // 不存在，查数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 缓存穿透用 ，不存在写入空值 避免访问数据库
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

            // 不存在，返回错误
            return null;
        }
    
        // 存在，存redis，返回信息
        this.set(key ,r ,time ,unit);
        return r;
    }
}
```

service
```java 
package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

        @Override
    public Result queryById(Long id) {
Shop shop = cacheClient.queryWithPassThrough(
               RedisConstants.CACHE_SHOP_KEY ,id ,Shop.class , this::getById , RedisConstants.CACHE_SHOP_TTL ,TimeUnit.MINUTES);

// this::getById  ==> id -> getById(id)
        
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
}
```

### 测试步驟

1. 先砍redis cache:shop:0
2. 
http://localhost:8080/api/shop/0 访问第一次时console 应该可以看到数据库查询语句的log 
3. 查完会将数据存到缓存，看下redis cache:shop:0是否为空白
4. 接着清除console
5. 访问多次看console 是否为空白
6. console 如果不为空白有查询语句代表没查redis 还是跑去访问数据库了

## 方法4: 利用逻辑过期解决缓存击穿

根据指定的key查询缓存,并反序列化为指定类型,需要利用逻辑过期解决缓存击穿问题

工具类
```java 
package com.hmdp.utils;

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

```

service 
```java 
package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    
 @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryWithLogicExpire(
                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, RedisConstants.LOCK_SHOP_KEY ,this::getById, 20L, TimeUnit.MINUTES);

        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

```

### 测试步驟

1. 先插逻辑过期时间
```java 
package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;
    @Resource
    private ShopServiceImpl shopService;

    @Test
    void testSaveShop2() {
        Long id = 1L;
        Shop shop = shopService.getById(id);
        // 测试缓存击穿 先插逻辑过期时间
        cacheClient.setWithLogicExpire(RedisConstants.CACHE_SHOP_KEY + id, shop, 10L, TimeUnit.SECONDS);
    }

}
```

2. 查看redis 逻辑过期时间是否更新
3. 到DB更新栏位ex:102茶餐厅 改成 103茶餐厅
4. JMeter 压测设定1秒100次
5. 访问第一次时 console 应该可以看到数据库查询语句的log 查完会将数据存到缓存，看下redis 是否更新
6. 查看JMeter 取样结果的响应数据 应该在隔一秒会看到数据改变

# 总结
