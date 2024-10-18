package com.feed01.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.feed01.dto.Result;
import com.feed01.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Long id);

    Result queryHotBlog(Integer current);

    Result likeBlog(Long id);
    Result likeBlog_zset(Long id);

    Result queryBlogLikes(Long id);

    Result saveBolg(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);

}
