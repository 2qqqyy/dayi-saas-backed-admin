package com.qingmeili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingmeili.entity.Favorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 物理删除收藏。
     * favorite 上有 uk_user_target(user_id,target_id,type) 唯一索引，且不带 deleted 列；
     * 走 @TableLogic 的软删会让「取消收藏后再次收藏」撞上唯一键直接 500。
     */
    @Delete("DELETE FROM favorite WHERE id = #{id} AND user_id = #{userId}")
    int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);
}
