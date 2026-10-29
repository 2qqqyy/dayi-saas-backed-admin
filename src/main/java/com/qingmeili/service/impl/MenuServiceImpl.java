package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.auth.MenuTreeVO;
import com.qingmeili.entity.Menu;
import com.qingmeili.entity.RoleMenu;
import com.qingmeili.mapper.MenuMapper;
import com.qingmeili.mapper.RoleMenuMapper;
import com.qingmeili.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务实现
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {

    private final RoleMenuMapper roleMenuMapper;

    @Override
    public Result<List<MenuTreeVO>> getMenuTree() {
        List<Menu> allMenus = baseMapper.selectList(
                new LambdaQueryWrapper<Menu>()
                        .eq(Menu::getStatus, 1)
                        .orderByAsc(Menu::getSort));
        List<MenuTreeVO> tree = buildTree(allMenus, 0L);
        return Result.ok(tree);
    }

    @Override
    public Result<List<MenuTreeVO>> getMenuTreeByRoleId(Long roleId) {
        // 查询该角色关联的菜单ID
        List<RoleMenu> roleMenus = roleMenuMapper.selectList(
                new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        Set<Long> menuIds = roleMenus.stream()
                .map(RoleMenu::getMenuId)
                .collect(Collectors.toSet());

        // 查询所有菜单
        List<Menu> allMenus = baseMapper.selectList(
                new LambdaQueryWrapper<Menu>()
                        .eq(Menu::getStatus, 1)
                        .orderByAsc(Menu::getSort));

        // 过滤出有权限的菜单（包含父级路径）
        List<Menu> filtered = allMenus.stream()
                .filter(m -> menuIds.contains(m.getId()))
                .collect(Collectors.toList());

        // 补充父级菜单
        Set<Long> parentIds = filtered.stream()
                .map(Menu::getParentId)
                .filter(pid -> pid != 0L && !menuIds.contains(pid))
                .collect(Collectors.toSet());
        if (!parentIds.isEmpty()) {
            List<Menu> parents = allMenus.stream()
                    .filter(m -> parentIds.contains(m.getId()))
                    .collect(Collectors.toList());
            filtered.addAll(parents);
        }

        List<MenuTreeVO> tree = buildTree(filtered, 0L);
        return Result.ok(tree);
    }

    /**
     * 递归构建菜单树
     */
    private List<MenuTreeVO> buildTree(List<Menu> menus, Long parentId) {
        List<MenuTreeVO> tree = new ArrayList<>();
        for (Menu menu : menus) {
            if (parentId.equals(menu.getParentId())) {
                MenuTreeVO vo = toVO(menu);
                vo.setChildren(buildTree(menus, menu.getId()));
                tree.add(vo);
            }
        }
        return tree;
    }

    private MenuTreeVO toVO(Menu menu) {
        MenuTreeVO vo = new MenuTreeVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setName(menu.getName());
        vo.setCode(menu.getCode());
        vo.setType(menu.getType());
        vo.setPath(menu.getPath());
        vo.setIcon(menu.getIcon());
        vo.setSort(menu.getSort());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        return vo;
    }
}
