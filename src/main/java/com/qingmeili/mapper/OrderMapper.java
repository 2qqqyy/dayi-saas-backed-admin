package com.qingmeili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingmeili.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /** 订单状态分布：status → 数量（仅返回存在的状态） */
    @Select("SELECT status AS name, COUNT(*) AS value FROM `order` WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> selectStatusDist();
}
