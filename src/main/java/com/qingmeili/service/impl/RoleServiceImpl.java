package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.auth.RoleCreateDTO;
import com.qingmeili.entity.Role;
import com.qingmeili.entity.RoleMenu;
import com.qingmeili.mapper.RoleMapper;
import com.qingmeili.mapper.RoleMenuMapper;
import com.qingmeili.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    private final RoleMenuMapper roleMenuMapper;

    @Override
    public Result<List<Role>> listAll() {
        List<Role> list = baseMapper.selectList(
                new LambdaQueryWrapper<Role>().orderByAsc(Role::getSort));
        return Result.ok(list);
    }

    @Override
    @Transactional
    public Result<Role> create(RoleCreateDTO dto) {
        // 角色编码唯一性
        String code = dto.getRoleCode();
        if (code == null || code.isEmpty()) {
            code = "ROLE_" + System.currentTimeMillis();
        }
        Long count = baseMapper.selectCount(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, code));
        if (count > 0) {
            throw new BusinessException("角色编码已存在");
        }

        Role role = new Role();
        role.setRoleName(dto.getName());
        role.setRoleCode(code);
        role.setDataScope(dto.getDataScope() != null ? dto.getDataScope() : "");
        role.setSort(dto.getSort() != null ? dto.getSort() : 0);
        role.setStatus(1);
        role.setDeleted(0);
        baseMapper.insert(role);

        // 分配菜单权限
        if (dto.getMenuIds() != null && !dto.getMenuIds().isEmpty()) {
            assignMenus(role.getId(), dto.getMenuIds());
        }

        return Result.ok("创建成功", role);
    }

    @Override
    @Transactional
    public Result<Role> update(Long id, RoleCreateDTO dto) {
        Role role = baseMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }

        if (dto.getName() != null) role.setRoleName(dto.getName());
        if (dto.getDataScope() != null) role.setDataScope(dto.getDataScope());
        if (dto.getSort() != null) role.setSort(dto.getSort());
        baseMapper.updateById(role);

        // 重新分配菜单权限
        if (dto.getMenuIds() != null) {
            assignMenus(id, dto.getMenuIds());
        }

        return Result.ok("修改成功", role);
    }

    @Override
    public Result<List<Long>> getRoleMenuIds(Long roleId) {
        List<RoleMenu> list = roleMenuMapper.selectList(
                new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        List<Long> menuIds = list.stream().map(RoleMenu::getMenuId).collect(Collectors.toList());
        return Result.ok(menuIds);
    }

    @Override
    @Transactional
    public Result<Void> assignMenus(Long roleId, List<Long> menuIds) {
        // 先清除旧关联
        roleMenuMapper.delete(
                new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        // 批量新增
        for (Long menuId : menuIds) {
            RoleMenu rm = new RoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
        return Result.ok("权限分配成功", null);
    }
}
