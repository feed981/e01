package com.feed01.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feed01.dto.Result;
import com.feed01.dto.UserDTO;
import com.feed01.entity.Blog;
import com.feed01.entity.User;
import com.feed01.mapper.BlogMapper;
import com.feed01.service.IBlogService;
import com.feed01.service.IUserService;
import com.feed01.utils.SystemConstants;
import com.feed01.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.feed01.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.feed01.utils.SystemConstants.MAX_PAGE_SIZE;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

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
                .page(new Page<>(current, MAX_PAGE_SIZE));
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

    private void isBlogLiked(Blog blog) {
        // 1. 获取登陆用户
        Long userId = UserHolder.getUser().getId();
        if(userId == null){
            // 用户未登录，无需查询是否点赞
            return;
        }
        // 2. 判断当前登录用户是否已经点赞
        String key = "blog:liked:" + blog.getId();

        //SET
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//        blog.setIsLike(BooleanUtil.isTrue(isMember));

        //ZSET
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
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

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
