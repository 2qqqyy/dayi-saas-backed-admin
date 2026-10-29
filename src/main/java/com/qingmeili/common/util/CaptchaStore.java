package com.qingmeili.common.util;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码：服务端保存答案，5 分钟过期、一次一用。
 */
@Component
public class CaptchaStore {

    private static final long TTL_MILLIS = 5 * 60 * 1000L;
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private record Entry(String code, long expireAt) {
    }

    public record Captcha(String captchaId, String image) {
    }

    public Captcha generate() {
        purgeExpired();
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt((int) (Math.random() * CHARS.length())));
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        store.put(id, new Entry(sb.toString(), System.currentTimeMillis() + TTL_MILLIS));
        return new Captcha(id, "data:image/png;base64," + Base64.getEncoder().encodeToString(draw(sb.toString())));
    }

    public boolean verify(String captchaId, String code) {
        if (captchaId == null || code == null) {
            return false;
        }
        Entry entry = store.remove(captchaId);
        return entry != null
                && entry.expireAt() > System.currentTimeMillis()
                && entry.code().equalsIgnoreCase(code.trim());
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt() < now);
    }

    private byte[] draw(String code) {
        int w = 110, h = 40;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // 之前没开抗锯齿，字符边缘发糙才是"老旧"观感的真因
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(251, 249, 242));
        g.fillRect(0, 0, w, h);

        // 干扰从 5 条随机粗线降到 2 条淡线 + 少量底噪点：仍不宜被机器直读，但不再糊在笔画上
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 2; i++) {
            g.setColor(new Color(190, 205, 192));
            g.drawLine(0, 6 + (int) (Math.random() * (h - 12)), w, 6 + (int) (Math.random() * (h - 12)));
        }
        g.setColor(new Color(214, 224, 214));
        for (int i = 0; i < 26; i++) {
            g.fillRect((int) (Math.random() * w), (int) (Math.random() * h), 1, 1);
        }

        // 取色统一为品牌绿，不再逐字随机；只保留 ±2px 基线抖动做扰动
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.setColor(new Color(47, 93, 67));
        int left = (w - (code.length() * 24 - 8)) / 2 + 2;
        for (int i = 0; i < code.length(); i++) {
            int jitter = (int) (Math.random() * 5) - 2;
            g.drawString(String.valueOf(code.charAt(i)), left + i * 24, 29 + jitter);
        }
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("验证码图片生成失败", e);
        }
    }
}
