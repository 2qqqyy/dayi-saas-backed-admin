package com.qingmeili.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外观主题：与新版前端 FALLBACK_THEMES 保持同一份色板，
 * 前端未拿到接口时会用本地兜底，这里保证联网时也拿到一致的 7 套主题。
 */
@Service
public class AppearanceService {

    public static final String DEFAULT_THEME = "forest";

    public Map<String, Object> appearance() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("themes", THEMES);
        result.put("defaultTheme", DEFAULT_THEME);
        return result;
    }

    private static Map<String, Object> theme(String code, String name, String description,
                                             String primary, String deep, String light,
                                             String accent, String soft, String tint, int isDefault) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("description", description);
        m.put("primaryColor", primary);
        m.put("deepColor", deep);
        m.put("lightColor", light);
        m.put("accentColor", accent);
        m.put("softColor", soft);
        m.put("tintColor", tint);
        m.put("isDefault", isDefault);
        return m;
    }

    private static final List<Map<String, Object>> THEMES = List.of(
            theme("forest", "青梅绿", "深林绿与青梅嫩芽，山野纸感",
                    "#2F5D43", "#1B3A29", "#71906A", "#C8DE84", "#F2F6EC", "#E9EFE1", 1),
            theme("spring", "山泉青", "清透山泉与薄雾蓝绿，安静克制",
                    "#2F6470", "#1B3F49", "#6F98A0", "#BFE0D6", "#F1F7F7", "#E4EFF0", 0),
            theme("clay", "暖陶土", "晒场陶土与秋日暖白，质朴温暖",
                    "#8A4D35", "#5A2F1E", "#B08468", "#F0C9A8", "#FBF3EC", "#F4E7DC", 0),
            theme("plum", "暮山紫", "暮色山岚与熟成青梅，沉静内敛",
                    "#6B4B63", "#3F2B3B", "#94718A", "#E6C9DC", "#F9F2F7", "#F1E4ED", 0),
            theme("indigo", "夜靛蓝", "夜色靛蓝与星子微光，清醒安静",
                    "#3C5175", "#202E45", "#7589AA", "#C9D6EE", "#F2F5FA", "#E5EBF5", 0),
            theme("gold", "稻田金", "秋收稻穗与暖阳，明亮丰收",
                    "#8A6224", "#4E3612", "#B79355", "#EFD79B", "#FBF6E8", "#F4EACD", 0),
            theme("rose", "山茶粉", "山茶初绽与米白，柔和有温度",
                    "#96566A", "#582F3C", "#BE8B9A", "#F0CDD7", "#FBF2F5", "#F4E3E8", 0)
    );
}
