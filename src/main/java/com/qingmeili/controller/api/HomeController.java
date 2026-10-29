package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qingmeili.common.result.Result;
import com.qingmeili.common.util.ContentStore;
import com.qingmeili.entity.Banner;
import com.qingmeili.entity.Guide;
import com.qingmeili.mapper.BannerMapper;
import com.qingmeili.mapper.GuideMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * C端 - 首页内容配置（公开接口）。
 * 版式为静态设计常量；轮播与攻略读 banner / guide 表，后台编辑后即时生效。
 */
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ContentStore contentStore;
    private final BannerMapper bannerMapper;
    private final GuideMapper guideMapper;
    private final ObjectMapper objectMapper;

    @GetMapping("/config")
    public Result<JsonNode> config() {
        JsonNode base = contentStore.getHomeConfig();
        ObjectNode node = base.isObject()
                ? (ObjectNode) base.deepCopy()
                : objectMapper.createObjectNode();

        var banners = node.putArray("banners");
        LocalDateTime now = LocalDateTime.now();
        bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, 1)
                        // 后台配的投放起止时间对 C 端真实生效，留空视为不限
                        .and(w -> w.isNull(Banner::getStartTime).or().le(Banner::getStartTime, now))
                        .and(w -> w.isNull(Banner::getEndTime).or().ge(Banner::getEndTime, now))
                        .orderByAsc(Banner::getSort)
                        .orderByAsc(Banner::getId))
                .forEach(b -> {
                    ObjectNode item = banners.addObject();
                    item.put("id", b.getId());
                    item.put("title", nz(b.getTitle()));
                    item.put("titleEn", nz(b.getTitleEn()));
                    item.put("image", nz(b.getImage()));
                    item.put("linkType", nz(b.getLinkType()));
                    item.put("linkUrl", nz(b.getLinkUrl()));
                });

        var guides = node.putArray("guides");
        guideMapper.selectList(new LambdaQueryWrapper<Guide>()
                        .eq(Guide::getStatus, 1)
                        .orderByDesc(Guide::getPublishTime)
                        .last("limit 4"))
                .forEach(g -> {
                    ObjectNode item = guides.addObject();
                    item.put("id", g.getId());
                    item.put("title", nz(g.getTitle()));
                    item.put("titleEn", nz(g.getTitleEn()));
                    item.put("author", nz(g.getAuthor()));
                    item.put("cover", nz(g.getCover()));
                    item.put("viewCount", g.getViewCount() == null ? 0 : g.getViewCount());
                    item.put("publishTime", g.getPublishTime() == null ? "" : g.getPublishTime().format(TIME));
                });

        node.put("serverTime", LocalDateTime.now().format(TIME));
        return Result.ok(node);
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }
}
