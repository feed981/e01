package com.feed01.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.feed01.dto.Result;
import com.feed01.entity.Follow;
import com.feed01.po.BlogTheUsersWhoFollow;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    List<BlogTheUsersWhoFollow> queryAllBlogTheUsersWhoFollow();
    void queryBlogIdTheUserWhoFollow(Long userId ,Long max);

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}
