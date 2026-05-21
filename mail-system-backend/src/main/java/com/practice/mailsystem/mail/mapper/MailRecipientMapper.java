package com.practice.mailsystem.mail.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.practice.mailsystem.mail.entity.MailRecipient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailRecipientMapper extends BaseMapper<MailRecipient> {
}
