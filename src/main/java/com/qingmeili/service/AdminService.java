package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.auth.AdminCreateDTO;
import com.qingmeili.entity.Admin;

/**
 * 管理员服务
 */
public interface AdminService extends IService<Admin> {

    /**
     * 分页查询管理员列表
     */
    Result<PageResult<Admin>> page(Integer page, Integer pageSize);

    /**
     * 新增管理员
     */
    Result<Admin> create(AdminCreateDTO dto);

    /**
     * 修改管理员
     */
    Result<Admin> update(Long id, AdminCreateDTO dto);

    /**
     * 删除管理员（逻辑删除）
     */
    Result<Void> delete(Long id);

    /**
     * 获取管理员信息（含角色）
     */
    Result<Admin> getAdminInfo(Long adminId);
}
