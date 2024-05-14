package com.ysdp.service;

import com.ysdp.dto.Result;
import com.ysdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注 or 取关
     * @param followUerId
     * @param isFollow
     * @return
     */
    Result follow(Long followUerId, Boolean isFollow);

    /**
     * 判断是否关注
     * @param followUserId
     * @return
     */
    Result isFollow(Long followUserId);

    /**
     * 查询共同关注
     * @param id
     * @return
     */
    Result followCommons(Long id);
}
