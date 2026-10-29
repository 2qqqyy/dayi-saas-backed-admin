package com.qingmeili.dto.review;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评价DTO
 */
@Data
public class ReviewDTO {

    @NotNull(message = "评分不能为空")
    private Integer score;

    private String content;

    private String images;
}
