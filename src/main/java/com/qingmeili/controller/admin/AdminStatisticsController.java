package com.qingmeili.controller.admin;

import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.Result;
import com.qingmeili.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台管理 - 统计分布
 */
@RestController
@RequestMapping("/api/admin/statistics")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminStatisticsController {

    private final DashboardService dashboardService;

    /** 订单状态分布 + 商品分类销量分布 */
    @GetMapping("/distribution")
    public Result<Map<String, Object>> distribution() {
        return dashboardService.distribution();
    }
}
