package com.feed01;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feed01.entity.Blog;
import com.feed01.entity.Follow;
import com.feed01.po.BlogTheUsersWhoFollow;
import com.feed01.service.IBlogService;
import com.feed01.service.IFollowService;
import com.feed01.utils.DateTimeFormatterUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.feed01.utils.RedisConstants.FEED_KEY;

@Slf4j
@SpringBootTest
public class BlogTest {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;
    @Resource
    private IBlogService blogService;
    @Resource
    DateTimeFormatterUtil dateTimeFormatterUtil;

    @Test
    void test() {
//        isFollow(false ,4033L);
        // select follow_user_id from tb_follow tf where user_id = 4033; -> 1011 ,2
        // SELECT id FROM tb_blog WHERE (user_id IN (1011 ,2)) ORDER BY id desc; -> 23,5,4

        queryBlogTheUsersWhoFollowTest();
    }


    public void queryBlogTheUsersWhoFollowTest(){
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
        log.info("excludeKeys:{}", excludeKeys);
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
    public void queryBlogTheUsersWhoFollowTest2(){

//        Map<String ,Object> params = new HashMap<>();
//        params.put("startTime", 0);
//        String scheFollowKey = "schedule:blogTheUsersWhoFollow";
//
//        String lastEndTime = stringRedisTemplate.opsForValue().get(scheFollowKey);
//        if(lastEndTime != null){
//            // 先推送最新博文就好
//            params.put("startTime", dateTimeFormatterUtil.yyyyMMddHHmmss(lastEndTime));
//        }
//
//        // 半夜跑的排程 全部刷新一次
//        if(daySchedule){
//            params.put("startTime", 0);
//        }
//
//        // 1. 当前时间
//        LocalDateTime endTime = LocalDateTime.now();
//        long endTimeSecond = endTime.toEpochSecond(ZoneOffset.UTC);
//        stringRedisTemplate.opsForValue().set(scheFollowKey , String.valueOf(endTimeSecond));
//        params.put("endTime",dateTimeFormatterUtil.yyyyMMddHHmmss(endTime));
//
//        log.info("params:{}",params);

//        params.put("startTime","2024-09-18 15:46:43");
//        params.put("endTime","2024-10-18 15:48:23");
        // 2. 第一次 会先取得 当前时间以前的记录 查看博客有哪些粉丝 然后把博文推送给他们
//        followService.queryBlogIdTheUserWhoFollow(null);
//        log.info(blogTheUsersWhoFollows.toString());
//
//        Map<Long, List<Long>> map = new HashMap<>();
//
//        // 解析成 粉丝: List 关注的博客的博文
//        blogTheUsersWhoFollows.forEach(b -> {
//            map.computeIfAbsent(b.getUserId(), k -> new ArrayList<>()).add(b.getId());
//        });
//        log.info("map:{}", map);
//
//        // ex: 当前有关注的博客的博文id v= bolgId 2,3 , 上次关注的博客的博文id redis zrange= 2,3,5 比完remove 5
//        map.forEach((k, v) -> {
//            String key = FEED_KEY + k;
//
//            // 先全部加完反正set会去重复
//            v.forEach(blogId -> {
//                // TODO: blogId == null
//                stringRedisTemplate.opsForZSet().add(key, blogId.toString(), System.currentTimeMillis());
//            });
//
//            // 比较之前有关注的，但后来取消关注了
//            // TODO: 但如果原本有 现在完全没关注，就没k redis 也没办法移除
//            Set<String> set = stringRedisTemplate.opsForZSet().range(key, 0, -1);
//            if (set != null) {
//                set.stream()
//                    .filter(e -> !v.contains(Long.valueOf(e))) // Filter out elements present in blogIds
//                    .forEach(blogId -> {
//                        stringRedisTemplate.opsForZSet().remove(key, blogId.toString()); // Remove from Redis
//                    });
//            }
//            stringRedisTemplate.expire(key, 2L, TimeUnit.MINUTES); // 刷新存活时间
//        });
    }
    // 关注时推送博文给当前用户
    public void isFollow(boolean isFollow ,Long userId) {
//         当前用户有关注谁
        List<Long> followUserIds = followService.query().eq("user_id", userId).list()
                .stream()
                .map(Follow::getFollowUserId)
                .map(Long::valueOf) // 记得要转型 不然下面会查不到
                .collect(Collectors.toList());

        // 关注的博客的博文按发布顺序倒序
        // select id from tb_blog where user_id in( ?) order by id desc;
        List<Blog> blogIds = blogService.query().in("user_id" ,followUserIds)
                .orderByDesc("id")
                .list();

        List<Long> collect = blogIds.stream()
                .map(Blog::getId)
                .collect(Collectors.toList());

        log.info("关注的博客的博文按发布顺序倒序:{}",collect);


//        // 关注者
        String key = FEED_KEY + userId;
//
//        // 关注就推送博客的文给当前用户
        if(isFollow){
            blogIds.forEach(blogId -> {
                // 推送笔记id 给所有粉丝， sortedSet add 粉丝id , 博文id ,时间戳
                stringRedisTemplate.opsForZSet().add(key ,blogId.toString() ,System.currentTimeMillis());
            });

        // 取消关注
        } else {
            // 先查全部 比对下 ZRANGE feeds:4033 0 -1 WITHSCORES
            Set<String> set = stringRedisTemplate.opsForZSet().range(key, 0, -1);
            // 使用流过滤出不在blogIds中的元素
            if(set == null){
                return;
            }
            log.info("原本有哪些博文:{}",set.toString());

            // 保留取消关注的博文，redis 再移除掉
            List<Long> rem = set.stream()
                    .filter(e -> !blogIds.contains(Long.valueOf(e))) // 过滤掉在blogIds中的元素
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            log.info("要排除的博文有哪些:{}",rem.toString());

            rem.forEach(blogId -> {
                stringRedisTemplate.opsForZSet().remove(key ,blogId.toString());
            });
        }
    }

    public static void main(String[] args) {
        Set<String> intersect = new HashSet<>(Arrays.asList("1", "2", "2", "2", "3"));
        List<Long> ids = intersect.stream()
                .map(Long::valueOf) // id String to Long
                .collect(Collectors.toList());
        System.out.println(ids);
    }

    public void uploadBlog(){
        // 查出所有博文，但因为有些刚测试已经跑新的saveBlog 所以要加上时间判断
        List<Blog> list = blogService.list(new QueryWrapper<Blog>()
                .lt("create_time", "2024-10-17")
        );

        List<Long> ids = list.stream()
                .map(Blog::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, List<Long>> blogIds = list.stream()
                .collect(Collectors.groupingBy(Blog::getUserId,
                        Collectors.mapping(Blog::getId, Collectors.toList())));

        // 找到有哪些人关注博主
        Map<Object, Object> map = new HashMap<>();
        map.put("blogIds", blogIds);

        ids.forEach(id -> {
            List<Long> followList = new ArrayList<>();
            List<Follow> followUserId = followService.query().eq("follow_user_id", id).list();
            followUserId.forEach(follow -> {
                // 关注者
                Long userId = follow.getUserId();
                // 推送笔记id 给所有粉丝， sortedSet add 粉丝id , 博文id ,时间戳
                String key = FEED_KEY + userId;

                // 这个博客的所有博文
                List<Long> longs = blogIds.get(id);
                longs.forEach(l -> {
                    stringRedisTemplate.opsForZSet().add(key, l.toString(), System.currentTimeMillis());
                });
                followList.add(userId);
            });
            map.put(id, followList);
        });
        log.info("map:{}",map);
    }
}
