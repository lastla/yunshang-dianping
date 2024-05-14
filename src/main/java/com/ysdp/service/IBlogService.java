package com.ysdp.service;

import com.ysdp.dto.Result;
import com.ysdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    /**
     * 查询热点博客
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据id查询博客
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 查询当前用户是否点赞过，点赞过则不能继续点赞
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 根据博客id查询点赞用户top5
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存笔记
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);
    /**
     * 滚动分页查询关注的人的笔记
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogOfFollow(Long max, Long offset);
}
