package com.practice.mailsystem.directory.vo;

import com.practice.mailsystem.mail.vo.PartyVO;

import java.util.List;

public record GroupItemVO(Long id, String name, List<PartyVO> contacts) {
}
