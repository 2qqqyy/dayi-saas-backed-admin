package com.qingmeili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingmeili.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /**
     * 分类销量分布：order_item 关联 product → product_category，
     * 子分类归并到其顶级分类（parent_id > 0 时取父分类名），按销量倒序
     */
    @Select("""
            SELECT COALESCE(parent.name, c.name) AS name, SUM(oi.quantity) AS value
            FROM order_item oi
            JOIN `order` o ON o.id = oi.order_id AND o.deleted = 0
            JOIN product p ON p.id = oi.product_id
            JOIN product_category c ON c.id = p.category_id
            LEFT JOIN product_category parent ON parent.id = c.parent_id AND c.parent_id > 0
            WHERE oi.deleted = 0
            GROUP BY COALESCE(parent.name, c.name)
            ORDER BY value DESC
            """)
    List<Map<String, Object>> selectCategorySalesDist();
}
