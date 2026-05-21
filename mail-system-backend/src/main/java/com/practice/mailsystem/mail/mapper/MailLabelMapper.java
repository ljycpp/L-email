package com.practice.mailsystem.mail.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.practice.mailsystem.mail.entity.MailLabel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailLabelMapper extends BaseMapper<MailLabel> {
}
