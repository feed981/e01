# 用户签到
# BitMap功能演示
我们按月来统计用户签到信息,签到记录为1,未签到则记录为0.

把每一个bit位对应当月的每一天,形成了映射关系。用0和1标示业务状态,这种思路就称为位图(BitMap)

Redis中是利用string类型数据结构实现BitMap,因此最大上限是512M,转换为bit则是 2^32个bit位。

## BitMap用法

Redis中是利用string类型数据结构实现BitMap,因此最大上限是512M,转换为bit则是 2^32个bit位。
BitMap的操作命令有:


| Column 1 | Column 2 | 
| -------- | -------- | 
| SETBIT     | 向指定位置(offset)存入一个0或1| 
| GETBIT     |获取指定位置(offset)的bit值|
|BITCOUNT |统计BitMap中值为1的bit位的数量|
|BITFIELD|操作(查询、修改、自增)BitMap中bit数组中的指定位置(offset)的值|
|BITFIELD_RO|获取BitMap中bit数组,并以十进制形式返回|
|BITOP|将多个BitMap的结果做位运算(与、或、异或)
BITPOS|查找bit数组中指定范围内第一个0或1出现的位置|

```bash 
# 0 第一天签到
192.168.33.10:6379> SETBIT bm1 0 1
(integer) 0

# 1 第二天签到
192.168.33.10:6379> SETBIT bm1 1 1
(integer) 0

192.168.33.10:6379> SETBIT bm1 2 1
(integer) 0

# 没签到默认就会给 0 所以不用特别SET 
# ex: 3 0 代表第四天未签

# 1 第五天签到
192.168.33.10:6379> SETBIT bm1 6 1
(integer) 0

# 1 第十一天签到
192.168.33.10:6379> SETBIT bm1 12 1
(integer) 0

# 查看第一天是否签到 1 代表有签
192.168.33.10:6379> GETBIT bm1 2
(integer) 1

# 二进制转十进制
192.168.33.10:6379> BITFIELD bm1 GET u2 0
1) (integer) 3
192.168.33.10:6379> BITFIELD bm1 GET u3 0
1) (integer) 7

# 找到第一个未签
192.168.33.10:6379> BITPOS bm1 0
(integer) 3

# 找到第一个有签
192.168.33.10:6379> BITPOS bm1 1
(integer) 0
```


# 实现签到功能

当前用户当天签到、补签

```bash 
192.168.33.10:6379> SETBIT sign:1011:202410 15 1
(integer) 0
```

```java 
package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.BeanUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();

        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key ,dayOfMonth - 1, true);

        return Result.ok();
    }

    @Override
    public Result resign(String yyyy, String MM, String dd) {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取当天日期
        LocalDateTime now = LocalDateTime.now();

        // 3.拼接key
        String keySuffix = ":" + yyyy + MM;
        String key = USER_SIGN_KEY + userId + keySuffix;

        // 4.获取今天是本月的第几天
        int nowdayOfMonth = now.getDayOfMonth();
        if( Integer.parseInt(dd) >= nowdayOfMonth){
            return Result.fail("无法补签");
        }

        int dayOfMonth = Integer.parseInt(dd);

        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key ,dayOfMonth - 1, true);

        return Result.ok();
    }

}

```
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
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    // 补签 SETBIT sign:1011:202410 15 1
    @PostMapping("/resign/{yyyy}/{MM}/{dd}")
    public Result resign(@PathVariable("yyyy") String yyyy,
                         @PathVariable("MM") String MM,
                         @PathVariable("dd") String dd){
        return userService.resign(yyyy,MM,dd);
    }
}

```
|请求头token|authorization|2468ffeb-0341-4a7d-a031-0bf78850a00e|
|--------| -------- | -------- | 
|签到|POST|http://localhost:8080/api/user/sign|
|补签|POST|http://localhost:8080/api/user/resign/2024/10/15|

# 统计连续签到


问题1:什么叫做连续签到天数?

从最后一次签到开始向前统计,直到遇到第一次未签到为止,计算总的签到次数,就是连续签到天数。

问题2:如何得到本月到今天为止的所有签到数据?

BITFIELD key GET u[dayOfMonth] 0

问题3:如何从后向前遍历每个bit位?

与1做与运算,就能得到最后一个bit位。

随后右移1位,下一个bit位就成为了最后一个bit位。

```bash 
192.168.33.10:6379> BITFIELD sign:1011:202410 GET u21 0
1) (integer) 1050625
```

```java 
package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.BeanUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();

        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5.获取本月截止今天为止的所有的签到记录,返回的是一个十进制的数字
        // BITFIELD sign:1011:202410 GET u21 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create() // 子命令 GET SET INCR...
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)) // 哪一天 = dayOfMonth , 有无符号 u = BitFieldSubCommands.BitFieldType.unsigned
                        .valueAt(0) // offset 从几开始
        );
        if(result == null || result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num == null || num ==0){
            return Result.ok(0);
        }

        // 6.循环遍历
        int count = 0;
        while (true){

        // 6.1.让这个数字1做与运算,得到数字的最后一个bit位
        // 判断这个bit位是否为
            if((num & 1) == 0){
                // 如果为0,说明未签到,结束
                break;
            }else{
                // 如果不为,说明已签到,计数器+1
                count++;
            }
            // 把数字右移一位,抛弃最后一个bit位,继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }
}
```

|请求头token|authorization|2468ffeb-0341-4a7d-a031-0bf78850a00e||
|--------| -------- | -------- | -------- | 
|补签|POST|http://localhost:8080/api/user/resign/2024/10/19||
|补签|POST|http://localhost:8080/api/user/resign/2024/10/20||
|补签|POST|http://localhost:8080/api/user/resign/2024/10/21||
|统计连续签到|GET|http://localhost:8080/api/user/sign/count|{"success": true,"data": 2}|



