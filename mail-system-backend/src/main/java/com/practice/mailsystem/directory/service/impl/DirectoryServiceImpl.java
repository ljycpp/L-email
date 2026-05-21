package com.practice.mailsystem.directory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.practice.mailsystem.auth.UserContext;
import com.practice.mailsystem.common.exception.BusinessException;
import com.practice.mailsystem.directory.dto.ContactUpsertRequest;
import com.practice.mailsystem.directory.dto.GroupUpsertRequest;
import com.practice.mailsystem.directory.entity.MailContact;
import com.practice.mailsystem.directory.entity.MailContactGroup;
import com.practice.mailsystem.directory.mapper.MailContactGroupMapper;
import com.practice.mailsystem.directory.mapper.MailContactMapper;
import com.practice.mailsystem.directory.service.DirectoryService;
import com.practice.mailsystem.directory.vo.ContactItemVO;
import com.practice.mailsystem.directory.vo.GroupItemVO;
import com.practice.mailsystem.mail.dto.LabelUpsertRequest;
import com.practice.mailsystem.mail.dto.MailListQuery;
import com.practice.mailsystem.mail.entity.MailLabel;
import com.practice.mailsystem.mail.entity.MailUserLabel;
import com.practice.mailsystem.mail.mapper.MailLabelMapper;
import com.practice.mailsystem.mail.mapper.MailUserLabelMapper;
import com.practice.mailsystem.mail.vo.LabelItemVO;
import com.practice.mailsystem.mail.vo.PartyVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Service
public class DirectoryServiceImpl implements DirectoryService {

    private final MailContactMapper contactMapper;
    private final MailContactGroupMapper groupMapper;
    private final MailLabelMapper labelMapper;
    private final MailUserLabelMapper userLabelMapper;

    public DirectoryServiceImpl(MailContactMapper contactMapper,
                                MailContactGroupMapper groupMapper,
                                MailLabelMapper labelMapper,
                                MailUserLabelMapper userLabelMapper) {
        this.contactMapper = contactMapper;
        this.groupMapper = groupMapper;
        this.labelMapper = labelMapper;
        this.userLabelMapper = userLabelMapper;
    }

    @Override
    public Map<String, Object> listContacts(MailListQuery query) {
        Long userId = UserContext.requireUserId();
        LambdaQueryWrapper<MailContact> wrapper = new LambdaQueryWrapper<MailContact>()
                .eq(MailContact::getOwnerUserId, userId)
                .like(query.getName() != null && !query.getName().isBlank(), MailContact::getContactName, query.getName())
                .like(query.getMail() != null && !query.getMail().isBlank(), MailContact::getContactEmail, query.getMail())
                .eq(query.getGroupId() != null, MailContact::getGroupId, query.getGroupId())
                .orderByDesc(MailContact::getCreatedAt);
        Page<MailContact> page = contactMapper.selectPage(new Page<>(query.getPage(), query.getLimit()), wrapper);
        List<ContactItemVO> contacts = page.getRecords().stream()
                .map(item -> new ContactItemVO(item.getId(), item.getContactName(), item.getContactEmail(), item.getAvatarUrl(), item.getGroupId()))
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("total", page.getTotal());
        result.put("contacts", contacts);
        return result;
    }

    @Override
    public Map<String, Object> listContactsByGroup(Long groupId) {
        Long userId = requireUserId();
        MailContactGroup group = groupMapper.selectOne(new LambdaQueryWrapper<MailContactGroup>()
                .eq(MailContactGroup::getId, groupId)
                .eq(MailContactGroup::getOwnerUserId, userId)
                .last("limit 1"));
        if (group == null) {
            throw new BusinessException(404, "分组不存在");
        }
        List<MailContact> contacts = contactMapper.selectList(new LambdaQueryWrapper<MailContact>()
                .eq(MailContact::getOwnerUserId, userId)
                .eq(MailContact::getGroupId, groupId)
                .orderByDesc(MailContact::getCreatedAt));
        List<ContactItemVO> items = contacts.stream()
                .map(item -> new ContactItemVO(item.getId(), item.getContactName(), item.getContactEmail(), item.getAvatarUrl(), item.getGroupId()))
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("groupId", groupId);
        result.put("groupName", group.getGroupName());
        result.put("total", items.size());
        result.put("contacts", items);
        return result;
    }

    @Override
    public Map<String, Object> listGroups() {
        Long userId = UserContext.requireUserId();
        List<MailContactGroup> groups = groupMapper.selectList(new LambdaQueryWrapper<MailContactGroup>()
                .eq(MailContactGroup::getOwnerUserId, userId)
                .orderByDesc(MailContactGroup::getCreatedAt));
        List<MailContact> allContacts = contactMapper.selectList(new LambdaQueryWrapper<MailContact>()
                .eq(MailContact::getOwnerUserId, userId));
        List<GroupItemVO> groupList = groups.stream().map(group -> {
            List<PartyVO> contacts = allContacts.stream()
                    .filter(item -> group.getId().equals(item.getGroupId()))
                    .map(item -> new PartyVO(item.getContactName(), item.getContactEmail()))
                    .toList();
            return new GroupItemVO(group.getId(), group.getGroupName(), contacts);
        }).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("total", groupList.size());
        result.put("groupList", groupList);
        return result;
    }

    @Override
    public Map<String, Object> listLabels() {
        Long userId = UserContext.requireUserId();
        List<LabelItemVO> labelList = findLabelsByUserId(userId).stream()
                .map(item -> new LabelItemVO(String.valueOf(item.getId()), String.valueOf(item.getId()), item.getName(), item.getColor()))
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("total", labelList.size());
        result.put("labelList", labelList);
        return result;
    }

    @Override
    public List<MailLabel> findLabelsByUserId(Long userId) {
        return labelMapper.selectList(new LambdaQueryWrapper<MailLabel>()
                .eq(MailLabel::getUserId, userId)
                .orderByDesc(MailLabel::getCreatedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveContact(ContactUpsertRequest request) {
        Long userId = requireUserId();
        ensureContactEmailUnique(userId, request.mail(), null);
        MailContact contact = new MailContact();
        contact.setOwnerUserId(userId);
        contact.setGroupId(request.groupId());
        contact.setContactName(request.name());
        contact.setContactEmail(request.mail());
        contact.setAvatarUrl(StringUtils.hasText(request.avatarUrl()) ? request.avatarUrl() : defaultAvatar());
        contact.setRemark(request.remark());
        contact.setCreatedAt(LocalDateTime.now());
        contact.setUpdatedAt(LocalDateTime.now());
        contactMapper.insert(contact);
        return contact.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateContact(Long contactId, ContactUpsertRequest request) {
        Long userId = requireUserId();
        MailContact contact = getOwnedContact(contactId, userId);
        ensureContactEmailUnique(userId, request.mail(), contactId);
        contact.setGroupId(request.groupId());
        contact.setContactName(request.name());
        contact.setContactEmail(request.mail());
        contact.setAvatarUrl(StringUtils.hasText(request.avatarUrl()) ? request.avatarUrl() : defaultAvatar());
        contact.setRemark(request.remark());
        contact.setUpdatedAt(LocalDateTime.now());
        contactMapper.updateById(contact);
        return contact.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContacts(List<Long> ids) {
        Long userId = requireUserId();
        for (Long id : ids) {
            MailContact contact = getOwnedContact(id, userId);
            contactMapper.deleteById(contact.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveGroup(GroupUpsertRequest request) {
        Long userId = requireUserId();
        MailContactGroup group = new MailContactGroup();
        group.setOwnerUserId(userId);
        group.setGroupName(request.name());
        group.setCreatedAt(LocalDateTime.now());
        groupMapper.insert(group);
        bindContactsToGroup(userId, group.getId(), request.safeContacts());
        return group.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateGroup(Long groupId, GroupUpsertRequest request) {
        Long userId = requireUserId();
        MailContactGroup group = getOwnedGroup(groupId, userId);
        group.setGroupName(request.name());
        groupMapper.updateById(group);
        contactMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MailContact>()
                .eq(MailContact::getOwnerUserId, userId)
                .eq(MailContact::getGroupId, groupId)
                .set(MailContact::getGroupId, null));
        bindContactsToGroup(userId, groupId, request.safeContacts());
        return groupId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long groupId) {
        Long userId = requireUserId();
        MailContactGroup group = getOwnedGroup(groupId, userId);
        contactMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MailContact>()
                .eq(MailContact::getOwnerUserId, userId)
                .eq(MailContact::getGroupId, groupId)
                .set(MailContact::getGroupId, null));
        groupMapper.deleteById(group.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveLabel(LabelUpsertRequest request) {
        Long userId = requireUserId();
        MailLabel label = new MailLabel();
        label.setUserId(userId);
        label.setName(request.name());
        label.setColor(request.color());
        label.setCreatedAt(LocalDateTime.now());
        labelMapper.insert(label);
        return label.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateLabel(Long labelId, LabelUpsertRequest request) {
        Long userId = requireUserId();
        MailLabel label = getOwnedLabel(labelId, userId);
        label.setName(request.name());
        label.setColor(request.color());
        labelMapper.updateById(label);
        return label.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLabel(Long labelId) {
        Long userId = requireUserId();
        MailLabel label = getOwnedLabel(labelId, userId);
        userLabelMapper.delete(new LambdaQueryWrapper<MailUserLabel>()
                .eq(MailUserLabel::getLabelId, labelId));
        labelMapper.deleteById(label.getId());
    }

    private void bindContactsToGroup(Long userId, Long groupId, List<String> emails) {
        if (emails.isEmpty()) {
            return;
        }
        for (String email : emails) {
            MailContact contact = contactMapper.selectOne(new LambdaQueryWrapper<MailContact>()
                    .eq(MailContact::getOwnerUserId, userId)
                    .eq(MailContact::getContactEmail, email)
                    .last("limit 1"));
            if (contact != null) {
                contact.setGroupId(groupId);
                contact.setUpdatedAt(LocalDateTime.now());
                contactMapper.updateById(contact);
            }
        }
    }

    private MailContact getOwnedContact(Long contactId, Long userId) {
        MailContact contact = contactMapper.selectOne(new LambdaQueryWrapper<MailContact>()
                .eq(MailContact::getId, contactId)
                .eq(MailContact::getOwnerUserId, userId)
                .last("limit 1"));
        if (contact == null) {
            throw new BusinessException(404, "联系人不存在");
        }
        return contact;
    }

    private MailContactGroup getOwnedGroup(Long groupId, Long userId) {
        MailContactGroup group = groupMapper.selectOne(new LambdaQueryWrapper<MailContactGroup>()
                .eq(MailContactGroup::getId, groupId)
                .eq(MailContactGroup::getOwnerUserId, userId)
                .last("limit 1"));
        if (group == null) {
            throw new BusinessException(404, "分组不存在");
        }
        return group;
    }

    private MailLabel getOwnedLabel(Long labelId, Long userId) {
        MailLabel label = labelMapper.selectOne(new LambdaQueryWrapper<MailLabel>()
                .eq(MailLabel::getId, labelId)
                .eq(MailLabel::getUserId, userId)
                .last("limit 1"));
        if (label == null) {
            throw new BusinessException(404, "标签不存在");
        }
        return label;
    }

    private void ensureContactEmailUnique(Long userId, String email, Long excludeId) {
        LambdaQueryWrapper<MailContact> wrapper = new LambdaQueryWrapper<MailContact>()
                .eq(MailContact::getOwnerUserId, userId)
                .eq(MailContact::getContactEmail, email);
        if (excludeId != null) {
            wrapper.ne(MailContact::getId, excludeId);
        }
        Long count = contactMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(409, "联系人邮箱已存在");
        }
    }

    private Long requireUserId() {
        Long userId = UserContext.requireUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }

    private String defaultAvatar() {
        return "https://dummyimage.com/100x100/7ab8ff/ffffff&text=C";
    }
}
