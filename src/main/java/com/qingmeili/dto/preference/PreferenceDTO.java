package com.qingmeili.dto.preference;

import lombok.Data;

/**
 * 显示偏好保存请求
 */
@Data
public class PreferenceDTO {

    /** 主题编码，见 GET /api/common/appearance */
    private String themeCode;

    /** 长辈模式 */
    private Boolean elderMode;
}
