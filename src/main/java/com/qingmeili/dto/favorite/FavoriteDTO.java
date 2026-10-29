package com.qingmeili.dto.favorite;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 收藏DTO
 */
@Data
public class FavoriteDTO {

    @NotNull(message = "收藏对象ID不能为空")
    private Long targetId;

    private String type;

    private String title;

    private String subtitle;

    private String image;
}
