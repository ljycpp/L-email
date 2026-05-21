package com.practice.mailsystem.mail.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.practice.mailsystem.mail.entity.MailMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailMessageMapper extends BaseMapper<MailMessage> {
}
