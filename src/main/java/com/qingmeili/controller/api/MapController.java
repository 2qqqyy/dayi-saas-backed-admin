package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Homestay;
import com.qingmeili.entity.Orchard;
import com.qingmeili.entity.Scenic;
import com.qingmeili.mapper.HomestayMapper;
import com.qingmeili.mapper.OrchardMapper;
import com.qingmeili.mapper.ScenicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * C端 - 地图标记点
 */
@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class MapController {

    private final OrchardMapper orchardMapper;
    private final HomestayMapper homestayMapper;
    private final ScenicMapper scenicMapper;

    @GetMapping("/markers")
    public Result<List<Map<String, Object>>> markers(@RequestParam(required = false) String category) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (category == null || "orchard".equals(category)) {
            LambdaQueryWrapper<Orchard> orchardWrapper = new LambdaQueryWrapper<>();
            orchardWrapper.eq(Orchard::getStatus, 1)
                          .isNotNull(Orchard::getLat)
                          .isNotNull(Orchard::getLng);
            List<Orchard> orchards = orchardMapper.selectList(orchardWrapper);
            for (Orchard o : orchards) {
                Map<String, Object> marker = new HashMap<>();
                marker.put("id", o.getId());
                marker.put("type", "orchard");
                marker.put("name", o.getName());
                marker.put("lat", o.getLat());
                marker.put("lng", o.getLng());
                marker.put("address", o.getAddress());
                marker.put("coverImage", o.getCoverImage());
                result.add(marker);
            }
        }

        if (category == null || "homestay".equals(category)) {
            LambdaQueryWrapper<Homestay> homestayWrapper = new LambdaQueryWrapper<>();
            homestayWrapper.eq(Homestay::getStatus, 1)
                           .eq(Homestay::getAuditStatus, 1)
                           .isNotNull(Homestay::getLat)
                           .isNotNull(Homestay::getLng);
            List<Homestay> homestays = homestayMapper.selectList(homestayWrapper);
            for (Homestay h : homestays) {
                Map<String, Object> marker = new HashMap<>();
                marker.put("id", h.getId());
                marker.put("type", "homestay");
                marker.put("name", h.getName());
                marker.put("lat", h.getLat());
                marker.put("lng", h.getLng());
                marker.put("address", h.getAddress());
                marker.put("coverImage", h.getCoverImage());
                result.add(marker);
            }
        }

        if (category == null || "scenic".equals(category)) {
            LambdaQueryWrapper<Scenic> scenicWrapper = new LambdaQueryWrapper<>();
            scenicWrapper.eq(Scenic::getStatus, 1)
                         .isNotNull(Scenic::getLat)
                         .isNotNull(Scenic::getLng);
            List<Scenic> scenics = scenicMapper.selectList(scenicWrapper);
            for (Scenic s : scenics) {
                Map<String, Object> marker = new HashMap<>();
                marker.put("id", s.getId());
                marker.put("type", "scenic");
                marker.put("name", s.getName());
                marker.put("lat", s.getLat());
                marker.put("lng", s.getLng());
                marker.put("address", s.getLocation());
                marker.put("coverImage", s.getCoverImage());
                result.add(marker);
            }
        }

        return Result.ok(result);
    }
}
