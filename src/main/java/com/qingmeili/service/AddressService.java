package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.user.AddressDTO;
import com.qingmeili.entity.Address;

import java.util.List;

/**
 * 收货地址服务
 */
public interface AddressService extends IService<Address> {

    Result<List<Address>> list(Long userId);

    Result<Address> create(Long userId, AddressDTO dto);

    Result<Address> update(Long id, AddressDTO dto);

    Result<Void> delete(Long id);

    Result<Void> setDefault(Long id, Long userId);
}
