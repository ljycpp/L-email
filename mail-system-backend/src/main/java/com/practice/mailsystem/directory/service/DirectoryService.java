package com.practice.mailsystem.directory.service;

import com.practice.mailsystem.directory.dto.ContactUpsertRequest;
import com.practice.mailsystem.directory.dto.GroupUpsertRequest;
import com.practice.mailsystem.directory.vo.ContactItemVO;
import com.practice.mailsystem.directory.vo.GroupItemVO;
import com.practice.mailsystem.mail.dto.LabelUpsertRequest;
import com.practice.mailsystem.mail.dto.MailListQuery;
import com.practice.mailsystem.mail.entity.MailLabel;

import java.util.List;
import java.util.Map;

public interface DirectoryService {

    Map<String, Object> listContacts(MailListQuery query);

    Map<String, Object> listGroups();

    Map<String, Object> listContactsByGroup(Long groupId);

    Map<String, Object> listLabels();

    List<MailLabel> findLabelsByUserId(Long userId);

    Long saveContact(ContactUpsertRequest request);

    Long updateContact(Long contactId, ContactUpsertRequest request);

    void deleteContacts(List<Long> ids);

    Long saveGroup(GroupUpsertRequest request);

    Long updateGroup(Long groupId, GroupUpsertRequest request);

    void deleteGroup(Long groupId);

    Long saveLabel(LabelUpsertRequest request);

    Long updateLabel(Long labelId, LabelUpsertRequest request);

    void deleteLabel(Long labelId);
}
