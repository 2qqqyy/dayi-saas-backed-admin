package com.qingmeili.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 解析山野向导回答末尾的 {@code %%META%%} 元信息行。
 *
 * <p>配合 {@link QingmeiliAgent} 的纯文本输出使用：正文与元信息共处一次回答，
 * 既避免了「结构化输出挤掉函数调用」的问题，又不用为此多花一次大模型调用。
 *
 * <p>解析采取「尽力而为」策略——格式不对、整行缺失都不会导致回答失败，
 * 最多退化为「没有推荐卡片 + 按问题关键词猜分类」，游客侧感知不到。
 */
public final class AgentMeta {

    /** 元信息行的起始标记 */
    public static final String MARKER = "%%META%%";

    /** 与系统提示词约定的分类枚举 */
    private static final Set<String> CATEGORIES = Set.of(
            "行程规划", "住宿指南", "游玩指南", "乡村好物", "出行交通", "亲子出行", "其他");

    private AgentMeta() {
    }

    /**
     * 把模型原始输出拆成「正文 + 结构化元信息」。
     *
     * @param raw       模型原始输出
     * @param question  游客问题，仅用于元信息缺失时兜底猜分类
     */
    public static AgentReply parse(String raw, String question) {
        AgentReply reply = new AgentReply();
        if (raw == null || raw.isBlank()) {
            reply.setContent("");
            reply.setCategory(guessCategory(question));
            reply.setResolved(Boolean.FALSE);
            reply.setCards(List.of());
            return reply;
        }

        String text = stripCodeFence(raw.trim());
        String meta = null;
        int idx = text.lastIndexOf(MARKER);
        if (idx >= 0) {
            meta = text.substring(idx + MARKER.length());
            text = text.substring(0, idx);
        } else {
            // 模型偶尔会漏掉标记，退一步找形如 category=... 的尾行
            int lineIdx = lastMetaLikeLine(text);
            if (lineIdx >= 0) {
                meta = text.substring(lineIdx);
                text = text.substring(0, lineIdx);
            }
        }

        reply.setContent(stripCodeFence(text.trim()));
        String category = null;
        Boolean resolved = null;
        List<String> cards = new ArrayList<>();
        if (meta != null) {
            for (String pair : meta.replace("\n", ";").split("[;；]")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = pair.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String value = pair.substring(eq + 1).trim();
                switch (key) {
                    case "category", "分类" -> category = value;
                    case "resolved", "已解决" -> resolved = !"false".equalsIgnoreCase(value) && !"0".equals(value);
                    case "cards", "卡片" -> cards.addAll(splitCards(value));
                    default -> {
                        // 忽略未知字段
                    }
                }
            }
        }
        reply.setCategory(category == null || !CATEGORIES.contains(category) ? guessCategory(question) : category);
        reply.setResolved(resolved == null ? Boolean.TRUE : resolved);
        reply.setCards(cards);
        return reply;
    }

    /** cards 值形如 homestay:1,homestay:2；也容忍顿号、空格、分号分隔 */
    private static List<String> splitCards(String value) {
        List<String> list = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return list;
        }
        for (String item : value.split("[,，、\\s]+")) {
            String ref = item.trim().replace("`", "");
            if (!ref.isEmpty() && ref.contains(":")) {
                list.add(ref);
            }
        }
        return list;
    }

    private static String stripCodeFence(String text) {
        String s = text.trim();
        if (s.startsWith("```")) {
            int firstLineEnd = s.indexOf('\n');
            s = firstLineEnd < 0 ? "" : s.substring(firstLineEnd + 1);
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
        }
        return s.trim();
    }

    /** 找出最后一行形如 category=... 的元信息 */
    private static int lastMetaLikeLine(String text) {
        String[] lines = text.split("\n");
        int offset = 0;
        int found = -1;
        for (String line : lines) {
            String t = line.trim().toLowerCase(Locale.ROOT);
            if (t.startsWith("category=") || t.startsWith("分类=")) {
                found = offset;
            }
            offset += line.length() + 1;
        }
        return found;
    }

    /** 元信息缺失时按问题关键词兜底猜分类，保证后台分类统计仍有意义 */
    public static String guessCategory(String question) {
        String q = question == null ? "" : question;
        if (q.matches(".*(亲子|孩子|带娃|小朋友|研学).*")) {
            return "亲子出行";
        }
        if (q.matches(".*(行程|路线|规划|一天|两天|怎么玩|安排|攻略).*")) {
            return "行程规划";
        }
        if (q.matches(".*(民宿|住宿|住哪|客栈|房间|入住|睡觉).*")) {
            return "住宿指南";
        }
        if (q.matches(".*(采摘|摘果|青梅季|场次|果园|预约).*")) {
            return "游玩指南";
        }
        if (q.matches(".*(农产品|特产|好物|买|多少钱|价格|伴手礼|梅酒|果干|蜂蜜|下单).*")) {
            return "乡村好物";
        }
        if (q.matches(".*(怎么去|交通|停车|地址|导航|在哪|多远|开车).*")) {
            return "出行交通";
        }
        return "其他";
    }
}
