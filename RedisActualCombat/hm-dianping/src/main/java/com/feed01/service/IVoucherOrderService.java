package com.feed01.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.feed01.dto.Result;
import com.feed01.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);
    Result seckillVoucher_Lua(Long voucherId);

    Result createVoucherOrder(Long voucherId);

    void createVoucherOrder_Lua(VoucherOrder voucherOrder);
    Result seckillVoucher_Lua2(Long voucherId);
}
