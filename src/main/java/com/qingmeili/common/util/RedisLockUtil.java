package com.qingmeili.common.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisLockUtil {

    private final ConcurrentHashMap<String, LockEntry> locks = new ConcurrentHashMap<>();

    private record LockEntry(String value, long expireAt) {}

    public boolean tryLock(String key, String value, long expireSeconds) {
        long expireAt = System.currentTimeMillis() + expireSeconds * 1000;
        LockEntry existing = locks.putIfAbsent(key, new LockEntry(value, expireAt));
        if (existing == null) return true;
        if (System.currentTimeMillis() > existing.expireAt()) {
            locks.remove(key);
            existing = locks.putIfAbsent(key, new LockEntry(value, expireAt));
            return existing == null;
        }
        return false;
    }

    public void unlock(String key, String value) {
        LockEntry entry = locks.get(key);
        if (entry != null && value.equals(entry.value())) {
            locks.remove(key);
        }
    }
}
