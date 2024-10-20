package com.feed01;

import com.feed01.entity.Shop;
import com.feed01.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.feed01.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
public class GEOTest {
    @Resource
    private IShopService shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 倒数据用
    @Test
    void geo(){
        // 查出所有店铺 group by shopType
        shopService.list().stream()
            .collect(Collectors.groupingBy(Shop::getTypeId))
            .forEach((k,v)->{
                List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(v.size());
                // k 店铺类型
                String key = SHOP_GEO_KEY + k;
                v.forEach(shop -> {
                    // 写入redis GEOADD key 经度 纬度 member
//                    stringRedisTemplate.opsForGeo().add(key , new Point(shop.getX() ,shop.getY()) ,shop.getId().toString());
                    locations.add(new RedisGeoCommands.GeoLocation<>(
                            shop.getId().toString(),
                            new Point(shop.getX() ,shop.getY())
                    ));
                });
                stringRedisTemplate.opsForGeo().add(key , locations);
        });
    }
}
