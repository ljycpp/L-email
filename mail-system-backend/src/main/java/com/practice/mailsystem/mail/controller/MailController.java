package com.practice.mailsystem.mail.controller;

import com.practice.mailsystem.attachment.service.AttachmentService;
import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.common.dto.BatchIdsRequest;
import com.practice.mailsystem.common.PageResult;
import com.practice.mailsystem.mail.dto.LegacyMailFormRequest;
import com.practice.mailsystem.mail.dto.LabelMarkRequest;
import com.practice.mailsystem.mail.dto.MailCreateRequest;
import com.practice.mailsystem.mail.dto.MailListQuery;
import com.practice.mailsystem.mail.service.MailService;
import com.practice.mailsystem.mail.util.LegacyMailFormParser;
import com.practice.mailsystem.mail.vo.DraftItemVO;
import com.practice.mailsystem.mail.vo.InboxItemVO;
import com.practice.mailsystem.mail.vo.MailDetailVO;
import com.practice.mailsystem.mail.vo.MailListItemVO;
import com.practice.mailsystem.mail.vo.OutboxItemVO;
import jakarta.validation.Valid;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class MailController {

    private final MailService mailService;
    private final AttachmentService attachmentService;
    private final LegacyMailFormParser legacyMailFormParser;

    public MailController(MailService mailService,
                          AttachmentService attachmentService,
                          LegacyMailFormParser legacyMailFormParser) {
        this.mailService = mailService;
        this.attachmentService = attachmentService;
        this.legacyMailFormParser = legacyMailFormParser;
    }

    @PostMapping("/api/mails/send")
    public ApiResponse<Long> send(@Valid @RequestBody MailCreateRequest request) {
        return ApiResponse.ok(mailService.send(request));
    }

    @PostMapping("/api/mails/drafts")
    public ApiResponse<Long> saveDraft(@Valid @RequestBody MailCreateRequest request) {
        return ApiResponse.ok(mailService.saveDraft(request));
    }

    @PostMapping("/api/mails/send-form")
    public ApiResponse<Long> sendLegacyForm(@RequestParam MultiValueMap<String, String> formData,
                                            @RequestParam(required = false) MultiValueMap<String, MultipartFile> fileMap) {
        return ApiResponse.ok(mailService.sendLegacyForm(parseLegacyRequest(formData, fileMap)));
    }

    @PostMapping("/api/mails/drafts-form")
    public ApiResponse<Long> saveLegacyDraft(@RequestParam MultiValueMap<String, String> formData,
                                             @RequestParam(required = false) MultiValueMap<String, MultipartFile> fileMap) {
        return ApiResponse.ok(mailService.saveLegacyDraft(parseLegacyRequest(formData, fileMap)));
    }

    @PutMapping("/api/mails/{mailId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long mailId) {
        mailService.markRead(mailId);
        return ApiResponse.ok();
    }

    @PutMapping("/api/mails/{mailId}/star")
    public ApiResponse<Void> toggleStar(@PathVariable Long mailId) {
        mailService.toggleStar(mailId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/mails/{mailId}")
    public ApiResponse<Void> delete(@PathVariable Long mailId) {
        mailService.deleteMail(mailId);
        return ApiResponse.ok();
    }

    @PostMapping("/api/mails/batch-delete")
    public ApiResponse<Void> batchDelete(@Valid @RequestBody BatchIdsRequest request) {
        mailService.deleteMails(request.ids());
        return ApiResponse.ok();
    }

    @PostMapping("/api/mails/batch-restore")
    public ApiResponse<Void> batchRestore(@Valid @RequestBody BatchIdsRequest request) {
        mailService.undoDeleteMails(request.ids());
        return ApiResponse.ok();
    }

    @PostMapping("/api/mails/labels/mark")
    public ApiResponse<Void> markLabel(@Valid @RequestBody LabelMarkRequest request) {
        mailService.markLabel(request.labelId(), request.mailIds());
        return ApiResponse.ok();
    }

    @GetMapping("/api/mails/inbox")
    public ApiResponse<PageResult<InboxItemVO>> apiInbox(MailListQuery query) {
        return ApiResponse.ok(mailService.listInbox(query));
    }

    @GetMapping("/api/mails/outbox")
    public ApiResponse<PageResult<OutboxItemVO>> apiOutbox(MailListQuery query) {
        return ApiResponse.ok(mailService.listOutbox(query));
    }

    @GetMapping("/api/mails/drafts")
    public ApiResponse<PageResult<DraftItemVO>> apiDrafts(MailListQuery query) {
        return ApiResponse.ok(mailService.listDrafts(query));
    }

    @GetMapping("/api/mails/{mailId}")
    public ApiResponse<MailDetailVO> apiDetail(@PathVariable Long mailId, @RequestParam String mailType) {
        return ApiResponse.ok(mailService.getDetail(mailId, mailType));
    }

    @GetMapping("/inbox/list")
    public PageResult<InboxItemVO> inbox(MailListQuery query) {
        return mailService.listInbox(query);
    }

    @GetMapping("/inbox/unread_count")
    public Map<String, Long> inboxUnreadCount() {
        return Map.of("count", mailService.countUnreadInbox());
    }

    @GetMapping("/spam/list")
    public PageResult<InboxItemVO> spamList(MailListQuery query) {
        query.setSpam(1);
        return mailService.listInbox(query);
    }

    @GetMapping("/spam/count")
    public Map<String, Long> spamCount() {
        return Map.of("count", mailService.countSpam());
    }

    @PostMapping("/inbox/mark_all_read")
    public String markAllInboxReadLegacy() {
        mailService.markAllInboxRead();
        return "success";
    }

    @PostMapping("/mail/report_spam")
    public String reportSpamLegacy(@Valid @RequestBody BatchIdsRequest request) {
        mailService.markAsSpam(request.ids());
        return "success";
    }

    @PostMapping("/mail/not_spam")
    public String notSpamLegacy(@Valid @RequestBody BatchIdsRequest request) {
        mailService.unmarkSpam(request.ids());
        return "success";
    }

    @GetMapping("/outbox/list")
    public PageResult<OutboxItemVO> outbox(MailListQuery query) {
        return mailService.listOutbox(query);
    }

    @GetMapping("/draftbox/list")
    public PageResult<DraftItemVO> drafts(MailListQuery query) {
        return mailService.listDrafts(query);
    }

    @GetMapping("/mail_detail")
    public MailDetailVO detail(@RequestParam Long mailId, @RequestParam String mailType) {
        return mailService.getDetail(mailId, mailType);
    }

    @GetMapping("/mail_list")
    public PageResult<MailListItemVO> mailList(MailListQuery query, @RequestParam Map<String, String> params) {
        return mailService.listByMailList(query, params);
    }

    @PostMapping("/mail_label/toggle_star")
    public String toggleStarLegacy(@Valid @RequestBody BatchIdsRequest request) {
        mailService.toggleStars(request.ids());
        return "success";
    }

    @PostMapping("/mail_label/mark")
    public String markLabelLegacy(@Valid @RequestBody LabelMarkRequest request) {
        mailService.markLabel(request.labelId(), request.mailIds());
        return "success";
    }

    @PostMapping("/mail/delete")
    public String deleteMailLegacy(@Valid @RequestBody BatchIdsRequest request) {
        mailService.deleteMails(request.ids());
        return "success";
    }

    @PostMapping("/mail/restore")
    public String restoreMailLegacy(@Valid @RequestBody BatchIdsRequest request) {
        mailService.undoDeleteMails(request.ids());
        return "success";
    }

    @PostMapping("/mail/delete_permanently")
    public String deletePermanentlyLegacy(@Valid @RequestBody BatchIdsRequest request) {
        mailService.deletePermanently(request.ids());
        return "success";
    }

    @PostMapping("/mail_send/send")
    public String legacySend(@RequestParam MultiValueMap<String, String> formData,
                             @RequestParam(required = false) MultiValueMap<String, MultipartFile> fileMap) {
        mailService.sendLegacyForm(parseLegacyRequest(formData, fileMap));
        return "success";
    }

    @PostMapping("/mail_send/draft")
    public String legacyDraft(@RequestParam MultiValueMap<String, String> formData,
                              @RequestParam(required = false) MultiValueMap<String, MultipartFile> fileMap) {
        mailService.saveLegacyDraft(parseLegacyRequest(formData, fileMap));
        return "success";
    }

    private LegacyMailFormRequest parseLegacyRequest(MultiValueMap<String, String> formData,
                                                     MultiValueMap<String, MultipartFile> fileMap) {
        List<Long> uploadedAttachmentIds = new ArrayList<>();
        if (fileMap != null && !fileMap.isEmpty()) {
            for (List<MultipartFile> files : fileMap.values()) {
                for (MultipartFile file : files) {
                    if (file != null && !file.isEmpty()) {
                        uploadedAttachmentIds.add(attachmentService.upload(file).id());
                    }
                }
            }
        }
        return legacyMailFormParser.parse(formData, uploadedAttachmentIds);
    }
}
