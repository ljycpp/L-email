package com.practice.mailsystem.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.practice.mailsystem.directory.entity.MailContact;
import com.practice.mailsystem.directory.entity.MailContactGroup;
import com.practice.mailsystem.directory.mapper.MailContactGroupMapper;
import com.practice.mailsystem.directory.mapper.MailContactMapper;
import com.practice.mailsystem.mail.entity.MailLabel;
import com.practice.mailsystem.mail.entity.MailMessage;
import com.practice.mailsystem.mail.entity.MailRecipient;
import com.practice.mailsystem.mail.entity.MailUserBox;
import com.practice.mailsystem.mail.entity.MailUserLabel;
import com.practice.mailsystem.mail.mapper.MailLabelMapper;
import com.practice.mailsystem.mail.mapper.MailMessageMapper;
import com.practice.mailsystem.mail.mapper.MailRecipientMapper;
import com.practice.mailsystem.mail.mapper.MailUserBoxMapper;
import com.practice.mailsystem.mail.mapper.MailUserLabelMapper;
import com.practice.mailsystem.config.MailSystemProperties;
import com.practice.mailsystem.user.entity.SysUser;
import com.practice.mailsystem.user.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final MailLabelMapper labelMapper;
    private final MailMessageMapper mailMessageMapper;
    private final MailRecipientMapper recipientMapper;
    private final MailUserBoxMapper userBoxMapper;
    private final MailUserLabelMapper userLabelMapper;
    private final MailContactMapper contactMapper;
    private final MailContactGroupMapper contactGroupMapper;
    private final MailSystemProperties mailSystemProperties;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.data.initialize-demo-data:true}")
    private boolean initializeDemoData;

    public DemoDataInitializer(SysUserMapper userMapper,
                               MailLabelMapper labelMapper,
                               MailMessageMapper mailMessageMapper,
                               MailRecipientMapper recipientMapper,
                               MailUserBoxMapper userBoxMapper,
                               MailUserLabelMapper userLabelMapper,
                               MailContactMapper contactMapper,
                               MailContactGroupMapper contactGroupMapper,
                               MailSystemProperties mailSystemProperties,
                               BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.labelMapper = labelMapper;
        this.mailMessageMapper = mailMessageMapper;
        this.recipientMapper = recipientMapper;
        this.userBoxMapper = userBoxMapper;
        this.userLabelMapper = userLabelMapper;
        this.contactMapper = contactMapper;
        this.contactGroupMapper = contactGroupMapper;
        this.mailSystemProperties = mailSystemProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (!initializeDemoData) {
            return;
        }
        Long userCount = userMapper.selectCount(null);
        if (userCount != null && userCount > 0) {
            return;
        }

        SysUser admin = createUser(mailbox("admin"), "123456", "管理员", "系统管理员");
        SysUser alice = createUser(mailbox("alice"), "123456", "Alice", "项目经理");
        SysUser bob = createUser(mailbox("bob"), "123456", "Bob", "后端开发");
        SysUser carol = createUser(mailbox("carol"), "123456", "Carol", "前端开发");

        List<MailLabel> adminLabels = createDefaultLabels(admin.getId());
        createDefaultLabels(alice.getId());
        createDefaultLabels(bob.getId());
        createDefaultLabels(carol.getId());

        MailContactGroup projectGroup = createGroup(admin.getId(), "项目组");
        MailContactGroup classGroup = createGroup(admin.getId(), "班级同学");

        createContact(admin.getId(), projectGroup.getId(), alice.getId(), "Alice", mailbox("alice"));
        createContact(admin.getId(), projectGroup.getId(), bob.getId(), "Bob", mailbox("bob"));
        createContact(admin.getId(), classGroup.getId(), carol.getId(), "Carol", mailbox("carol"));
        createContact(alice.getId(), null, admin.getId(), "管理员", mailbox("admin"));
        createContact(bob.getId(), null, admin.getId(), "管理员", mailbox("admin"));
        createContact(carol.getId(), null, admin.getId(), "管理员", mailbox("admin"));

        MailMessage kickoff = createMessage(
                admin.getId(),
                "实训项目启动安排",
                "<p>本周完成后端骨架、数据库设计和前端改造方案确认。</p><p>请各成员在晚 8 点前同步进度。</p>",
                false,
                LocalDateTime.now().minusDays(2)
        );
        addRecipient(kickoff.getId(), alice.getId(), "Alice", mailbox("alice"), "TO");
        addRecipient(kickoff.getId(), bob.getId(), "Bob", mailbox("bob"), "TO");
        addRecipient(kickoff.getId(), carol.getId(), "Carol", mailbox("carol"), "CC");
        MailUserBox adminOutbox = addUserBox(kickoff.getId(), admin.getId(), "OUTBOX", "SENDER", 1, LocalDateTime.now().minusDays(2));
        MailUserBox aliceInbox = addUserBox(kickoff.getId(), alice.getId(), "INBOX", "TO", 0, LocalDateTime.now().minusDays(2));
        MailUserBox bobInbox = addUserBox(kickoff.getId(), bob.getId(), "INBOX", "TO", 1, LocalDateTime.now().minusDays(2));
        MailUserBox carolInbox = addUserBox(kickoff.getId(), carol.getId(), "INBOX", "CC", 0, LocalDateTime.now().minusDays(2));
        bindLabel(adminOutbox.getId(), adminLabels.get(1).getId());
        bindLabel(aliceInbox.getId(), findLabelIdByName(alice.getId(), "项目"));
        bindLabel(bobInbox.getId(), findLabelIdByName(bob.getId(), "待处理"));
        bindLabel(carolInbox.getId(), findLabelIdByName(carol.getId(), "项目"));

        MailMessage report = createMessage(
                bob.getId(),
                "后端接口开发进度",
                "<p>已完成登录、收件箱、草稿箱和标签管理接口。</p><p>今晚补充发信兼容层。</p>",
                false,
                LocalDateTime.now().minusDays(1)
        );
        addRecipient(report.getId(), admin.getId(), "管理员", mailbox("admin"), "TO");
        addRecipient(report.getId(), alice.getId(), "Alice", mailbox("alice"), "CC");
        MailUserBox bobOutbox = addUserBox(report.getId(), bob.getId(), "OUTBOX", "SENDER", 1, LocalDateTime.now().minusDays(1));
        MailUserBox adminInbox = addUserBox(report.getId(), admin.getId(), "INBOX", "TO", 0, LocalDateTime.now().minusDays(1));
        MailUserBox aliceCc = addUserBox(report.getId(), alice.getId(), "INBOX", "CC", 1, LocalDateTime.now().minusDays(1));
        bindLabel(bobOutbox.getId(), findLabelIdByName(bob.getId(), "重要"));
        bindLabel(adminInbox.getId(), adminLabels.get(2).getId());
        bindLabel(aliceCc.getId(), findLabelIdByName(alice.getId(), "待处理"));

        MailMessage draft = createMessage(
                admin.getId(),
                "邮件系统答辩提纲（草稿）",
                "<p>1. 需求分析</p><p>2. 数据库设计</p><p>3. 核心功能演示</p>",
                true,
                null
        );
        MailUserBox draftBox = addUserBox(draft.getId(), admin.getId(), "DRAFT", "SENDER", 1, LocalDateTime.now().minusHours(6));
        bindLabel(draftBox.getId(), adminLabels.get(0).getId());
    }

    private String mailbox(String localPart) {
        String domain = mailSystemProperties.emailDomain();
        if (domain == null || domain.isBlank()) {
            throw new IllegalStateException("未配置 mail-system.email-domain");
        }
        return localPart + "@" + domain.trim().toLowerCase();
    }

    private SysUser createUser(String email, String password, String nickname, String introduction) {
        SysUser user = new SysUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setAvatarUrl("https://dummyimage.com/100x100/4f86f7/ffffff&text=" + nickname.substring(0, 1));
        user.setStatus(1);
        user.setIntroduction(introduction);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    private List<MailLabel> createDefaultLabels(Long userId) {
        List<MailLabel> labels = new ArrayList<>();
        labels.add(insertLabel(userId, "重要", "#F56C6C"));
        labels.add(insertLabel(userId, "项目", "#409EFF"));
        labels.add(insertLabel(userId, "待处理", "#E6A23C"));
        return labels;
    }

    private MailLabel insertLabel(Long userId, String name, String color) {
        MailLabel label = new MailLabel();
        label.setUserId(userId);
        label.setName(name);
        label.setColor(color);
        label.setCreatedAt(LocalDateTime.now());
        labelMapper.insert(label);
        return label;
    }

    private MailContactGroup createGroup(Long ownerUserId, String groupName) {
        MailContactGroup group = new MailContactGroup();
        group.setOwnerUserId(ownerUserId);
        group.setGroupName(groupName);
        group.setCreatedAt(LocalDateTime.now());
        contactGroupMapper.insert(group);
        return group;
    }

    private void createContact(Long ownerUserId, Long groupId, Long contactUserId, String name, String email) {
        MailContact contact = new MailContact();
        contact.setOwnerUserId(ownerUserId);
        contact.setGroupId(groupId);
        contact.setContactUserId(contactUserId);
        contact.setContactName(name);
        contact.setContactEmail(email);
        contact.setAvatarUrl("https://dummyimage.com/100x100/7ab8ff/ffffff&text=" + name.substring(0, 1));
        contact.setRemark("演示数据");
        contact.setCreatedAt(LocalDateTime.now());
        contact.setUpdatedAt(LocalDateTime.now());
        contactMapper.insert(contact);
    }

    private MailMessage createMessage(Long senderUserId, String subject, String html, boolean draft, LocalDateTime sentAt) {
        MailMessage message = new MailMessage();
        message.setSenderUserId(senderUserId);
        message.setSubject(subject);
        message.setContentHtml(html);
        message.setContentText(html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim());
        message.setDraftFlag(draft ? 1 : 0);
        message.setHasAttachment(0);
        message.setSentAt(sentAt);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        mailMessageMapper.insert(message);
        return message;
    }

    private void addRecipient(Long mailId, Long recipientUserId, String name, String email, String type) {
        MailRecipient recipient = new MailRecipient();
        recipient.setMailId(mailId);
        recipient.setRecipientUserId(recipientUserId);
        recipient.setRecipientName(name);
        recipient.setRecipientEmail(email);
        recipient.setRecipientType(type);
        recipient.setCreatedAt(LocalDateTime.now());
        recipientMapper.insert(recipient);
    }

    private MailUserBox addUserBox(Long mailId, Long ownerUserId, String boxType, String roleType, int readFlag, LocalDateTime time) {
        MailUserBox box = new MailUserBox();
        box.setMailId(mailId);
        box.setOwnerUserId(ownerUserId);
        box.setBoxType(boxType);
        box.setRoleType(roleType);
        box.setReadFlag(readFlag);
        box.setReadAt(readFlag == 1 ? time : null);
        box.setStarFlag(0);
        box.setDeletedFlag(0);
        box.setImportantFlag(0);
        box.setSpamFlag(0);
        box.setPriorityScore(0D);
        box.setCreatedAt(time);
        box.setUpdatedAt(time);
        userBoxMapper.insert(box);
        return box;
    }

    private void bindLabel(Long userBoxId, Long labelId) {
        if (labelId == null) {
            return;
        }
        MailUserLabel relation = new MailUserLabel();
        relation.setUserBoxId(userBoxId);
        relation.setLabelId(labelId);
        relation.setCreatedAt(LocalDateTime.now());
        userLabelMapper.insert(relation);
    }

    private Long findLabelIdByName(Long userId, String labelName) {
        MailLabel label = labelMapper.selectOne(new LambdaQueryWrapper<MailLabel>()
                .eq(MailLabel::getUserId, userId)
                .eq(MailLabel::getName, labelName)
                .last("limit 1"));
        return label == null ? null : label.getId();
    }
}
