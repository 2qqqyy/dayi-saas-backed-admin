package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.product.ProductDTO;
import com.qingmeili.entity.Product;
import com.qingmeili.entity.ProductSku;

import java.util.List;
import java.util.Map;

/**
 * 商品服务
 */
public interface ProductService extends IService<Product> {

    /** C端分页：categoryIds 传多个时按 IN 匹配（父分类会带上子分类），null/空 表示不限分类 */
    Result<PageResult<Map<String, Object>>> page(Integer page, Integer pageSize, List<Long> categoryIds, String keyword, Integer onShelf);

    /** 商户端分页：强制按 merchantId 隔离 */
    Result<PageResult<Map<String, Object>>> pageByMerchant(Long merchantId, Integer page, Integer pageSize, Long categoryId, String keyword, Integer onShelf);

    Result<Map<String, Object>> getDetail(Long id);

    Result<Product> create(ProductDTO dto);

    Result<Product> update(Long id, ProductDTO dto);

    Result<Void> updateStatus(Long id, Integer onShelf);

    Result<Void> audit(Long id, String action, String reason);
}
