package com.qingmeili.controller.api;

import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Scenic;
import com.qingmeili.service.ScenicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * C端 - 景点
 */
@RestController
@RequestMapping("/api/scenic")
@RequiredArgsConstructor
public class ScenicController {

    private final ScenicService scenicService;

    @GetMapping
    public Result<PageResult<Scenic>> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return scenicService.page(page, pageSize, keyword, category);
    }

    @GetMapping("/{id}")
    public Result<Scenic> getDetail(@PathVariable Long id) {
        return scenicService.getDetail(id);
    }
}
