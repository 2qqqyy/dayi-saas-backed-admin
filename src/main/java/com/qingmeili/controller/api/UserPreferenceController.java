package com.qingmeili.controller.api;

import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.preference.PreferenceDTO;
import com.qingmeili.service.NoticeService;
import com.qingmeili.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * C端 - 显示偏好（主题 / 长辈模式）
 */
@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping
    public Result<Map<String, Object>> get() {
        return Result.ok(preferenceService.get(NoticeService.OWNER_USER, UserContext.getUserId()));
    }

    @PutMapping
    public Result<Map<String, Object>> save(@RequestBody PreferenceDTO dto) {
        return Result.ok("已保存", preferenceService.save(NoticeService.OWNER_USER, UserContext.getUserId(), dto));
    }
}
