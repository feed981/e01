package com.feed01.utils;

public class RedisConstants {

    // 登陆注册
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long CACHE_SHOP_TTL = 30L;

    // 商户缓存
    public static final Long CACHE_NULL_TTL = 2L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType";
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    // 优惠券
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    //  达人探店
    public static final String BLOG_LIKED_KEY = "blog:liked:";

    // 推送
    public static final String FEED_KEY = "feeds:";
}
