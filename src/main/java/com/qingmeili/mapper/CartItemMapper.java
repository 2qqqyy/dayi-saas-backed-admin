package com.qingmeili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingmeili.entity.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 物理删除购物车行。
     * cart_item 上的 uk_user_sku(user_id,sku_id) 不含 deleted 列，
     * 走 @TableLogic 软删会让「删除后再加购同一 SKU」直接撞唯一键 500。
     * 同时带 user_id 条件，避免越权删别人的购物车。
     */
    @Delete("<script>DELETE FROM cart_item WHERE user_id = #{userId} AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int deleteOwned(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    @Delete("DELETE FROM cart_item WHERE user_id = #{userId} AND selected = 1")
    int deleteSelectedOwned(@Param("userId") Long userId);

    @Delete("DELETE FROM cart_item WHERE user_id = #{userId}")
    int deleteAllOwned(@Param("userId") Long userId);
}
