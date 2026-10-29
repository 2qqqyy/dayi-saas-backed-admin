package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Guide;
import com.qingmeili.mapper.GuideMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C端 - 攻略文章（公开接口）。后台「内容管理-攻略」编辑后此处即时生效。
 */
@RestController
@RequestMapping("/api/guides")
@RequiredArgsConstructor
public class GuideController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final GuideMapper guideMapper;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = new ArrayList<>();
        guideMapper.selectList(new LambdaQueryWrapper<Guide>()
                        .eq(Guide::getStatus, 1)
                        .orderByDesc(Guide::getPublishTime))
                .forEach(g -> list.add(brief(g)));
        return Result.ok(list);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Guide guide = guideMapper.selectById(id);
        if (guide == null || guide.getStatus() == null || guide.getStatus() != 1) {
            return Result.fail("攻略不存在或已下架");
        }
        Guide inc = new Guide();
        inc.setId(id);
        inc.setViewCount((guide.getViewCount() == null ? 0 : guide.getViewCount()) + 1);
        guideMapper.updateById(inc);

        Map<String, Object> data = brief(guide);
        data.put("content", guide.getContent() == null ? "" : guide.getContent());
        return Result.ok(data);
    }

    private Map<String, Object> brief(Guide g) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", g.getId());
        item.put("title", g.getTitle());
        item.put("titleEn", g.getTitleEn());
        item.put("author", g.getAuthor());
        item.put("cover", g.getCover());
        item.put("viewCount", g.getViewCount() == null ? 0 : g.getViewCount());
        item.put("publishTime", g.getPublishTime() == null ? "" : g.getPublishTime().format(TIME));
        return item;
    }
}
