package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.entity.Notice;
import com.qingmeili.entity.NoticeRead;
import com.qingmeili.mapper.NoticeMapper;
import com.qingmeili.mapper.NoticeReadMapper;
import com.qingmeili.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 公告服务实现：只返回已发布、未过期且面向当前端的公告；
 * 已读状态按 notice_read(owner_type, owner_id) 独立记录。
 */
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {

    private final NoticeReadMapper noticeReadMapper;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public List<Map<String, Object>> publicFeed(int limit) {
        List<Notice> notices = baseMapper.selectList(
                visibleQuery(audienceOf(OWNER_USER)).last("LIMIT " + clamp(limit)));
        return toItems(notices, Set.of());
    }

    @Override
    public Map<String, Object> publicDetail(Long id) {
        return toItem(requireVisible(id, OWNER_USER), false, true);
    }

    @Override
    public List<Map<String, Object>> ownerFeed(String ownerType, Long ownerId, int limit) {
        List<Notice> notices = baseMapper.selectList(
                visibleQuery(audienceOf(ownerType)).last("LIMIT " + clamp(limit)));
        return toItems(notices, readNoticeIds(ownerType, ownerId, notices));
    }

    @Override
    public Map<String, Object> ownerDetail(String ownerType, Long ownerId, Long id) {
        Notice notice = requireVisible(id, ownerType);
        boolean read = readNoticeIds(ownerType, ownerId, List.of(notice)).contains(notice.getId());
        return toItem(notice, read, true);
    }

    @Override
    public int unreadCount(String ownerType, Long ownerId) {
        List<Notice> notices = baseMapper.selectList(visibleQuery(audienceOf(ownerType)));
        if (notices.isEmpty()) {
            return 0;
        }
        int read = readNoticeIds(ownerType, ownerId, notices).size();
        return Math.max(0, notices.size() - read);
    }

    @Override
    public void markRead(String ownerType, Long ownerId, Long id) {
        requireVisible(id, ownerType);
        Long existing = noticeReadMapper.selectCount(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getOwnerType, ownerType)
                .eq(NoticeRead::getOwnerId, ownerId)
                .eq(NoticeRead::getNoticeId, id));
        if (existing != null && existing > 0) {
            return;
        }
        NoticeRead read = new NoticeRead();
        read.setNoticeId(id);
        read.setOwnerType(ownerType);
        read.setOwnerId(ownerId);
        read.setReadTime(LocalDateTime.now());
        try {
            noticeReadMapper.insert(read);
        } catch (DuplicateKeyException ignored) {
            // 并发重复标记由唯一键兜底，结果一致
        }
    }

    private Notice requireVisible(Long id, String ownerType) {
        Notice notice = baseMapper.selectOne(
                visibleQuery(audienceOf(ownerType)).eq(Notice::getId, id));
        if (notice == null) {
            throw new BusinessException(404, "公告不存在或已下线");
        }
        return notice;
    }

    private LambdaQueryWrapper<Notice> visibleQuery(String audience) {
        LocalDateTime now = LocalDateTime.now();
        return new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, 1)
                .in(Notice::getAudience, "ALL", audience)
                .and(w -> w.isNull(Notice::getExpireTime).or().gt(Notice::getExpireTime, now))
                .orderByDesc(Notice::getPinned)
                .orderByDesc(Notice::getPublishTime)
                .orderByDesc(Notice::getId);
    }

    private Set<Long> readNoticeIds(String ownerType, Long ownerId, Collection<Notice> notices) {
        if (notices.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = notices.stream().map(Notice::getId).toList();
        List<NoticeRead> reads = noticeReadMapper.selectList(new LambdaQueryWrapper<NoticeRead>()
                .eq(NoticeRead::getOwnerType, ownerType)
                .eq(NoticeRead::getOwnerId, ownerId)
                .in(NoticeRead::getNoticeId, ids));
        Set<Long> result = new HashSet<>();
        for (NoticeRead read : reads) {
            result.add(read.getNoticeId());
        }
        return result;
    }

    private List<Map<String, Object>> toItems(List<Notice> notices, Set<Long> readIds) {
        List<Map<String, Object>> list = new ArrayList<>(notices.size());
        for (Notice notice : notices) {
            list.add(toItem(notice, readIds.contains(notice.getId()), false));
        }
        return list;
    }

    private Map<String, Object> toItem(Notice notice, boolean read, boolean withContent) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", notice.getId());
        item.put("title", notice.getTitle());
        item.put("summary", notice.getSummary());
        item.put("titleEn", notice.getTitleEn());
        item.put("summaryEn", notice.getSummaryEn());
        item.put("category", notice.getCategory() == null ? "NOTICE" : notice.getCategory());
        item.put("audience", notice.getAudience() == null ? "ALL" : notice.getAudience());
        item.put("pinned", notice.getPinned() != null && notice.getPinned() == 1);
        item.put("publishTime", notice.getPublishTime() == null ? "" : notice.getPublishTime().format(DF));
        if (notice.getExpireTime() != null) {
            item.put("expireTime", notice.getExpireTime().format(DF));
        }
        item.put("read", read);
        if (withContent) {
            item.put("content", notice.getContent() == null ? "" : notice.getContent());
        item.put("contentEn", notice.getContentEn());
        }
        return item;
    }

    private String audienceOf(String ownerType) {
        return OWNER_MERCHANT.equals(ownerType) ? "MERCHANT" : "USER";
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
