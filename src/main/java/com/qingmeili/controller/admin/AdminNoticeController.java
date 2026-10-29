package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Notice;
import com.qingmeili.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 平台 - 公告管理。此前公告只能靠 SQL 种子维护，运营无法发布/下线。
 */
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminNoticeController {

    private final NoticeMapper noticeMapper;

    @GetMapping
    public Result<PageResult<Notice>> list(@RequestParam(required = false) String audience,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Notice> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(audience)) {
            wrapper.eq(Notice::getAudience, audience);
        }
        wrapper.orderByDesc(Notice::getPublishTime);
        Page<Notice> result = noticeMapper.selectPage(pageParam, wrapper);
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @PostMapping
    @OperationLog(module = "平台公告", action = "发布公告")
    public Result<Notice> create(@RequestBody Map<String, String> body) {
        Notice n = fill(new Notice(), body);
        n.setDeleted(0);
        if (n.getPublishTime() == null) {
            n.setPublishTime(LocalDateTime.now());
        }
        noticeMapper.insert(n);
        return Result.ok("发布成功", n);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "平台公告", action = "编辑公告")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Notice n = noticeMapper.selectById(id);
        if (n == null) {
            throw new BusinessException("公告不存在");
        }
        fill(n, body);
        noticeMapper.updateById(n);
        return Result.ok("已保存", null);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "平台公告", action = "删除公告")
    public Result<Void> delete(@PathVariable Long id) {
        noticeMapper.deleteById(id);
        return Result.ok("已删除", null);
    }

    private Notice fill(Notice n, Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            throw new BusinessException("公告标题不能为空");
        }
        n.setTitle(title.trim());
        n.setTitleEn(body.get("titleEn"));
        n.setSummary(body.get("summary"));
        n.setSummaryEn(body.get("summaryEn"));
        n.setContent(body.get("content"));
        n.setContentEn(body.get("contentEn"));
        n.setCategory(StringUtils.hasText(body.get("category")) ? body.get("category") : "NOTICE");
        n.setAudience(StringUtils.hasText(body.get("audience")) ? body.get("audience") : "ALL");
        n.setStatus(StringUtils.hasText(body.get("status")) ? Integer.parseInt(body.get("status")) : 1);
        return n;
    }
}
