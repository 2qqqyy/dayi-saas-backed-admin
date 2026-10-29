package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("merchant")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long villageId;

    private String name;
    private String nameEn;

    private String avatar;

    private Integer type;

    private String contact;

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.qingmeili.common.util.MaskSerializers.Mobile.class)
    private String mobile;

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.qingmeili.common.util.MaskSerializers.IdCard.class)
    private String idCard;

    private String licenseUrl;

    private String photoUrls;

    private String bankName;

    private String accountName;

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.qingmeili.common.util.MaskSerializers.BankCard.class)
    private String accountNo;

    private String address;

    private String intro;

    private String businessScope;

    /** 营业时间，如 08:00-20:00 */
    private String openTime;

    /** 店铺公告（展示给游客） */
    private String shopNotice;

    /** 登录密码 BCrypt 哈希；为空时登录回退演示初始密码 123456 */
    private String passwordHash;

    private Integer auditStatus;

    private String rejectReason;

    private Integer rejectCount;

    private BigDecimal rating;

    private Integer status;

    /** 营业状态：1营业中 0暂停接单。与 status（后台禁用位）分离，商户自助切换不影响登录 */
    private Integer businessStatus;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
