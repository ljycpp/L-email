package com.practice.mailsystem.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.practice.mailsystem.auth.JwtService;
import com.practice.mailsystem.common.exception.BusinessException;
import com.practice.mailsystem.config.MailSystemProperties;
import com.practice.mailsystem.mail.mapper.MailLabelMapper;
import com.practice.mailsystem.user.dto.LoginRequest;
import com.practice.mailsystem.user.entity.SysUser;
import com.practice.mailsystem.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private JwtService jwtService;
    @Mock
    private MailLabelMapper labelMapper;
    @Mock
    private MailSystemProperties mailSystemProperties;

    @InjectMocks
    private UserServiceImpl userService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        when(mailSystemProperties.emailDomain()).thenReturn("lmailbox.com");
    }

    @Test
    void login_rejectsDisabledUser() {
        SysUser user = activeUser("admin@lmailbox.com", "password123");
        user.setStatus(0);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(new LoginRequest("admin", "password123")));

        assertEquals(403, ex.getCode());
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void login_allowsLocalAccountWithoutAtSign() {
        SysUser user = activeUser("admin@lmailbox.com", "password123");
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(jwtService.generateToken(user.getId(), user.getEmail())).thenReturn("token");

        assertEquals("token", userService.login(new LoginRequest("admin", "password123")).token());
    }

    private SysUser activeUser(String email, String rawPassword) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setStatus(1);
        return user;
    }
}
