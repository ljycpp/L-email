package com.practice.mailsystem.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.practice.mailsystem.auth.JwtService;
import com.practice.mailsystem.auth.LoginUser;
import com.practice.mailsystem.common.exception.BusinessException;
import com.practice.mailsystem.config.MailSystemProperties;
import com.practice.mailsystem.mail.entity.MailLabel;
import com.practice.mailsystem.mail.mapper.MailLabelMapper;
import com.practice.mailsystem.user.dto.LoginRequest;
import com.practice.mailsystem.user.dto.RegisterRequest;
import com.practice.mailsystem.user.entity.SysUser;
import com.practice.mailsystem.user.mapper.SysUserMapper;
import com.practice.mailsystem.user.service.UserService;
import com.practice.mailsystem.user.vo.LoginResponse;
import com.practice.mailsystem.user.vo.UserInfoResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {

    private static final Pattern LOCAL_MAILBOX = Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9._-]{0,62}[a-zA-Z0-9])?$");

    /** 旧版演示数据使用此后缀；仅登录时若在新后缀下找不到用户则回退查找，避免未清库的库无法登录。 */
    private static final String LEGACY_LOGIN_EMAIL_SUFFIX = "@mail.com";

    private final SysUserMapper sysUserMapper;
    private final JwtService jwtService;
    private final MailLabelMapper labelMapper;
    private final MailSystemProperties mailSystemProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(SysUserMapper sysUserMapper, JwtService jwtService, MailLabelMapper labelMapper,
                           MailSystemProperties mailSystemProperties) {
        this.sysUserMapper = sysUserMapper;
        this.jwtService = jwtService;
        this.labelMapper = labelMapper;
        this.mailSystemProperties = mailSystemProperties;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String email = normalizeLoginEmail(request.email());
        SysUser user = findUserForLogin(email);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "邮箱或密码错误");
        }
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new LoginResponse(token);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        String email = requireSystemMailbox(request.email());
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email));
        if (count != null && count > 0) {
            throw new BusinessException(409, "邮箱已注册");
        }
        SysUser user = new SysUser();
        user.setEmail(email);
        user.setNickname(request.nickname());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAvatarUrl("https://dummyimage.com/100x100/4f86f7/ffffff&text=U");
        user.setIntroduction("L邮箱用户");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);
        createDefaultLabels(user.getId());
    }

    @Override
    public UserInfoResponse getUserInfo(LoginUser loginUser) {
        SysUser user = sysUserMapper.selectById(loginUser.userId());
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return new UserInfoResponse(
                List.of("admin"),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getId(),
                user.getIntroduction(),
                user.getEmail()
        );
    }

    private void createDefaultLabels(Long userId) {
        insertLabel(userId, "重要", "#F56C6C");
        insertLabel(userId, "项目", "#409EFF");
        insertLabel(userId, "待处理", "#E6A23C");
    }

    private void insertLabel(Long userId, String name, String color) {
        MailLabel label = new MailLabel();
        label.setUserId(userId);
        label.setName(name);
        label.setColor(color);
        label.setCreatedAt(LocalDateTime.now());
        labelMapper.insert(label);
    }

    /**
     * 先按规范化邮箱查库；若为当前系统固定后缀且无记录，则尝试同本地账号的旧域名后缀（historical demo 使用 xxx@mail.com）。
     */
    private SysUser findUserForLogin(String canonicalEmail) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, canonicalEmail));
        if (user != null) {
            return user;
        }
        String suffix = "@" + configuredDomain();
        if (canonicalEmail.endsWith(suffix) && canonicalEmail.length() > suffix.length()) {
            String local = canonicalEmail.substring(0, canonicalEmail.length() - suffix.length());
            user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, local + LEGACY_LOGIN_EMAIL_SUFFIX));
        }
        return user;
    }

    private String configuredDomain() {
        String domain = mailSystemProperties.emailDomain();
        if (domain == null || domain.isBlank()) {
            throw new IllegalStateException("未配置 mail-system.email-domain");
        }
        return domain.trim().toLowerCase();
    }

    /**
     * 注册：必须使用系统固定域名；规范为小写。
     */
    private String requireSystemMailbox(String raw) {
        String domain = configuredDomain();
        String suffix = "@" + domain;
        String email = raw == null ? "" : raw.trim().toLowerCase();
        if (!email.endsWith(suffix)) {
            throw new BusinessException(400, "请使用 @" + domain + " 邮箱账号");
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        if (local.isEmpty() || email.indexOf('@', at + 1) >= 0) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
        if (!LOCAL_MAILBOX.matcher(local).matches()) {
            throw new BusinessException(400, "邮箱账号仅支持字母、数字及 . _ -，且首尾须为字母或数字");
        }
        return local + suffix;
    }

    /**
     * 登录：若只填本地账号则自动拼上系统域名；完整地址则原样小写（兼容历史数据如旧域名演示库）。
     */
    private String normalizeLoginEmail(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase();
        if (s.isEmpty()) {
            return s;
        }
        if (!s.contains("@")) {
            return s + "@" + configuredDomain();
        }
        return s;
    }
}
