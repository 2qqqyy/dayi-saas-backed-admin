package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Activity;
import com.qingmeili.entity.Guide;
import com.qingmeili.mapper.ActivityMapper;
import com.qingmeili.mapper.GuideMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台管理 - 内容运营（活动 / 攻略）
 */
@RestController
@RequestMapping("/api/admin")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminContentController {

    private final ActivityMapper activityMapper;
    private final GuideMapper guideMapper;

    /* ---------------- 活动 ---------------- */

    @GetMapping("/activities")
    public Result<List<Activity>> activities() {
        return Result.ok(activityMapper.selectList(
                new LambdaQueryWrapper<Activity>().orderByDesc(Activity::getCreateTime)));
    }

    @PostMapping("/activities")
    public Result<Activity> createActivity(@RequestBody Activity activity) {
        if (!StringUtils.hasText(activity.getTitle())) throw new BusinessException("活动标题不能为空");
        if (activity.getStatus() == null) activity.setStatus(0);
        if (activity.getJoinCount() == null) activity.setJoinCount(0);
        activityMapper.insert(activity);
        return Result.ok("创建成功", activity);
    }

    @PutMapping("/activities/{id}")
    public Result<Void> updateActivity(@PathVariable Long id, @RequestBody Activity body) {
        Activity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException("活动不存在");
        if (body.getTitle() != null) a.setTitle(body.getTitle());
        if (body.getCover() != null) a.setCover(body.getCover());
        if (body.getStartTime() != null) a.setStartTime(body.getStartTime());
        if (body.getEndTime() != null) a.setEndTime(body.getEndTime());
        if (body.getSort() != null) a.setSort(body.getSort());
        if (body.getStatus() != null) a.setStatus(body.getStatus());
        activityMapper.updateById(a);
        return Result.ok("更新成功", null);
    }

    /** 活动状态：0草稿 1上线 2已结束 */
    @PutMapping("/activities/{id}/status")
    public Result<Void> updateActivityStatus(@PathVariable Long id, @RequestParam Integer status) {
        Activity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException("活动不存在");
        a.setStatus(status);
        activityMapper.updateById(a);
        return Result.ok("状态更新成功", null);
    }

    @DeleteMapping("/activities/{id}")
    public Result<Void> deleteActivity(@PathVariable Long id) {
        Activity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException("活动不存在");
        activityMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }

    /* ---------------- 攻略 ---------------- */

    @GetMapping("/guides")
    public Result<List<Guide>> guides() {
        return Result.ok(guideMapper.selectList(
                new LambdaQueryWrapper<Guide>().orderByDesc(Guide::getCreateTime)));
    }

    @PostMapping("/guides")
    public Result<Guide> createGuide(@RequestBody Guide guide) {
        if (!StringUtils.hasText(guide.getTitle())) throw new BusinessException("攻略标题不能为空");
        if (guide.getStatus() == null) guide.setStatus(0);
        if (guide.getViewCount() == null) guide.setViewCount(0);
        guideMapper.insert(guide);
        return Result.ok("创建成功", guide);
    }

    @PutMapping("/guides/{id}")
    public Result<Void> updateGuide(@PathVariable Long id, @RequestBody Guide body) {
        Guide g = guideMapper.selectById(id);
        if (g == null) throw new BusinessException("攻略不存在");
        if (body.getTitle() != null) g.setTitle(body.getTitle());
        if (body.getAuthor() != null) g.setAuthor(body.getAuthor());
        if (body.getCover() != null) g.setCover(body.getCover());
        if (body.getContent() != null) g.setContent(body.getContent());
        if (body.getStatus() != null) {
            g.setStatus(body.getStatus());
            if (body.getStatus() == 1 && g.getPublishTime() == null) g.setPublishTime(LocalDateTime.now());
        }
        guideMapper.updateById(g);
        return Result.ok("更新成功", null);
    }

    /** 攻略状态：0草稿 1已发布 2已下架 */
    @PutMapping("/guides/{id}/status")
    public Result<Void> updateGuideStatus(@PathVariable Long id, @RequestParam Integer status) {
        Guide g = guideMapper.selectById(id);
        if (g == null) throw new BusinessException("攻略不存在");
        g.setStatus(status);
        if (status == 1 && g.getPublishTime() == null) g.setPublishTime(LocalDateTime.now());
        guideMapper.updateById(g);
        return Result.ok("状态更新成功", null);
    }

    @DeleteMapping("/guides/{id}")
    public Result<Void> deleteGuide(@PathVariable Long id) {
        Guide g = guideMapper.selectById(id);
        if (g == null) throw new BusinessException("攻略不存在");
        guideMapper.deleteById(id);
        return Result.ok("删除成功", null);
    }
}
