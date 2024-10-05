package com.feed01.controller;


import com.feed01.dto.Result;
import com.feed01.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService iVoucherOrderService;
    @PostMapping("seckill/{id}") //优惠券Id
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
//        return  iVoucherOrderService.seckillVoucher(voucherId);
//        return  iVoucherOrderService.seckillVoucher_Lua(voucherId);
        return  iVoucherOrderService.seckillVoucher_Lua2(voucherId);
    }
}
