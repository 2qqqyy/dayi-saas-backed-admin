package com.qingmeili.dto.auth;

import lombok.Data;

import java.util.List;

/**
 * 菜单树 VO
 */
@Data
public class MenuTreeVO {

    private Long id;
    private Long parentId;
    private String name;
    private String code;
    private Integer type;
    private String path;
    private String icon;
    private Integer sort;
    private Integer visible;
    private Integer status;
    private List<MenuTreeVO> children;
}
