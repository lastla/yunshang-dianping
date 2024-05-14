package com.ysdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ysdp.dto.Result;
import com.ysdp.entity.ShopType;
import com.ysdp.mapper.ShopTypeMapper;
import com.ysdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ysdp.utils.RedisConstants.SHOP_TYPE_LIST_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;
    /**
     * 查询店铺分类
     * @return
     */
    @Override
    public Result queryTypeList() {
        //1.查redis
        String jsonShopList = stringRedisTemplate.opsForValue().get(SHOP_TYPE_LIST_KEY);
        if (StrUtil.isNotBlank(jsonShopList)) {
            //命中
            List<ShopType> list = JSONUtil.toList(jsonShopList, ShopType.class);
            return Result.ok(list);
        }
        //未命中,查数据库
        List<ShopType> typeList = this.query().orderByAsc("sort").list();
        if(typeList==null || typeList.size()==0){
            //未命中
            return Result.fail("查询店铺分类失败");
        }
        //命中，存入Redis,返回数据
        stringRedisTemplate.opsForValue().set(SHOP_TYPE_LIST_KEY,JSONUtil.toJsonStr(typeList),
                30L, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
