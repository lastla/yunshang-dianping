package com.ysdp.service;

import com.ysdp.dto.Result;
import com.ysdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
    /**
     * 优惠券秒杀功能
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);
    //同步下单
   // Result creatVoucherOrder(long voucherId);
    //异步下单
    void creatVoucherOrder(VoucherOrder voucherOrder);
}
