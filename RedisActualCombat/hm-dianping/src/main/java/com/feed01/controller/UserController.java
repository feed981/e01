package com.feed01.controller;

import com.feed01.dto.LoginFormDTO;
import com.feed01.dto.Result;
import com.feed01.dto.UserDTO;
import com.feed01.service.IUserService;
import com.feed01.utils.UserHolder;
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

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone ,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // TODO 实现登录功能
        return userService.login_redis(loginForm ,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        // TODO 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        log.info("/me user={}",user);
        return Result.ok(user);
    }

    @GetMapping("/login-createTestuser")
    public Result loginCreateTestuser(){
        return userService.loginCreateTestuser();
    }

}
