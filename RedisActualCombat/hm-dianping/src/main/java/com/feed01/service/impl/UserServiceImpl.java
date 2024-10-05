package com.feed01.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feed01.dto.LoginFormDTO;
import com.feed01.dto.Result;
import com.feed01.dto.UserDTO;
import com.feed01.entity.User;
import com.feed01.mapper.UserMapper;
import com.feed01.service.IUserService;
import com.feed01.utils.RedisConstants;
import com.feed01.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.feed01.utils.RedisConstants.LOGIN_USER_KEY;
import static com.feed01.utils.SystemConstants.USER_NICK_NAME_PREFIX;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

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
    public Result sendCode(String phone, HttpSession session) {
        // 效验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            // 如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }

        // 如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 保存验证码到session
//        session.setAttribute("code", code);

        // 保存验证码到redis set key vale ex 120
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code ,RedisConstants.LOGIN_CODE_TTL , TimeUnit.MINUTES);

        // 发送验证码
        log.debug("发送短信验证码成功 ,验证码:{}" ,code);
        // 返回ok
        return null;
    }

    @Override
    public Result login_redis(LoginFormDTO loginForm, HttpSession session) {
        // 效验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            // 如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 从redis 获取验证码并效验
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            // 不一致， 报错
            return Result.fail("验证码错误");
        }

        // 一致， 根据手机号查询用户
        User user = query().eq("phone" ,phone).one();
        // 判断用户是否存在
        if(user == null){
            // 不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        // 保存用户信息到redis
        // 随机生成 token 作为登陆令牌
        String token = UUID.randomUUID().toString();

        // 将 user 对象转为 Hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // Long to String
//        Map<String ,Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String ,Object> userMap = BeanUtil.beanToMap(userDTO ,new HashMap<>() ,
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName ,fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        // 存储 目前会是 从登入到30分钟会被踢出，但应该是 用户30分钟内不断访问就应该刷新存活时间
        stringRedisTemplate.opsForHash().putAll(tokenKey ,userMap);
        stringRedisTemplate.expire(tokenKey ,RedisConstants.CACHE_SHOP_TTL ,TimeUnit.MINUTES);
        // 返回 token
        return Result.ok(token);
    }


    @Override
    public Result login_session(LoginFormDTO loginForm, HttpSession session) {
        // 效验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            // 如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 效验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.toString().equals(code)){
            // 不一致， 报错
            return Result.fail("验证码错误");
        }

        // 一致， 根据手机号查询用户
        User user = query().eq("phone" ,phone).one();
        // 判断用户是否存在
        if(user == null){
            // 不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        // 保存用户信息到session
//        session.setAttribute("user" ,user);
        // 隐藏用户敏感信息
//        UserDTO userDTO = BeanUtils.copyProperties(user, UserDTO.class);
        session.setAttribute("user" , BeanUtil.copyProperties(user , UserDTO.class));
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        // 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        // select  * from tb_user order by create_time desc;
//        User user2 = query().eq("phone" ,phone).one();
//        log.info("user2={}",user2);
        return user;
    }
    @Override
    public Result loginCreateTestuser() {
        int howManyTestUser = 10;
        String filePath = "D:\\workspace\\write\\tokens.txt";
        List<String> list = new ArrayList<>();
        IntStream.rangeClosed(0, howManyTestUser).forEach(i -> {
            createTestuser(RandomUtil.randomNumbers(8) ,list);
        });
        tokenJMeterTest(filePath ,list);
        return Result.ok("done");
    }

    private void tokenJMeterTest(String txtFile ,List<String> list){
        try {
            // 检查并创建必要的目录
            File file = new File(txtFile);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs(); // 创建目录
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(txtFile))) {
                // 写入数据记录
                list.forEach(e -> {
                    try {
                        writer.write(e); // 写入每个元素
                        writer.newLine(); // 添加换行符
                    } catch (IOException ioException) {
                        log.error("写入时出错: {}", ioException.getMessage());
                    }
                });
                log.info("数据已成功写入文件。");
            }
        } catch (Exception e) {
            log.error("文件操作时出错: {}" , e.getMessage());
        }
    }

    private void createTestuser(String phone ,List<String> list){
        // 一致， 根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 判断用户是否存在
        if (user == null) {
            // 不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        // 保存用户信息到redis
        // 随机生成 token 作为登陆令牌
        String token = UUID.randomUUID().toString();
        list.add(token);
        // 将 user 对象转为 Hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // Long to String
        //        Map<String ,Object> userMap = BeanUtil.beanToMap(userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        // 存储 目前会是 从登入到30分钟会被踢出，但应该是 用户30分钟内不断访问就应该刷新存活时间
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, 30, TimeUnit.DAYS);
    }
}
