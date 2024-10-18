# 好友关注

# 关注和取关

关注就是新增，
取消关注就是删除

```java 
package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    // 关注或取消关注
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId , @PathVariable Boolean isFollow){
        return followService.follow(followUserId ,isFollow);
    }


    // 页面载入时查看是否有关注
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId){
        return followService.isFollow(followUserId );

    }
}

```

```java 
package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 当前用户
        Long userId = UserHolder.getUser().getId();

        // 判断关注
        if(isFollow){
            // 关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            save(follow);
        // 取消关注
        }else {
            // delete from tb_follow where user_id = ? and follow_user_id = ?
            remove(new QueryWrapper<Follow>()
                    .eq("user_id" ,userId)
                    .eq("follow_user_id", followUserId));
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
}

```

# 博主的个人主页

## 个人主页的博文

GET /api/blog/of/user

```java 
package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam(value = "current", defaultValue = "1") Integer current ,@RequestParam("id") Long id){
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

}

```

## 个人主页的基本资讯
GET /api/user/{id}


```java 
package com.hmdp.controller;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    // 查看用户基本资讯
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        User user = userService.getById(userId);
        if(user == null){
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }
}
```

# 共同关注

GET /api/follow/common/{id}

共同关注就是求两个的交集，可以用SET
```bash 
192.168.33.10:6379> SADD s1 m1 m2
(integer) 2
192.168.33.10:6379> SADD s2 m3 m2
(integer) 2
192.168.33.10:6379> SINTER s1 s2
1) "m2"
```

把关注用户的id ，放入 redis 的 set集合

```java 
package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
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
            
            if(isSuccess){ // 先判断下是否成功再存redis
                // 把关注用户的id ，放入 redis 的 set集合 sadd userId followUserId
                stringRedisTemplate.opsForSet().add(key , followUserId.toString());
            }
        // 取消关注
        }else {
            // delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            
            if(isSuccess){ // 先判断下是否成功再存redis
                stringRedisTemplate.opsForSet().remove(key ,followUserId.toString());
            }
        }
        return Result.ok();
    }
}

```
共同关注

```java 
package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id );
    }

}

```
用博主id 与当前用户id 取得redis 交集，相当于SINTER s1 s2 这个动作

然后解析id集合 String to Long 后把集合再丢到查数据库查询，接着查完将返回转成UserDTO 避免显示用户敏感资讯，最后再返回这个dto集合就可了
```java 
package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

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
}
```

# 关注推送

## Feed流产品有两种常见模式

1. Timeline:不做内容筛选,简单的按照内容发布时间排序,常用于好友或关注。例如朋友圈
    - 优点:信息全面,不会有缺失。并且实现也相对简单
    - 缺点:信息噪音较多,用户不一定感兴趣,内容获取效率低
2. 智能排序:利用智能算法屏蔽掉违规的、用户不感兴趣的内容。推送用户感兴趣信息来吸引用户
    - 优点:投喂用户感兴趣信息,用户粘度很高,容易沉迷
    - 缺点:如果算法不精准,可能起到反作用

## Feed流的实现方案

1. 拉模式:也叫做读扩散。

消息只保存一份所以较节省内存空间

每次用户读取时都需要重新拉取他有关注的博主的收件箱消息到粉丝的发件箱，然后做时间戳的排序

会造成读取时的延迟较高


缺点: 延迟

2. 推模式:也叫做写扩散。

直接推送到粉丝收件箱，并且按时间戳排序

就不需要临时拉取造成读取的延迟

因为没有博主没有收件箱，消息直接发给有关注他的粉丝，所以是重复写了好几份

内存占用高

3. 推拉结合模式:也叫做读写混合,兼具推和拉两种模式的优点。

一般博客推送给粉丝直接推模式

超多粉丝的博客
- 普通粉丝: 拉模式，延迟高，但节省内存空间
- 活跃粉丝: 推模式，延迟低

# 基于推模式实现关注推送功能

需求:
1. 修改新增探店笔记的业务,在保存blog到数据库的同时,推送到粉丝的收件箱
2. 收件箱满足可以根据时间戳排序,必须用Redis的数据结构实现
3. 查询收件箱数据时,可以实现分页查询

## Feed流的分页问题

Feed流中的数据会不断更新,所以数据的角标也在变化,因此不能采用传统的分页模式。

## Feed流的滚动分页

Feed流中的数据会不断更新,所以数据的角标也在变化,因此不能采用传统的分页模式。

数据有变化的情况尽量不要用List队列而是用SortedSet


# 推送到粉丝收件箱

```java 
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBolg(blog);
    }
}
```
在保存博文后查看当前的所有粉丝，然后把博文推送给所有有关注自己的粉丝
```java 
package com.hmdp.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

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
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    

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
            // 推送笔记id 给所有粉丝 ，sortedSet add 粉丝id , 博文id ,时间戳
            String key = "feeds:" + userId;
            stringRedisTemplate.opsForZSet().add(key ,blog.getId().toString() ,System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }
}
```

# 滚动分页查询收件箱的思路

## 按脚标查询

```bash 
192.168.33.10:6379> ZADD z1 1 m1 2 m2 3 m3 4 m4 5 m5 6 m6
(integer) 3

192.168.33.10:6379> ZRANGE z1 0 6
1) "m1"
2) "m2"
3) "m3"
4) "m4"
5) "m5"
6) "m6"

192.168.33.10:6379> ZREVRANGE z1 0 2 WITHSCORES
1) "m6"
2) "6"
3) "m5"
4) "5"
5) "m4"
6) "4"

# 此时新增一笔
192.168.33.10:6379>  ZADD z1  7 m7
(integer) 1

# 预其查到 3 2 1 但因为新增一笔 所以变成 4 3 2
192.168.33.10:6379> ZREVRANGE z1 3 5 WITHSCORES
1) "m4"
2) "4"
3) "m3"
4) "3"
5) "m2"
6) "2"
```

## 滚动查询

```bash 
192.168.33.10:6379> ZREVRANGEBYSCORE z1 1000 0 WITHSCORES LIMIT 0 3
1) "m7"
2) "7"
3) "m6"
4) "6"
5) "m5"
6) "5"

192.168.33.10:6379>  ZADD z1 8 m8
(integer) 1

192.168.33.10:6379> ZREVRANGEBYSCORE z1 5 0 WITHSCORES LIMIT 1 3
1) "m4"
2) "4"
3) "m3"
4) "3"
5) "m2"
6) "2"

192.168.33.10:6379> ZREVRANGEBYSCORE z1 2 0 WITHSCORES LIMIT 1 3
1) "m1"
2) "1"
```

### 重复的score

```bash 
# m7、m6 都是6的情况 
192.168.33.10:6379> ZINCRBY z1 -1 m7
"6"

192.168.33.10:6379> ZREVRANGEBYSCORE z1 1000 0 WITHSCORES LIMIT 0 3
1) "m8"
2) "8"
3) "m7"
4) "6"
5) "m6"
6) "6"

# 因为有两个重复的6 所以 offset 1 还是查到下个6
192.168.33.10:6379> ZREVRANGEBYSCORE z1 6 0 WITHSCORES LIMIT 1 3
1) "m6"
2) "6"
3) "m5"
4) "5"
5) "m4"
6) "4"
```

滚动分页查询参数:

max: 当前时间戳 | 上一次查询的最小时间戳

min: 0

offset: 0 | 在上一次的结果中，与最小值一样的元素的个数

count: 3

# 实现滚动分页查询

```java 
package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
```

```java 
   public static final String FEED_KEY = "feeds:";
```

```java 
package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;


    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset ) {
        return blogService.queryBlogOfFollow(max , offset);
    }
}

```

```java 
package com.hmdp.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

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


    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}

```

## 排程更新博文

因为后来新增博文的业务逻辑有加上推送给关注者

所以稍早前的博文自己更新下才能在前端关注的tab看到

只有用户在点个人主页的关注时后需要刷新有关注的博客主的博文
，但如果放着这个滚动就会重新访问刷新

考虑过 
1. 如果放在关注、取关/follow/{id}/{isFollow} 这个一值关注、取消关注、关注、取消关注就会一值请求，数据库访问量会太大
2. 如果放在用户在点个人主页的关注时后 /blog/of/follow 但往上滚就会再请求一次，也是数据库访问量会太大
3. 排程，难点我是卡在如果用户原本有关注，但后来全部都不关注数据库就不会捞出来，没k就不会去抓redis 也就没办法有set 取消关注的那段的后续比对、remove的动作，后来是用 redis KEYS feeds:* 比对有捞出来的然后 DEL key

```java
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

        // 原本有关注 但后来完全没关注 就无法更新redis
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
```
入口

1. @EnableScheduling
2. @Bean

```java
package com.feed01;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@MapperScan("com.feed01.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableScheduling
public class HmDianPingApplication {
    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
    }
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5); // 根据需要设置线程池大小
        return scheduler;
    }

}
```

```sql
select tb.id ,tb.user_id as follow_user_id ,tf.user_id
from tb_blog tb
right join tb_follow tf on tf.follow_user_id = tb.user_id
where 1=1
and tb.update_time >0
order by tb.id desc;
```
