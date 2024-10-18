package com.feed01.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feed01.dto.Result;
import com.feed01.dto.UserDTO;
import com.feed01.entity.Follow;
import com.feed01.mapper.FollowMapper;
import com.feed01.po.BlogTheUsersWhoFollow;
import com.feed01.service.IBlogService;
import com.feed01.service.IFollowService;
import com.feed01.service.IUserService;
import com.feed01.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.feed01.utils.RedisConstants.FEED_KEY;

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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;
    @Resource
    private IFollowService followService;

    // 当前用户的关注对象
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 当前用户
        Long userId = UserHolder.getUser().getId();

        // 判断关注
        String key = "follow:" + userId;
        if(isFollow){
            // 关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                // 把关注用户的id ，放入 redis 的 set集合 sadd userId followUserId
                stringRedisTemplate.opsForSet().add(key , followUserId.toString());
            }
        // 取消关注
        }else {
            // delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key ,followUserId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        // select count() from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        // 当前用户id
        Long userId = UserHolder.getUser().getId();

        // 求交集
        String key1 = "follow:" + id; // 博主id
        String key2 = "follow:" + userId; // 当前用户id
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect == null || intersect.isEmpty()){
            // 无交集
            return Result.ok(Collections.emptyList());
        }

        // 解析id集合 String to Long
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        // 查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }

    // 找到关注的博客有哪些博文id
    @Override
    public void queryBlogIdTheUserWhoFollow(Long userId ,Long max) {
        // userId 当前用户
        List<Long> blogIds = getBaseMapper().queryBlogIdTheUserWhoFollow(userId);
        String key = FEED_KEY + userId;
        for(Long blogId : blogIds){
            // 先全部加完反正set会去重复
            stringRedisTemplate.opsForZSet().add(key, blogId.toString(), max--);

        }
//        blogIds.forEach(blogId -> {
//        });

        Set<String> set = stringRedisTemplate.opsForZSet().range(key, 0, -1);
        log.info("blogIds:{},set:{}",blogIds ,set);
        // 比较之前有关注的，但后来取消关注了
        if (set != null) {
            set.stream()
                    .filter(e -> !blogIds.contains(Long.valueOf(e))) // Filter out elements present in blogIds
                    .forEach(b -> {
                        stringRedisTemplate.opsForZSet().remove(key, b.toString()); // Remove from Redis
                    });
        }

    }

    // 找到关注的博客有哪些博文id
    @Override
    public List<BlogTheUsersWhoFollow> queryAllBlogTheUsersWhoFollow() {
        return getBaseMapper().queryAllBlogTheUsersWhoFollow();
    }
}
