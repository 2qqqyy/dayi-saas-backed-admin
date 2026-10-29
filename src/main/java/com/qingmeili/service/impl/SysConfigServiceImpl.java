package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.SysConfig;
import com.qingmeili.mapper.SysConfigMapper;
import com.qingmeili.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务实现
 */
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Override
    public Result<List<SysConfig>> listAll() {
        List<SysConfig> list = baseMapper.selectList(
                new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId));
        return Result.ok(list);
    }

    @Override
    public Result<String> getValue(String key) {
        SysConfig config = baseMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            return Result.ok(null);
        }
        return Result.ok(config.getConfigValue());
    }

    @Override
    @Transactional
    public Result<Void> batchUpdate(List<Map<String, String>> configs) {
        if (configs == null || configs.isEmpty()) {
            return Result.ok("无需更新", null);
        }
        for (Map<String, String> config : configs) {
            String key = config.get("key");
            String value = config.get("value");
            if (key == null || key.isEmpty()) continue;

            SysConfig existing = baseMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
            if (existing != null) {
                existing.setConfigValue(value != null ? value : "");
                baseMapper.updateById(existing);
            } else {
                SysConfig newConfig = new SysConfig();
                newConfig.setConfigKey(key);
                newConfig.setConfigValue(value != null ? value : "");
                baseMapper.insert(newConfig);
            }
        }
        return Result.ok("更新成功", null);
    }

    @Override
    public boolean flag(String key, boolean defaultValue) {
        SysConfig config = baseMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        String value = config == null ? null : config.getConfigValue();
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String v = value.trim();
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "on".equalsIgnoreCase(v);
    }

    @Override
    public void assertGuestWritesOpen() {
        if (flag("maintenance_mode", false)) {
            throw new BusinessException("系统维护中，已暂停下单，请稍后再试");
        }
    }

    @Override
    public void assertRegisterOpen() {
        assertGuestWritesOpen();
        if (!flag("register_enabled", true)) {
            throw new BusinessException("管理员已关闭新用户注册");
        }
    }
}
