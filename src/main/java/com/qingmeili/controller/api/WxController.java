package com.qingmeili.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.User;
import com.qingmeili.mapper.UserMapper;
import com.qingmeili.service.SysConfigService;
import com.qingmeili.service.WxMiniService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序：openid 绑定 + 订阅消息模板下发
 */
@RestController
@RequiredArgsConstructor
public class WxController {

    private final WxMiniService wxMiniService;
    private final UserMapper userMapper;
    private final SysConfigService sysConfigService;

    /** 前端拿模板 ID 调 wx.requestSubscribeMessage；只有 ID，不泄露密钥 */
    @GetMapping("/api/common/wx/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", wxMiniService.configured());
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put("shipped", configValue("wx.template.shipped"));
        templates.put("refund", configValue("wx.template.refund"));
        templates.put("verified", configValue("wx.template.verified"));
        data.put("templates", templates);
        return Result.ok(data);
    }

    /** 小程序 wx.login 的 code 换 openid 并绑定到当前游客 */
    @PostMapping("/api/user/wx/openid")
    @RequireRole({"tourist"})
    public Result<Void> bindOpenid(@RequestBody Map<String, String> body) {
        String code = body == null ? null : body.get("code");
        String openid = wxMiniService.code2Openid(code);
        if (openid == null || openid.isBlank()) {
            return Result.fail("微信登录未配置或凭证无效");
        }
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        // openid 已被其他账号占用时不做覆盖，避免串号
        Long used = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getWechatOpenid, openid)
                .ne(User::getId, userId));
        if (used != null && used > 0) {
            return Result.fail("该微信已绑定其他账号");
        }
        user.setWechatOpenid(openid);
        userMapper.updateById(user);
        return Result.ok("绑定成功", null);
    }

    private String configValue(String key) {
        try {
            String v = sysConfigService.getValue(key).getData();
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }
}
