package com.qingmeili.controller;

import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.auth.RoleCreateDTO;
import com.qingmeili.entity.Role;
import com.qingmeili.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleService roleService;

    @GetMapping
    public Result<List<Role>> list() {
        return roleService.listAll();
    }

    @PostMapping
    public Result<Role> create(@Valid @RequestBody RoleCreateDTO dto) {
        return roleService.create(dto);
    }

    @PutMapping("/{id}")
    public Result<Role> update(@PathVariable Long id, @Valid @RequestBody RoleCreateDTO dto) {
        return roleService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return Result.fail("角色不存在");
        }
        if ("super_admin".equals(role.getRoleCode())) {
            return Result.fail("超级管理员角色不可删除");
        }
        roleService.removeById(id);
        return Result.ok("删除成功", null);
    }

    @GetMapping("/{id}/menus")
    public Result<List<Long>> getRoleMenuIds(@PathVariable Long id) {
        return roleService.getRoleMenuIds(id);
    }

    @PostMapping("/{id}/menus")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        return roleService.assignMenus(id, menuIds);
    }
}
