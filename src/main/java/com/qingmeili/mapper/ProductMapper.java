package com.qingmeili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingmeili.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
