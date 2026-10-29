package com.qingmeili.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingmeili.entity.Product;
import com.qingmeili.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品定时上架：商家设置 on_shelf_time 后，到点自动把商品上架并清空定时。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductPublishTask {

    private final ProductMapper productMapper;

    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 30 * 1000)
    public void publishScheduled() {
        List<Product> due = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .isNotNull(Product::getOnShelfTime)
                        .le(Product::getOnShelfTime, LocalDateTime.now())
                        .eq(Product::getOnShelf, 0)
                        .eq(Product::getAuditStatus, 1));
        for (Product p : due) {
            // updateById 会忽略 null 字段，清空定时必须用 UpdateWrapper 显式 set null
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, p.getId())
                    .set(Product::getOnShelf, 1)
                    .set(Product::getOnShelfTime, null));
            log.info("定时上架商品 id={} name={}", p.getId(), p.getName());
        }
    }
}
