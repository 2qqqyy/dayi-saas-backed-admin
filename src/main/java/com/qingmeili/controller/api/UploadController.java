package com.qingmeili.controller.api;

import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传（本地存储版）。生产环境可替换为 OSS 实现，接口契约不变。
 */
@Slf4j
@RestController
@RequestMapping("/api/common")
public class UploadController {

    /** 只收图片：svg 可内嵌脚本、mp4/mp3/pdf 无业务使用方，一律不收 */
    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp");

    /** 魔数校验：扩展名可伪造，文件头不会 */
    private static boolean magicOk(String ext, byte[] head) {
        if (head == null || head.length < 12) {
            return false;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
            case "png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "gif" -> head[0] == 'G' && head[1] == 'I' && head[2] == 'F';
            case "webp" -> head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            default -> false;
        };
    }

    @Value("${qingmeili.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "biz", required = false, defaultValue = "common") String biz) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的文件类型: " + ext);
        }
        byte[] head;
        try {
            byte[] all = file.getBytes();
            if (all.length < 12) {
                throw new BusinessException("文件内容不完整");
            }
            head = java.util.Arrays.copyOfRange(all, 0, 12);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("文件读取失败");
        }
        if (!magicOk(ext, head)) {
            throw new BusinessException("文件内容与扩展名不符");
        }

        // biz 只允许字母数字，防止路径穿越
        String safeBiz = biz.replaceAll("[^a-zA-Z0-9_-]", "");
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File dir = new File(uploadDir, safeBiz);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("上传目录创建失败");
        }
        try {
            File target = new File(dir, filename);
            file.transferTo(target.getAbsoluteFile());
            Map<String, Object> result = new HashMap<>();
            result.put("url", "/uploads/" + safeBiz + "/" + filename);
            result.put("size", file.getSize());
            return Result.ok("上传成功", result);
        } catch (Exception e) {
            log.error("文件保存失败", e);
            throw new BusinessException("文件保存失败");
        }
    }

    /**
     * 入驻申请（未登录）提交营业执照/门店照片用。
     * 与登录态上传隔离：固定 biz=apply、限 5MB，避免匿名口被当成通用网盘。
     */
    @PostMapping("/upload-public")
    public Result<Map<String, Object>> uploadPublic(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(value = "biz", required = false, defaultValue = "apply") String biz) {
        if (!"apply".equals(biz)) {
            throw new BusinessException("该上传口仅用于入驻申请");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("图片不能超过 5MB");
        }
        return upload(file, "apply");
    }
}
