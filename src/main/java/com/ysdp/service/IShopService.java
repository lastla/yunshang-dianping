package com.ysdp.service;

import com.ysdp.dto.Result;
import com.ysdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {
    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    Result queryById(Long id);

    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    Result updateShop(Shop shop);

    /**
     * 根据商铺类型分页查询商铺信息 或根据地理坐标查询
     * @param typeId
     * @param current
     * @param x 经度
     * @param y 纬度
     * @return
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
