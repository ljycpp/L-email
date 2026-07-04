package com.practice.mailsystem.mail.controller;

import com.practice.mailsystem.attachment.service.AttachmentService;
import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.common.PageResult;
import com.practice.mailsystem.common.dto.BatchIdsRequest;
import com.practice.mailsystem.mail.dto.LabelMarkRequest;
import com.practice.mailsystem.mail.dto.LegacyMailFormRequest;
import com.practice.mailsystem.mail.dto.MailListQuery;
import com.practice.mailsystem.mail.service.MailService;
import com.practice.mailsystem.mail.util.LegacyMailFormParser;
import com.practice.mailsystem.mail.vo.DraftItemVO;
import com.practice.mailsystem.mail.vo.InboxItemVO;
import com.practice.mailsystem.mail.vo.MailDetailVO;
import com.practice.mailsystem.mail.vo.MailListItemVO;
import com.practice.mailsystem.mail.vo.OutboxItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 旧版路径兼容接口，响应格式已统一为 {@link ApiResponse}。
 *
 * @deprecated 请迁移到 {@link MailController}（/api/mails/**）。
 */
@Deprecated
@RestController
@RequiredArgsConstructor
public class LegacyMailController {

    private final MailService mailService;
    private final AttachmentService attachmentService;
    private final LegacyMailFormParser legacyMailFormParser;

    @PostMapping("/api/mails/send-form")
    public ApiResponse<Long> sendForm(@RequestParam MultiValueMap<String, String> formData,
                                      @RequestParam(required = false) MultiValueMap<String, MultipartFile> fileMap) {
        return ApiResponse.ok(mailService.sendLegacyForm(parseLegacyRequest(formData, fileMap)));
    }

    @PostMapping("/api/mails/drafts-form")
    public ApiResponse<Long> saveDraftForm(@RequestParam MultiValueMap<String, String> formData,
                                           @RequestParam(required = false) MultiValueMap<String, MultipartFile> fileMap) {
        return ApiResponse.ok(mailService.saveLegacyDraft(parseLegacyRequest(formData, fileMap)));
    }

    @PostMapping("/mail_send/send")
    public ApiResponse<Void> legacySend(@RequestParam MultiValueMap<String, String> formData,
                                        @RequestParam(required = false) MultiValueMap<String, MultipartFile> fileMap) {
        mailService.sendLegacyForm(parseLegacyRequest(formData, fileMap));
        return ApiResponse.ok();
    }

    @PostMapping("/mail_send/draft")
    public ApiResponse<Void> legacyDraft(@RequestParam MultiValueMap<String, String> formData,
                                         @RequestParam(required = false) MultiValueMap<String, MultipartFile> fileMap) {
        mailService.saveLegacyDraft(parseLegacyRequest(formData, fileMap));
        return ApiResponse.ok();
    }

    @GetMapping("/inbox/list")
    public ApiResponse<PageResult<InboxItemVO>> inbox(MailListQuery query) {
        return ApiResponse.ok(mailService.listInbox(query));
    }

    @GetMapping("/inbox/unread_count")
    public ApiResponse<Map<String, Long>> inboxUnreadCount() {
        return ApiResponse.ok(Map.of("count", mailService.countUnreadInbox()));
    }

    @PostMapping("/inbox/mark_all_read")
    public ApiResponse<Void> markAllInboxRead() {
        mailService.markAllInboxRead();
        return ApiResponse.ok();
    }

    @GetMapping("/spam/list")
    public ApiResponse<PageResult<InboxItemVO>> spamList(MailListQuery query) {
        query.setSpam(1);
        return ApiResponse.ok(mailService.listInbox(query));
    }

    @GetMapping("/spam/count")
    public ApiResponse<Map<String, Long>> spamCount() {
        return ApiResponse.ok(Map.of("count", mailService.countSpam()));
    }

    @PostMapping("/mail/report_spam")
    public ApiResponse<Void> reportSpam(@Valid @RequestBody BatchIdsRequest request) {
        mailService.markAsSpam(request.ids());
        return ApiResponse.ok();
    }

    @PostMapping("/mail/not_spam")
    public ApiResponse<Void> notSpam(@Valid @RequestBody BatchIdsRequest request) {
        mailService.unmarkSpam(request.ids());
        return ApiResponse.ok();
    }

    @GetMapping("/outbox/list")
    public ApiResponse<PageResult<OutboxItemVO>> outbox(MailListQuery query) {
        return ApiResponse.ok(mailService.listOutbox(query));
    }

    @GetMapping("/draftbox/list")
    public ApiResponse<PageResult<DraftItemVO>> drafts(MailListQuery query) {
        return ApiResponse.ok(mailService.listDrafts(query));
    }

    @GetMapping("/mail_detail")
    public ApiResponse<MailDetailVO> detail(@RequestParam Long mailId, @RequestParam String mailType) {
        return ApiResponse.ok(mailService.getDetail(mailId, mailType));
    }

    @GetMapping("/mail_list")
    public ApiResponse<PageResult<MailListItemVO>> mailList(MailListQuery query, @RequestParam Map<String, String> params) {
        return ApiResponse.ok(mailService.listByMailList(query, params));
    }

    @PostMapping("/mail_label/toggle_star")
    public ApiResponse<Void> toggleStar(@Valid @RequestBody BatchIdsRequest request) {
        mailService.toggleStars(request.ids(), request.boxType());
        return ApiResponse.ok();
    }

    @PostMapping("/mail_label/mark")
    public ApiResponse<Void> markLabel(@Valid @RequestBody LabelMarkRequest request) {
        mailService.markLabel(request.labelId(), request.mailIds(), request.boxType());
        return ApiResponse.ok();
    }

    @PostMapping("/mail/delete")
    public ApiResponse<Void> deleteMail(@Valid @RequestBody BatchIdsRequest request) {
        mailService.deleteMails(request.ids(), request.boxType());
        return ApiResponse.ok();
    }

    @PostMapping("/mail/restore")
    public ApiResponse<Void> restoreMail(@Valid @RequestBody BatchIdsRequest request) {
        mailService.undoDeleteMails(request.ids(), request.boxType());
        return ApiResponse.ok();
    }

    @PostMapping("/mail/delete_permanently")
    public ApiResponse<Void> deletePermanently(@Valid @RequestBody BatchIdsRequest request) {
        mailService.deletePermanently(request.ids(), request.boxType());
        return ApiResponse.ok();
    }

    private LegacyMailFormRequest parseLegacyRequest(MultiValueMap<String, String> formData,
                                                     MultiValueMap<String, MultipartFile> fileMap) {
        List<Long> uploadedIds = new ArrayList<>();
        if (fileMap != null && !fileMap.isEmpty()) {
            for (List<MultipartFile> files : fileMap.values()) {
                for (MultipartFile file : files) {
                    if (file != null && !file.isEmpty()) {
                        uploadedIds.add(attachmentService.upload(file).id());
                    }
                }
            }
        }
        return legacyMailFormParser.parse(formData, uploadedIds);
    }
}
