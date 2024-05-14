package com.ysdp.service;

import com.ysdp.dto.Result;
import com.ysdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {
    /**
     * 查询店铺分类
     * @return
     */
    Result queryTypeList();
}
