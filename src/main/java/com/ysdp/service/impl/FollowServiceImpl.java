package com.ysdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ysdp.dto.Result;
import com.ysdp.dto.UserDTO;
import com.ysdp.entity.Follow;
import com.ysdp.mapper.FollowMapper;
import com.ysdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ysdp.service.IUserService;
import com.ysdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;
    /**
     * 关注 or 取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1.获取登录用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" +userId;
        //2.判断关注还是取关
        if(isFollow){
            //关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            //将用户的关注添加到redis集合中，便于后续求交集实现共同关注功能
            if(isSuccess){

                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }

        }else{
            //取关
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if(isSuccess){
                //把关注的用户id从redis集合中移除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }

        }
        return Result.ok();
    }

    /**
     * 判断是否关注
     * @param followUerId
     * @return
     */
    @Override
    public Result isFollow(Long followUerId) {
        Long userId = UserHolder.getUser().getId();
        //1.查询是否关注
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", followUerId).count();

        return Result.ok(count>0);
    }
    /**
     * 查询共同关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //1.获取当前用户id
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" +userId;
        String key2 = "follows:" +id;
        //2.求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        //判断交集是否为空
        if(intersect==null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //将共同id集合转为long型
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户并封装到userDto中
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
