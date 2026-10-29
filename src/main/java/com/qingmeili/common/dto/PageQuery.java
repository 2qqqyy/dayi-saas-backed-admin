package com.qingmeili.common.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询基础参数
 */
@Data
public class PageQuery {
    @Min(value = 1, message = "页码最小为1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为1")
    private Integer pageSize = 10;
}
