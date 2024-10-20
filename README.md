# 附近商铺

# GEO数据结构的基本用法

GEO数据结构
GEO就是Geolocation的简写形式,代表地理坐标。Redis在3.2版本中加入了对GEO的支持,允许存储地理坐标信息,帮
助我们根据经纬度来检索数据。
常见的命令有:


| Column 1 | Column 2 | Column 3 |
| -------- | -------- | --------|
| GEOADD     | 添加一个地理空间信息,包含:经度(longitude)、纬度(latitude)、值(member)     | |
|GEODIST|计算指定的两个点之间的距离并返回||
|GEOHASH|将指定member的坐标转为hash字符串形式并返回||
|GEOPOS|返回指定member的坐标||
|GEORADIUS|指定圆心、半径,找到该圆内包含的所有member,并按照与圆心之间的距离排序后返回。|6.2以后已废弃|
|GEOSEARCH|在指定范围内搜索member,并按照与指定点之间的距离排序后返回。范围可以是圆形或矩形。|6.2.新功能|
|GEOSEARCHSTORE|与GEOSEARCH功能一致,不过可以把结果存储到一个指定的key。 |6.2.新功能|

##  练习Redis的GEO功能

```bash 
# 添加下面几条数据:
# 北京南站(116.378248 39.865275)
# 北京站( 116.4280339.903738)
# 北京西站(116.32228739.893729 )
192.168.33.10:6379> GEOADD g1 116.378248 39.865275 bjn 116.42803 39.903738 bj 116.322287 39.893729 bjx
(integer) 3

# 计算北京西站到北京站的距离
192.168.33.10:6379> GEODIST g1 bjn bjx km
"5.7300"

# 搜索天安门(116.39790439.909005)附近10km内的所有火车站,并按照距离升序排序
192.168.33.10:6379> GEOSEARCH g1 FROMLONLAT 116.397904 39.909005 BYRADIUS 10 km WITHDIST
1) 1) "bj"
   2) "2.6361"
2) 1) "bjn"
   2) "5.1452"
3) 1) "bjx"
   2) "6.6723"
```
# 导入店铺数据到GEO

附近商户搜索
按照商户类型做分组,类型相同的商户作为同一组,以typeld为key存入同一个GEO集合中即可



| Key | Value | Score |
| -------- | -------- | -------- |
| shop: geo:1     | 海底捞火锅     | 4069152240174578     |
|     | 新白鹿     | 4069879450313142     |
| shop: geo:2     | KAILEDI KTV     | 4069885469876391     |
|    | 星聚會     | 4069885424176331     |

1. 倒数据用
2. 查出所有店铺 group by shopType
    - redis key shop:geo:shopType
3. 写入redis GEOADD key 经度 纬度 member 
    - 单笔
4. 写入redis GEOADD key locations
    - 先写到一个集合，再存到redis
```java 
package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
public class GEOTest {
    @Resource
    private IShopService shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void geo(){
        // 查出所有店铺 group by shopType
        shopService.list().stream()
            .collect(Collectors.groupingBy(Shop::getTypeId))
            .forEach((k,v)->{
                List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(v.size());
                // k 店铺类型
                String key = "shop:geo:" + k;
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

```

# 实现附近商户功能

SpringDataRedis的2.3.9版本并不支持Redis 6.2提供的GEOSEARCH命令，因此我们需要提示其版本，修改自己的POM

导入pom

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <exclusions>
        <exclusion>
            <artifactId>spring-data-redis</artifactId>
            <groupId>org.springframework.data</groupId>
        </exclusion>
        <exclusion>
            <artifactId>lettuce-core</artifactId>
            <groupId>io.lettuce</groupId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-redis</artifactId>
    <version>2.6.2</version>
</dependency>
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
    <version>6.1.6.RELEASE</version>
</dependency>
```


```java 
package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {


    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x" ,required = false)Double x,
            @RequestParam(value = "y" ,required = false)Double y
    ) {
        return shopService.queryShopByType(typeId ,current ,x ,y);
    }

}

```

按距离排序

```java 
package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop")
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableField(exist = false)
    private Double distance;
}

```
1. 不根据坐标查询，根据类型分页查询
2. 计算分页参数 预设是5笔 
    - from 起始 end 结束
3. 根据这个坐标查找范围是5公里，并且要返回距离的数据 GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
4. 解析redis的值取得id 跟 距离存在list 跟 map
5. 查店铺 根据 redis 距离的排序


```java 
package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if(x == null || y == null){
            // 不根据坐标查询，根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 查询redis 按照距离排序、分页 结果 shopId distance
        String key = SHOP_GEO_KEY + typeId;

        // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y), // 根据这个坐标
                new Distance(5000), // 查找范围是5公里
                RedisGeoCommands.GeoSearchCommandArgs
                        .newGeoSearchArgs().includeDistance() // WITHDISTANCE 也要返回距离的数据
                        .limit(end) // 只能查全部
        );

        if(search == null){
            return Result.ok(Collections.emptyList());
        }

        // 解析id
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = search.getContent();
//        log.info("content:{}",content.toString());
        if(content.size() <= from){
            // 没有下一页，结束
            return Result.ok(Collections.emptyList());
        }
        // 因为redis 查全部所以这边分页 需要截取出 from ~ end
        List<Long> ids = new ArrayList<>(content.size());
        Map<String ,Distance> distanceMap = new HashMap<>(content.size());
        content.stream().skip(from).forEach(r->{
//            log.info("id:{},distance:{}",r.getContent().getName(),r.getDistance());

            // 获取店铺id
            String shopId = r.getContent().getName();
            ids.add(Long.valueOf(shopId));
            // 获取距离
            Distance distance = r.getDistance();
            distanceMap.put(shopId ,distance);
        });

        // 查店铺 根据 redis 距离的排序
        String join = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + join + ")").list();
        shops.forEach(shop ->{
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
//            log.info("id:{},distance:{}",shop.getId(),shop.getDistance());
        });

        return Result.ok(shops);
    }
}

```
