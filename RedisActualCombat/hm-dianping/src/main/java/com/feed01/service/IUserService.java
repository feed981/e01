package com.feed01.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.feed01.dto.LoginFormDTO;
import com.feed01.dto.Result;
import com.feed01.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login_session(LoginFormDTO loginForm, HttpSession session);
    Result login_redis(LoginFormDTO loginForm, HttpSession session);

    Result loginCreateTestuser();
}
