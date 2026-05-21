package com.practice.mailsystem.attachment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.practice.mailsystem.attachment.entity.MailAttachment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailAttachmentMapper extends BaseMapper<MailAttachment> {
}
