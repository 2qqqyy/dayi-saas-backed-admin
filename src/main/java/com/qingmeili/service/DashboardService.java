package com.qingmeili.service;

import com.qingmeili.common.result.Result;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘服务
 */
public interface DashboardService {

    Result<Map<String, Object>> overview();

    Result<List<Map<String, Object>>> trend(String metric, String range);

    Result<List<Map<String, Object>>> merchantActivity(String range, Integer limit);

    /** 统计分布：订单状态分布 + 商品分类销量分布 */
    Result<Map<String, Object>> distribution();

    /** 顶栏消息：按待办实时统计生成，没有事项的类型不出现 */
    Result<List<Map<String, Object>>> notifications();

    /**
     * 报表导出。
     *
     * @param type      orders / products / merchants / stats
     * @param startDate yyyy-MM-dd，可空
     * @param endDate   yyyy-MM-dd，可空
     */
    ReportFile exportReport(String type, String startDate, String endDate);

    /** 导出文件载体：文件名 + 字节内容（CSV 带 BOM，Excel 双击即读，无需额外依赖） */
    record ReportFile(String filename, byte[] content) {
    }
}
