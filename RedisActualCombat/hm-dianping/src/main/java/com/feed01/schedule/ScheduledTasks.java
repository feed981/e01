package com.feed01.schedule;

import cn.hutool.core.util.StrUtil;
import com.feed01.entity.Blog;
import com.feed01.entity.Follow;
import com.feed01.po.BlogTheUsersWhoFollow;
import com.feed01.service.IBlogService;
import com.feed01.service.IFollowService;
import com.feed01.utils.DateTimeFormatterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.feed01.utils.RedisConstants.FEED_KEY;

@Slf4j
@Component
public class ScheduledTasks {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IBlogService blogService;
    @Resource
    private IFollowService followService;

    // TODO: 每5分钟执行推送刚关注的博客的最新博文 ，每小时候推送关注的博客所有博文
    // 1. 捞不到文章 map 没k redis 存活时间失效
    @Scheduled(cron = "0 */5 * * * ?")
    public void update_5min(){
        LocalDateTime now = LocalDateTime.now();
        // 检查当前分钟是否为 0（即整点）
        queryAllBlogTheUsersWhoFollow();
    }

    // 查看博客有哪些粉丝 然后把博文推送给他们
    public void queryAllBlogTheUsersWhoFollow(){

        /**
         // 有哪些人有关注人
         Map<Long, List<Long>> collect = followService.query().list().stream()
         .collect(Collectors.groupingBy(Follow::getUserId,
         Collectors.mapping(Follow::getFollowUserId, Collectors.toList())));

         collect.forEach((k, v) -> {
         // k 粉丝 , v 关注的博客
         String join = StrUtil.join(",", v);

         // 捞出关注的博客的博文
         List<Long> blogIds = blogService.query().in("id",join).orderByDesc("liked").orderByDesc("update_time").list().stream().map(Blog::getId).collect(Collectors.toList());

         */
        List<BlogTheUsersWhoFollow> blogTheUsersWhoFollows = followService.queryAllBlogTheUsersWhoFollow();
        // log.info(blogTheUsersWhoFollows.toString());

        Map<Long, List<Long>> map = new HashMap<>();

        // 解析成 粉丝: List 关注的博客的博文
        blogTheUsersWhoFollows.forEach(b -> {
            map.computeIfAbsent(b.getUserId(), k -> new ArrayList<>()).add(b.getId());
        });
        log.info("map:{}", map);

        // 2. 定义要排除的 keys
        Set<String> excludeKeys = new HashSet<>();

        // ex: 当前有关注的博客的博文id v= bolgId 2,3 , 上次关注的博客的博文id redis zrange= 2,3,5 比完remove 5
        map.forEach((k, v) -> {
            String key = FEED_KEY + k;
            excludeKeys.add(key);
            // 先全部加完反正set会去重复
            v.forEach(blogId -> {
                // TODO: blogId == null
                stringRedisTemplate.opsForZSet().add(key, blogId.toString(), System.currentTimeMillis());
            });

            // 比较之前有关注的，但后来取消关注了
            Set<String> set = stringRedisTemplate.opsForZSet().range(key, 0, -1);
            if (set != null) {
                set.stream()
                        .filter(e -> !v.contains(Long.valueOf(e))) // Filter out elements present in blogIds
                        .forEach(blogId -> {
                            stringRedisTemplate.opsForZSet().remove(key, blogId.toString()); // Remove from Redis
                        });
            }

        });
        // TODO: 原本有关注 但后来完全没关注 就无法更新redis
        // 1. 获取所有 feeds: 开头的 key
        Set<String> feedKeys = stringRedisTemplate.keys("feeds:*");

        // 3. 遍历 keys 并排除特定的 keys
        if (feedKeys != null) {
            for (String key : feedKeys) {
                // 如果该 key 不在列表中
                if (!excludeKeys.contains(key)) {
                    // 4. 删除 feeds:
                    stringRedisTemplate.delete(key);
                }
            }
        }
    }
}