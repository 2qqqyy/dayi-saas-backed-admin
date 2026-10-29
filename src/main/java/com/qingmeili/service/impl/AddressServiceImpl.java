package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.user.AddressDTO;
import com.qingmeili.entity.Address;
import com.qingmeili.mapper.AddressMapper;
import com.qingmeili.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址服务实现
 */
@Service
@RequiredArgsConstructor
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements AddressService {

    @Override
    public Result<List<Address>> list(Long userId) {
        List<Address> list = baseMapper.selectList(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault)
                        .orderByDesc(Address::getCreateTime));
        return Result.ok(list);
    }

    @Override
    @Transactional
    public Result<Address> create(Long userId, AddressDTO dto) {
        // 如果设为默认，先取消其他默认
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            unsetDefault(userId);
        }
        Address address = new Address();
        address.setUserId(userId);
        address.setReceiver(dto.getReceiver());
        address.setMobile(dto.getMobile());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetail(dto.getDetail());
        address.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : 0);
        baseMapper.insert(address);
        return Result.ok("创建成功", address);
    }

    @Override
    public Result<Address> update(Long id, AddressDTO dto) {
        Address address = baseMapper.selectById(id);
        if (address == null) {
            throw new BusinessException("地址不存在");
        }
        if (dto.getReceiver() != null) address.setReceiver(dto.getReceiver());
        if (dto.getMobile() != null) address.setMobile(dto.getMobile());
        if (dto.getProvince() != null) address.setProvince(dto.getProvince());
        if (dto.getCity() != null) address.setCity(dto.getCity());
        if (dto.getDistrict() != null) address.setDistrict(dto.getDistrict());
        if (dto.getDetail() != null) address.setDetail(dto.getDetail());
        if (dto.getIsDefault() != null) address.setIsDefault(dto.getIsDefault());
        baseMapper.updateById(address);
        return Result.ok("修改成功", address);
    }

    @Override
    public Result<Void> delete(Long id) {
        Address address = baseMapper.selectById(id);
        if (address == null) {
            throw new BusinessException("地址不存在");
        }
        baseMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }

    @Override
    @Transactional
    public Result<Void> setDefault(Long id, Long userId) {
        Address address = baseMapper.selectById(id);
        if (address == null) {
            throw new BusinessException("地址不存在");
        }
        unsetDefault(userId);
        address.setIsDefault(1);
        baseMapper.updateById(address);
        return Result.ok("设置成功", null);
    }

    private void unsetDefault(Long userId) {
        Address defaultAddr = new Address();
        defaultAddr.setIsDefault(0);
        baseMapper.update(defaultAddr,
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .eq(Address::getIsDefault, 1));
    }
}
