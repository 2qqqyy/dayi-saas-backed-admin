package com.qingmeili.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 新增/修改角色请求
 */
@Data
public class RoleCreateDTO {

    @NotBlank(message = "角色名称不能为空")
    private String name;

    private String roleCode;

    private List<Long> menuIds;

    private String dataScope;

    private Integer sort;
}
