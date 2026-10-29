package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingmeili.dto.preference.PreferenceDTO;
import com.qingmeili.entity.UserPreference;
import com.qingmeili.mapper.UserPreferenceMapper;
import com.qingmeili.service.AppearanceService;
import com.qingmeili.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 显示偏好实现：未保存过返回 {saved:false}，保存时按 (owner_type, owner_id) upsert
 */
@Service
@RequiredArgsConstructor
public class PreferenceServiceImpl implements PreferenceService {

    private final UserPreferenceMapper userPreferenceMapper;

    @Override
    public Map<String, Object> get(String ownerType, Long ownerId) {
        return toResult(find(ownerType, ownerId));
    }

    @Override
    public Map<String, Object> save(String ownerType, Long ownerId, PreferenceDTO dto) {
        String themeCode = dto.getThemeCode();
        if (themeCode == null || themeCode.isBlank()) {
            themeCode = AppearanceService.DEFAULT_THEME;
        } else if (themeCode.length() > 40) {
            themeCode = themeCode.substring(0, 40);
        }
        int elderMode = Boolean.TRUE.equals(dto.getElderMode()) ? 1 : 0;

        UserPreference existing = find(ownerType, ownerId);
        if (existing == null) {
            UserPreference preference = new UserPreference();
            preference.setOwnerType(ownerType);
            preference.setOwnerId(ownerId);
            preference.setThemeCode(themeCode);
            preference.setElderMode(elderMode);
            try {
                userPreferenceMapper.insert(preference);
            } catch (DuplicateKeyException ignored) {
                update(ownerType, ownerId, themeCode, elderMode);
            }
        } else {
            existing.setThemeCode(themeCode);
            existing.setElderMode(elderMode);
            userPreferenceMapper.updateById(existing);
        }
        return toResult(find(ownerType, ownerId));
    }

    private void update(String ownerType, Long ownerId, String themeCode, int elderMode) {
        userPreferenceMapper.update(null, new LambdaUpdateWrapper<UserPreference>()
                .eq(UserPreference::getOwnerType, ownerType)
                .eq(UserPreference::getOwnerId, ownerId)
                .set(UserPreference::getThemeCode, themeCode)
                .set(UserPreference::getElderMode, elderMode)
                .set(UserPreference::getUpdateTime, LocalDateTime.now()));
    }

    private UserPreference find(String ownerType, Long ownerId) {
        return userPreferenceMapper.selectOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getOwnerType, ownerType)
                .eq(UserPreference::getOwnerId, ownerId)
                .last("LIMIT 1"));
    }

    private Map<String, Object> toResult(UserPreference preference) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (preference == null) {
            result.put("saved", false);
            return result;
        }
        result.put("saved", true);
        result.put("themeCode", preference.getThemeCode() == null
                ? AppearanceService.DEFAULT_THEME : preference.getThemeCode());
        result.put("elderMode", preference.getElderMode() != null && preference.getElderMode() == 1);
        return result;
    }
}
