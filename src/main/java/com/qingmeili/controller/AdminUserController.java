package com.qingmeili.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.auth.AdminCreateDTO;
import com.qingmeili.dto.auth.AdminUpdateDTO;
import com.qingmeili.entity.Admin;
import com.qingmeili.entity.Role;
import com.qingmeili.entity.UserRole;
import com.qingmeili.mapper.AdminMapper;
import com.qingmeili.mapper.RoleMapper;
import com.qingmeili.mapper.UserRoleMapper;
import com.qingmeili.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员账号管理
 */
@RestController
@RequestMapping("/api/admin/users")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminService adminService;
    private final AdminMapper adminMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    /** 列表：附带角色名，供管理后台直接展示 */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Admin> pageParam = new Page<>(page, pageSize);
        Page<Admin> result = adminMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Admin>().orderByDesc(Admin::getCreateTime));

        Map<Long, String> roleNames = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(Role::getId, Role::getRoleName, (a, b) -> a));
        List<UserRole> userRoles = userRoleMapper.selectList(null);
        Map<Long, Long> adminRoleMap = userRoles.stream()
                .filter(ur -> roleNames.containsKey(ur.getRoleId()))
                .collect(Collectors.toMap(UserRole::getUserId, UserRole::getRoleId, (a, b) -> a));

        List<Map<String, Object>> list = result.getRecords().stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("username", a.getUsername());
            m.put("nickname", a.getRealName());
            m.put("mobile", a.getMobile());
            Long roleId = adminRoleMap.get(a.getId());
            m.put("roleId", roleId);
            m.put("role", roleId != null ? roleNames.getOrDefault(roleId, "普通管理员") : "普通管理员");
            m.put("status", a.getStatus());
            m.put("lastLoginAt", a.getUpdateTime());
            m.put("createdAt", a.getCreateTime());
            return m;
        }).toList();
        return Result.ok(PageResult.of(list, result.getTotal(), page, pageSize));
    }

    @PostMapping
    public Result<Admin> create(@Valid @RequestBody AdminCreateDTO dto) {
        return adminService.create(dto);
    }

    @PutMapping("/{id}")
    public Result<Admin> update(@PathVariable Long id, @RequestBody AdminUpdateDTO dto) {
        return adminService.update(id, toCreateDTO(dto));
    }

    /** 启用/禁用：status 1启用 0禁用 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Admin admin = adminMapper.selectById(id);
        if (admin == null) {
            throw new BusinessException("管理员不存在");
        }
        admin.setStatus(status);
        adminMapper.updateById(admin);
        return Result.ok("状态更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return adminService.delete(id);
    }

    private AdminCreateDTO toCreateDTO(AdminUpdateDTO dto) {
        AdminCreateDTO target = new AdminCreateDTO();
        target.setUsername(dto.getUsername());
        target.setPassword(dto.getPassword());
        target.setRealName(dto.getRealName());
        target.setMobile(dto.getMobile());
        target.setRoleId(dto.getRoleId());
        return target;
    }
}
