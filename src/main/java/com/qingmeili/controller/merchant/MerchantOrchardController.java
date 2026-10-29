package com.qingmeili.controller.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Orchard;
import com.qingmeili.mapper.OrchardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商户端 - 我的采摘园
 *
 * 商家端 H5 创建采摘档期时只知道登录身份，不知道 orchardId，
 * 此前只能在前端硬编码兜底为 1，导致档期挂到别的商户果园上。
 */
@RestController
@RequestMapping("/api/merchant/orchards")
@RequiredArgsConstructor
@RequireRole({"merchant"})
public class MerchantOrchardController {

    private final OrchardMapper orchardMapper;

    @GetMapping
    public Result<List<Orchard>> myOrchards() {
        List<Orchard> list = orchardMapper.selectList(
                new LambdaQueryWrapper<Orchard>()
                        .eq(Orchard::getMerchantId, UserContext.getMerchantId())
                        .orderByAsc(Orchard::getId));
        return Result.ok(list);
    }
}
