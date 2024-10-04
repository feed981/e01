package com.feed01.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feed01.dto.Result;
import com.feed01.entity.ShopType;
import com.feed01.mapper.ShopTypeMapper;
import com.feed01.service.IShopTypeService;
import com.feed01.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

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
