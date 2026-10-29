package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.auth.RoleCreateDTO;
import com.qingmeili.entity.Role;

import java.util.List;

/**
 * 角色服务
 */
public interface RoleService extends IService<Role> {

    /**
     * 查询所有角色
     */
    Result<List<Role>> listAll();

    /**
     * 新增角色（含菜单权限分配）
     */
    Result<Role> create(RoleCreateDTO dto);

    /**
     * 修改角色
     */
    Result<Role> update(Long id, RoleCreateDTO dto);

    /**
     * 查询角色已分配的菜单ID列表
     */
    Result<List<Long>> getRoleMenuIds(Long roleId);

    /**
     * 分配角色权限（菜单ID列表）
     */
    Result<Void> assignMenus(Long roleId, List<Long> menuIds);
}
