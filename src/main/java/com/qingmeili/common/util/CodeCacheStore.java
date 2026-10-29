package com.qingmeili.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CodeCacheStore {

    private final Map<String, Entry> local = new ConcurrentHashMap<>();

    private record Entry(String value, long expireAt) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    public void set(String key, String value, long expireSeconds) {
        local.put(key, new Entry(value, System.currentTimeMillis() + expireSeconds * 1000));
    }

    public String get(String key) {
        Entry entry = local.get(key);
        if (entry == null) return null;
        if (entry.expired()) {
            local.remove(key);
            return null;
        }
        return entry.value();
    }

    public boolean hasKey(String key) {
        return get(key) != null;
    }

    public void delete(String key) {
        local.remove(key);
    }
}
