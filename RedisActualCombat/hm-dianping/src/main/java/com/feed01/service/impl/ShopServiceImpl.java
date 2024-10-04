package com.feed01.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feed01.dto.Result;
import com.feed01.entity.Shop;
import com.feed01.mapper.ShopMapper;
import com.feed01.service.IShopService;
import com.feed01.utils.CacheClient;
import com.feed01.utils.RedisConstants;
import com.feed01.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);
        // 使用工具类
//        Shop shop = cacheClient.queryWithPassThrough(
//                RedisConstants.CACHE_SHOP_KEY ,id ,Shop.class , this::getById , RedisConstants.CACHE_SHOP_TTL ,TimeUnit.MINUTES);

        // 互斥锁: 解决缓存击穿
//        Shop shop = queryWithMutex(id);

        // 逻辑过期: 解决缓存击穿
//        Shop shop = queryWithLogicExpire(id);
        Shop shop = cacheClient.queryWithLogicExpire(
                RedisConstants.CACHE_SHOP_KEY, id, Shop.class, RedisConstants.LOCK_SHOP_KEY ,this::getById, 20L, TimeUnit.MINUTES);

        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    // 线程池
    private static final ExecutorService CACHE_EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);


    /**
     * 逻辑过期: 解决缓存击穿，不需要判断缓存穿透
     * @param id
     * @return
     */
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

    /**
     *  互斥锁: 解决缓存击穿
     * @param id
     * @return
     */
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
            if(!isLock){
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
            stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(shop)  ,RedisConstants.CACHE_SHOP_TTL , TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // TODO: 4、释放锁
            unlock(lockKey);
        }

        return shop;
    }

    /**
     * 缓存穿透
     * @param id
     * @return
     */
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
        stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(shop)  ,RedisConstants.CACHE_SHOP_TTL , TimeUnit.MINUTES);
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
        // 店铺信息
        Shop shop = getById(id);
        // 延迟测试
        Thread.sleep(200);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(redisData));
    }

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
