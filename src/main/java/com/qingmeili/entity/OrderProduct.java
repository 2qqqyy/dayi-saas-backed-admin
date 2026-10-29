package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 商品订单扩展表
 */
@Data
@TableName("order_product")
public class OrderProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long addressId;

    private String addressSnapshot;

    private String logisticsNo;

    private String logisticsCompany;

    private LocalDateTime shipTime;

    private LocalDateTime receiveTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
