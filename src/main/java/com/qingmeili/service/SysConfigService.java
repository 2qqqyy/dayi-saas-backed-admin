package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.SysConfig;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务
 */
public interface SysConfigService extends IService<SysConfig> {

    Result<List<SysConfig>> listAll();

    Result<String> getValue(String key);

    Result<Void> batchUpdate(List<Map<String, String>> configs);

    /** 读取布尔开关，key 不存在时返回 defaultValue；true/1/on 视为开启 */
    boolean flag(String key, boolean defaultValue);

    /** 游客端写操作（下单 / 注册）前置校验：后台开启「维护模式」后直接拒绝 */
    void assertGuestWritesOpen();

    /** 后台关闭「开放注册」后游客端无法注册新用户 */
    void assertRegisterOpen();
}
