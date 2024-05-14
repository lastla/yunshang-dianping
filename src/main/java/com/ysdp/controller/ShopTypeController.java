package com.ysdp.controller;


import com.ysdp.dto.Result;
import com.ysdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    /**
     * 查询店铺分类
     * @return
     */
    @GetMapping("list")
    public Result queryTypeList() {
        /*List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);*/
        return typeService.queryTypeList();
    }
}
