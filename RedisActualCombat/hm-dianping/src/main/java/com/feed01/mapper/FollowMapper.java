package com.feed01.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feed01.entity.Follow;
import com.feed01.po.BlogTheUsersWhoFollow;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {
    List<BlogTheUsersWhoFollow> queryAllBlogTheUsersWhoFollow();
    List<Long> queryBlogIdTheUserWhoFollow(@Param("userId") Long userId);
}
