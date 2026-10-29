package com.qingmeili.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户信息更新DTO
 */
@Data
public class UserUpdateDTO {

    @Size(max = 20, message = "昵称最长 20 个字符")
    private String nickname;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
