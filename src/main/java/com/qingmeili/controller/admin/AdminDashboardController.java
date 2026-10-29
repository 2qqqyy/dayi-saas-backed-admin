package com.qingmeili.controller.admin;

import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.Result;
import com.qingmeili.service.DashboardService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 后台管理 - 仪表盘
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return dashboardService.overview();
    }

    /** 顶栏消息：按待办实时统计生成 */
    @GetMapping("/notifications")
    public Result<List<Map<String, Object>>> notifications() {
        return dashboardService.notifications();
    }

    /** 报表导出：CSV（带 BOM，Excel 双击即读）。type = orders / products / merchants / stats */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "orders") String type,
                                         @RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate) {
        DashboardService.ReportFile file = dashboardService.exportReport(type, startDate, endDate);
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"report.csv\"; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                .body(file.content());
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(
            @RequestParam(defaultValue = "sales") String metric,
            @RequestParam(defaultValue = "week") String range) {
        return dashboardService.trend(metric, range);
    }

    @GetMapping("/merchant-activity")
    public Result<List<Map<String, Object>>> merchantActivity(
            @RequestParam(defaultValue = "week") String range,
            @RequestParam(required = false) Integer limit) {
        return dashboardService.merchantActivity(range, limit);
    }
}
