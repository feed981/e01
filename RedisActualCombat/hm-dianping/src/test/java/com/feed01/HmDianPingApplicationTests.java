package com.feed01;

import com.feed01.entity.Shop;
import com.feed01.service.impl.ShopServiceImpl;
import com.feed01.utils.CacheClient;
import com.feed01.utils.RedisConstants;
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

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }
}