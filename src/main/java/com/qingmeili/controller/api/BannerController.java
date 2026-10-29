package com.qingmeili.controller.api;

import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Banner;
import com.qingmeili.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C端 - 轮播图（公开接口）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @GetMapping("/banners")
    public Result<List<Banner>> list() {
        return bannerService.listAll();
    }
}
