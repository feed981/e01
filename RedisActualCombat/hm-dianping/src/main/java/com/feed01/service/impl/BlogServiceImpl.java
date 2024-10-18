package com.feed01.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feed01.dto.Result;
import com.feed01.dto.ScrollResult;
import com.feed01.dto.UserDTO;
import com.feed01.entity.Blog;
import com.feed01.entity.Follow;
import com.feed01.entity.User;
import com.feed01.mapper.BlogMapper;
import com.feed01.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feed01.service.IFollowService;
import com.feed01.service.IUserService;
import com.feed01.utils.SystemConstants;
import com.feed01.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.feed01.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.feed01.utils.RedisConstants.FEED_KEY;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        // 查询blog 是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }


    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
//        records.forEach(this::queryBlogUser);
        return Result.ok(records);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        // 1. 获取登陆用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();

        // 2. 判断当前登录用户是否已经点赞
        String key = "blog:liked:" + blog.getId();

        //SET
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//        blog.setIsLike(BooleanUtil.isTrue(isMember));

        //ZSET
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }


    //SET
    @Override
    public Result likeBlog(Long id) {
        // 1. 获取登陆用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断当前登录用户是否已经点赞
        String key = "blog:liked:" + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if(BooleanUtil.isFalse(isMember)){
            // 3. 如果未点赞，可以点赞
            // 3.1. 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2. 保存用户 redis set集合 SADD、SISMEMBER
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key ,userId.toString());
            }
        }else{
            // 3. 如果已点赞，取消点赞 SMOVE
            // 3.1. 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 3.2. 移除用户 redis set集合
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key ,userId.toString());
            }
        }
        return Result.ok();
    }
    //ZSET
    @Override
    public Result likeBlog_zset(Long id) {
        // 1. 获取登陆用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score == null){
            // 3. 如果未点赞，可以点赞
            // 3.1. 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2. 保存用户 redis set集合 SADD、SISMEMBER
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key ,userId.toString() ,System.currentTimeMillis());
            }
        }else{
            // 3. 如果已点赞，取消点赞 SMOVE
            // 3.1. 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 3.2. 移除用户 redis set集合
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key ,userId.toString());
            }
        }
        return Result.ok();
    }

    // 点赞排行榜，博客的下方会按照时间顺序显示
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // top5 点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 解析用户id
        List<Long> ids = top5
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // 根据用户id 查询
        String idStr = StrUtil.join(",", ids);
//        List<UserDTO> userDTOs = userService.listByIds(ids)
        // SQL问题: 根据用户id 查询 WHERE id IN (5, 1) ORDER BY FIELD(id, 5, 1);
        List<UserDTO> userDTOS = userService.query().in("id" ,ids)
                .last("ORDER BY FIELD(id,"+idStr+")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBolg(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = blogService.save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
        // 查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        // follow_user_id 被关注的人
        List<Follow> followUserId = followService.query().eq("follow_user_id", user.getId()).list();
        for(Follow follow : followUserId){
            // 获取粉丝id
            Long userId = follow.getUserId();
            // 推送笔记id 给所有粉丝， sortedSet add 粉丝id , 博文id ,时间戳
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key ,blog.getId().toString() ,System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();

        // TODO: 更新有关注或是取消关注的博客 推送博文 访问次数过多，改成放排程
//        followService.queryBlogIdTheUserWhoFollow(userId ,max);


        String key = FEED_KEY + userId;

        // 查询收件箱 ZREVRANGEBYSCORE z1 1000 0 WITHSCORES LIMIT 0 3
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }

        // 解析数据: blogId , 时间戳 score = minTime , offset 在上一次的结果中，与最小值一样的元素的个数
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples){
            // 获取id
            String idStr = tuple.getValue();
            ids.add(Long.valueOf(idStr));

            // 时间戳
            long time = tuple.getScore().longValue();

            // 获取最小时间有几个
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }

            log.info("idStr:{} ,time:{} ,os:{}",idStr ,time ,os);
        }
        // 根据id 查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        blogs.forEach(blog -> {
            // 查看blog 有关的用户
            this.queryBlogUser(blog);
            // 查看blog 是否被点赞
            this.isBlogLiked(blog);
        });

        // 封装、返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}
