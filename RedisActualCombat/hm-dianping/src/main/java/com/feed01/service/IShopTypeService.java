package com.feed01.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.feed01.dto.Result;
import com.feed01.entity.ShopType;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList() ;
}
