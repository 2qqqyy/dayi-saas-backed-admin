package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.entity.Notice;

import java.util.List;
import java.util.Map;

/**
 * 公告服务：公共（未登录）/ 游客 / 商家三个视角
 */
public interface NoticeService extends IService<Notice> {

    /** notice_read.owner_type 取值 */
    String OWNER_USER = "user";
    String OWNER_MERCHANT = "merchant";

    List<Map<String, Object>> publicFeed(int limit);

    Map<String, Object> publicDetail(Long id);

    List<Map<String, Object>> ownerFeed(String ownerType, Long ownerId, int limit);

    Map<String, Object> ownerDetail(String ownerType, Long ownerId, Long id);

    int unreadCount(String ownerType, Long ownerId);

    void markRead(String ownerType, Long ownerId, Long id);
}
