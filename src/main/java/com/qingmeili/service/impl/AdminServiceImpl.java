package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.common.util.PasswordUtil;
import com.qingmeili.dto.auth.AdminCreateDTO;
import com.qingmeili.entity.Admin;
import com.qingmeili.entity.UserRole;
import com.qingmeili.mapper.AdminMapper;
import com.qingmeili.mapper.UserRoleMapper;
import com.qingmeili.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl extends ServiceImpl<AdminMapper, Admin> implements AdminService {

    private final UserRoleMapper userRoleMapper;

    @Override
    public Result<PageResult<Admin>> page(Integer page, Integer pageSize) {
        Page<Admin> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        Page<Admin> result = baseMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Admin>().orderByDesc(Admin::getCreateTime));
        // 清除密码哈希
        result.getRecords().forEach(a -> a.setPasswordHash(null));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    @Transactional
    public Result<Admin> create(AdminCreateDTO dto) {
        // 检查用户名唯一
        Long count = baseMapper.selectCount(
                new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        Admin admin = new Admin();
        admin.setUsername(dto.getUsername());
        admin.setPasswordHash(PasswordUtil.encode(dto.getPassword()));
        admin.setRealName(dto.getRealName() != null ? dto.getRealName() : "");
        admin.setMobile(dto.getMobile() != null ? dto.getMobile() : "");
        admin.setStatus(1);
        admin.setLoginFailCount(0);
        admin.setDeleted(0);
        baseMapper.insert(admin);

        // 关联角色
        UserRole userRole = new UserRole();
        userRole.setUserId(admin.getId());
        userRole.setRoleId(dto.getRoleId());
        userRoleMapper.insert(userRole);

        admin.setPasswordHash(null);
        return Result.ok("创建成功", admin);
    }

    @Override
    @Transactional
    public Result<Admin> update(Long id, AdminCreateDTO dto) {
        Admin admin = baseMapper.selectById(id);
        if (admin == null) {
            throw new BusinessException("管理员不存在");
        }

        if (dto.getRealName() != null) admin.setRealName(dto.getRealName());
        if (dto.getMobile() != null) admin.setMobile(dto.getMobile());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            admin.setPasswordHash(PasswordUtil.encode(dto.getPassword()));
        }
        baseMapper.updateById(admin);

        // 更新角色
        if (dto.getRoleId() != null) {
            userRoleMapper.delete(
                    new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
            UserRole userRole = new UserRole();
            userRole.setUserId(id);
            userRole.setRoleId(dto.getRoleId());
            userRoleMapper.insert(userRole);
        }

        admin.setPasswordHash(null);
        return Result.ok("修改成功", admin);
    }

    @Override
    @Transactional
    public Result<Void> delete(Long id) {
        Admin admin = baseMapper.selectById(id);
        if (admin == null) {
            throw new BusinessException("管理员不存在");
        }
        baseMapper.deleteById(id);
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        return Result.ok("删除成功", null);
    }

    @Override
    public Result<Admin> getAdminInfo(Long adminId) {
        Admin admin = baseMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException("管理员不存在");
        }
        admin.setPasswordHash(null);
        return Result.ok(admin);
    }
}
