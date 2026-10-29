package com.qingmeili.service;

import com.qingmeili.dto.preference.PreferenceDTO;

import java.util.Map;

/**
 * 显示偏好（主题 / 长辈模式）服务，游客与商家共用 user_preference 表
 */
public interface PreferenceService {

    Map<String, Object> get(String ownerType, Long ownerId);

    Map<String, Object> save(String ownerType, Long ownerId, PreferenceDTO dto);
}
