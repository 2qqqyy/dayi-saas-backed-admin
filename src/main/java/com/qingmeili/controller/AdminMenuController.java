package com.qingmeili.controller;

import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.auth.MenuTreeVO;
import com.qingmeili.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理
 */
@RestController
@RequestMapping("/api/admin/menus")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminMenuController {

    private final MenuService menuService;

    /**
     * 获取完整菜单树
     */
    @GetMapping
    public Result<List<MenuTreeVO>> tree() {
        return menuService.getMenuTree();
    }

    /**
     * 获取当前登录角色的菜单树；未传 roleId 时返回完整菜单树
     */
    @GetMapping("/my")
    public Result<List<MenuTreeVO>> myMenus(@RequestParam(required = false) Long roleId) {
        if (roleId == null) {
            return menuService.getMenuTree();
        }
        return menuService.getMenuTreeByRoleId(roleId);
    }
}
