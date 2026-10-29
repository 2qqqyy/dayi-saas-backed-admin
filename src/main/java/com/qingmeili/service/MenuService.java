package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.auth.MenuTreeVO;
import com.qingmeili.entity.Menu;

import java.util.List;

/**
 * 菜单服务
 */
public interface MenuService extends IService<Menu> {

    /**
     * 获取菜单树
     */
    Result<List<MenuTreeVO>> getMenuTree();

    /**
     * 根据角色ID获取菜单树（仅该角色有权限的）
     */
    Result<List<MenuTreeVO>> getMenuTreeByRoleId(Long roleId);
}
