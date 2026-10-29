package com.qingmeili.dto.auth;

import lombok.Data;

/**
 * 修改管理员请求（字段均可选，仅更新传入字段）
 */
@Data
public class AdminUpdateDTO {

    private String username;

    private String realName;

    private String mobile;

    /** 传空表示不修改密码 */
    private String password;

    private Long roleId;

    private Integer status;
}
