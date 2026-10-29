package com.qingmeili.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.Merchant;
import com.qingmeili.entity.Order;
import com.qingmeili.entity.User;
import com.qingmeili.mapper.MerchantMapper;
import com.qingmeili.mapper.OrderMapper;
import com.qingmeili.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 平台 - 列表导出。此前仅工作台/统计有 CSV 导出，订单/用户/商家列表无法对账留档。
 */
@RestController
@RequestMapping("/api/admin/export")
@RequiredArgsConstructor
@RequireRole({"admin", "super_admin"})
public class AdminExportController {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;

    @GetMapping("/orders")
    public ResponseEntity<byte[]> orders() {
        List<Order> list = orderMapper.selectList(
                new LambdaQueryWrapper<Order>().orderByDesc(Order::getCreateTime).last("LIMIT 5000"));
        StringBuilder sb = new StringBuilder("\uFEFF订单号,类型,状态,金额,实付,退款,商家ID,用户ID,下单时间\n");
        for (Order o : list) {
            sb.append(csv(o.getOrderNo())).append(',')
                    .append(o.getType() == null ? "" : o.getType()).append(',')
                    .append(csv(o.getStatus())).append(',')
                    .append(o.getAmount() == null ? "" : o.getAmount()).append(',')
                    .append(o.getPayAmount() == null ? "" : o.getPayAmount()).append(',')
                    .append(o.getRefundAmount() == null ? "" : o.getRefundAmount()).append(',')
                    .append(o.getMerchantId() == null ? "" : o.getMerchantId()).append(',')
                    .append(o.getUserId() == null ? "" : o.getUserId()).append(',')
                    .append(o.getCreateTime() == null ? "" : o.getCreateTime().format(DF)).append('\n');
        }
        return csvResponse("orders", sb.toString());
    }

    @GetMapping("/users")
    public ResponseEntity<byte[]> users() {
        List<User> list = userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime).last("LIMIT 5000"));
        StringBuilder sb = new StringBuilder("\uFEFF用户ID,昵称,手机号,状态,注册时间\n");
        for (User u : list) {
            sb.append(u.getId()).append(',')
                    .append(csv(u.getNickname())).append(',')
                    .append(csv(com.qingmeili.common.util.MaskSerializers.mobile(u.getMobile()))).append(',')
                    .append(u.getStatus() == null ? "" : u.getStatus()).append(',')
                    .append(u.getCreateTime() == null ? "" : u.getCreateTime().format(DF)).append('\n');
        }
        return csvResponse("users", sb.toString());
    }

    @GetMapping("/merchants")
    public ResponseEntity<byte[]> merchants() {
        List<Merchant> list = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>().orderByAsc(Merchant::getId));
        StringBuilder sb = new StringBuilder("\uFEFF商家ID,名称,类型,联系人,手机号,状态,资质状态\n");
        for (Merchant m : list) {
            sb.append(m.getId()).append(',')
                    .append(csv(m.getName())).append(',')
                    .append(m.getType() == null ? "" : m.getType()).append(',')
                    .append(csv(m.getContact())).append(',')
                    .append(csv(com.qingmeili.common.util.MaskSerializers.mobile(m.getMobile()))).append(',')
                    .append(m.getStatus() == null ? "" : m.getStatus()).append(',')
                    .append(csv(m.getAuditStatus() == null ? null : String.valueOf(m.getAuditStatus()))).append('\n');
        }
        return csvResponse("merchants", sb.toString());
    }

    private String csv(String v) {
        if (v == null) {
            return "";
        }
        String s = v.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }

    private ResponseEntity<byte[]> csvResponse(String name, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name + ".csv");
        return ResponseEntity.ok().headers(headers).body(body.getBytes(StandardCharsets.UTF_8));
    }
}
