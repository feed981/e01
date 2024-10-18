package com.feed01.po;

import lombok.Data;

import java.io.Serializable;

@Data
public class BlogTheUsersWhoFollow implements Serializable {
    private Long id; // 博文id
    private Long followUserId; //tb_blog 博客
    private Long userId; // 粉丝
}