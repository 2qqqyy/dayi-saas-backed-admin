package com.qingmeili.service.impl;

import com.qingmeili.common.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.util.CodeCacheStore;
import com.qingmeili.common.util.CaptchaStore;
import com.qingmeili.common.util.JwtUtil;
import com.qingmeili.common.util.PasswordUtil;
import com.qingmeili.dto.auth.*;
import com.qingmeili.entity.Admin;
import com.qingmeili.entity.User;
import com.qingmeili.entity.UserRole;
import com.qingmeili.mapper.AdminMapper;
import com.qingmeili.mapper.RoleMapper;
import com.qingmeili.mapper.UserMapper;
import com.qingmeili.mapper.UserRoleMapper;
import com.qingmeili.service.AuthService;
import com.qingmeili.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final JwtUtil jwtUtil;
    private final CodeCacheStore codeCacheStore;
    private final CaptchaStore captchaStore;
    private final SysConfigService sysConfigService;
    private final com.qingmeili.service.SmsSender smsSender;

    /** 演示模式固定验证码（application.yml sms.demo-code），为空则生成随机码 */
    @org.springframework.beans.factory.annotation.Value("${sms.demo-code:}")
    private String demoSmsCode;

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final String SMS_LIMIT_PREFIX = "sms:limit:";
    private static final long SMS_CODE_EXPIRE = 300; // 5分钟
    private static final long SMS_LIMIT_EXPIRE = 60; // 60秒发送间隔

    @Override
    public SmsResponse sendSmsCode(SmsCodeDTO dto) {
        // 检查发送频率限制
        String limitKey = SMS_LIMIT_PREFIX + dto.getMobile();
        if (codeCacheStore.hasKey(limitKey)) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }

        // 生成验证码：演示模式（sms.demo-code 非空）使用固定码，便于无真实短信通道时联调
        String code = (demoSmsCode != null && !demoSmsCode.isBlank())
                ? demoSmsCode
                : RandomUtil.randomNumbers(6);
        String key = SMS_CODE_PREFIX + dto.getMobile();
        // 5分钟过期（Redis 不可用时自动降级为内存存储）
        codeCacheStore.set(key, code, SMS_CODE_EXPIRE);
        // 设置发送频率限制，60秒后才能再次发送
        codeCacheStore.set(limitKey, "1", SMS_LIMIT_EXPIRE);

        smsSender.sendCode(dto.getMobile(), code);

        return new SmsResponse((int) SMS_LIMIT_EXPIRE);
    }

    @Override
    @Transactional
    public LoginResponse userLogin(UserLoginDTO dto) {
        // 校验验证码
        verifySmsCode(dto.getMobile(), dto.getCode());

        // 查找用户，不存在则自动注册
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getMobile, dto.getMobile()));
        if (user == null) {
            user = new User();
            user.setMobile(dto.getMobile());
            // 验证码登录用户无需密码，存储空哈希
            user.setPasswordHash("");
            user.setNickname("用户" + dto.getMobile().substring(7));
            user.setLevel("普通会员");
            user.setStatus(1);
            user.setDeleted(0);
            userMapper.insert(user);
        }

        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        // 生成 token
        String token = jwtUtil.generateUserToken(user.getId(), "tourist");

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpireAt(System.currentTimeMillis() + 86400000L);
        response.setUserInfo(buildUserInfo(user));
        return response;
    }

    @Override
    @Transactional
    public LoginResponse userRegister(UserRegisterDTO dto) {
        sysConfigService.assertRegisterOpen();
        verifySmsCode(dto.getMobile(), dto.getCode());

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getMobile, dto.getMobile()));
        if (count > 0) {
            throw new BusinessException("该手机号已注册");
        }

        User user = new User();
        user.setMobile(dto.getMobile());
        // 验证码注册用户无需密码
        user.setPasswordHash("");
        String nickname = dto.getNickname() == null ? "" : dto.getNickname().trim();
        user.setNickname(nickname.isEmpty() ? "用户" + dto.getMobile().substring(7) : nickname);
        user.setLevel("普通会员");
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);

        String token = jwtUtil.generateUserToken(user.getId(), "tourist");

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpireAt(System.currentTimeMillis() + 86400000L);
        response.setUserInfo(buildUserInfo(user));
        return response;
    }

    @Override
    @Transactional
    public AdminLoginResponse adminLogin(AdminLoginDTO dto) {
        if (!captchaStore.verify(dto.getCaptchaId(), dto.getCaptcha())) {
            throw new BusinessException("验证码错误或已过期，请刷新后重试");
        }
        Admin admin = adminMapper.selectOne(
                new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, dto.getUsername()));
        if (admin == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 检查锁定
        if (admin.getStatus() == 0) {
            throw new BusinessException("账号已被锁定，请联系超级管理员");
        }

        // 校验密码（使用 BCrypt）
        if (!PasswordUtil.matches(dto.getPassword(), admin.getPasswordHash())) {
            // 登录失败计数
            admin.setLoginFailCount(admin.getLoginFailCount() + 1);
            if (admin.getLoginFailCount() >= 5) {
                admin.setStatus(0);
            }
            adminMapper.updateById(admin);
            throw new BusinessException("用户名或密码错误，已失败" + admin.getLoginFailCount() + "次");
        }

        // 登录成功，重置失败计数
        admin.setLoginFailCount(0);
        adminMapper.updateById(admin);

        // 查询角色
        String roleCode = getAdminRoleCode(admin.getId());

        String token = jwtUtil.generateAdminToken(admin.getId(), roleCode);

        AdminLoginResponse response = new AdminLoginResponse();
        response.setToken(token);
        response.setExpireAt(System.currentTimeMillis() + 86400000L);

        AdminLoginResponse.AdminInfo adminInfo = new AdminLoginResponse.AdminInfo();
        adminInfo.setId(admin.getId());
        adminInfo.setUsername(admin.getUsername());
        adminInfo.setRealName(admin.getRealName());
        adminInfo.setAvatar(admin.getAvatar());
        adminInfo.setRole(roleCode);
        response.setAdminInfo(adminInfo);

        return response;
    }

    @Override
    public void logout() {
        // JWT 无状态，客户端清除 token 即可
        // 如需服务端失效可引入 Redis 黑名单
        UserContext.clear();
    }

    // ==================== 私有方法 ====================

    private void verifySmsCode(String mobile, String code) {
        // 演示环境固定验证码 123456（接入真实短信服务后移除）
        if ("123456".equals(code)) {
            return;
        }
        String key = SMS_CODE_PREFIX + mobile;
        String cached = codeCacheStore.get(key);
        if (cached == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!cached.equals(code)) {
            throw new BusinessException("验证码错误");
        }
        codeCacheStore.delete(key);
    }

    private LoginResponse.UserInfo buildUserInfo(User user) {
        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        info.setId(user.getId());
        info.setMobile(user.getMobile());
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        info.setLevel(user.getLevel());
        return info;
    }

    private String getAdminRoleCode(Long adminId) {
        UserRole userRole = userRoleMapper.selectOne(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, adminId).last("LIMIT 1"));
        if (userRole != null) {
            com.qingmeili.entity.Role role = roleMapper.selectById(userRole.getRoleId());
            if (role != null) {
                return role.getRoleCode();
            }
        }
        return "admin";
    }
}
