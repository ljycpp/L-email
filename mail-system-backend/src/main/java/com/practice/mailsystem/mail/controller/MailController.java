package com.practice.mailsystem.mail.controller;

import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.common.PageResult;
import com.practice.mailsystem.common.dto.BatchIdsRequest;
import com.practice.mailsystem.mail.dto.LabelMarkRequest;
import com.practice.mailsystem.mail.dto.MailCreateRequest;
import com.practice.mailsystem.mail.dto.MailListQuery;
import com.practice.mailsystem.mail.service.MailService;
import com.practice.mailsystem.mail.vo.DraftItemVO;
import com.practice.mailsystem.mail.vo.InboxItemVO;
import com.practice.mailsystem.mail.vo.MailDetailVO;
import com.practice.mailsystem.mail.vo.MailListItemVO;
import com.practice.mailsystem.mail.vo.OutboxItemVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 邮件核心 REST API
 * 基础路径：/api/mails
 */
@RestController
@RequestMapping("/api/mails")
@Validated
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    // ── 写操作 ──────────────────────────────────────────────────────────

    @PostMapping("/send")
    public ApiResponse<Long> send(@Valid @RequestBody MailCreateRequest request) {
        return ApiResponse.ok(mailService.send(request));
    }

    @PostMapping("/drafts")
    public ApiResponse<Long> saveDraft(@Valid @RequestBody MailCreateRequest request) {
        return ApiResponse.ok(mailService.saveDraft(request));
    }

    @PutMapping("/{mailId}/read")
    public ApiResponse<Void> markRead(@PathVariable @Positive Long mailId) {
        mailService.markRead(mailId);
        return ApiResponse.ok();
    }

    @PutMapping("/{mailId}/star")
    public ApiResponse<Void> toggleStar(@PathVariable @Positive Long mailId) {
        mailService.toggleStar(mailId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{mailId}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long mailId) {
        mailService.deleteMail(mailId);
        return ApiResponse.ok();
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(@Valid @RequestBody BatchIdsRequest request) {
        mailService.deleteMails(request.ids(), request.boxType());
        return ApiResponse.ok();
    }

    @PostMapping("/batch-restore")
    public ApiResponse<Void> batchRestore(@Valid @RequestBody BatchIdsRequest request) {
        mailService.undoDeleteMails(request.ids(), request.boxType());
        return ApiResponse.ok();
    }

    @PostMapping("/labels/mark")
    public ApiResponse<Void> markLabel(@Valid @RequestBody LabelMarkRequest request) {
        mailService.markLabel(request.labelId(), request.mailIds(), request.boxType());
        return ApiResponse.ok();
    }

    // ── 读操作 ──────────────────────────────────────────────────────────

    @GetMapping("/inbox")
    public ApiResponse<PageResult<InboxItemVO>> inbox(@Valid MailListQuery query) {
        return ApiResponse.ok(mailService.listInbox(query));
    }

    @GetMapping("/outbox")
    public ApiResponse<PageResult<OutboxItemVO>> outbox(@Valid MailListQuery query) {
        return ApiResponse.ok(mailService.listOutbox(query));
    }

    @GetMapping("/drafts")
    public ApiResponse<PageResult<DraftItemVO>> drafts(@Valid MailListQuery query) {
        return ApiResponse.ok(mailService.listDrafts(query));
    }

    @GetMapping("/{mailId}")
    public ApiResponse<MailDetailVO> detail(@PathVariable @Positive Long mailId,
                                            @RequestParam(required = false) String mailType) {
        return ApiResponse.ok(mailService.getDetail(mailId, mailType));
    }

    // ── 统计 & 批量状态 ─────────────────────────────────────────────────

    @GetMapping("/inbox/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("count", mailService.countUnreadInbox()));
    }

    @PutMapping("/inbox/read-all")
    public ApiResponse<Void> markAllRead() {
        mailService.markAllInboxRead();
        return ApiResponse.ok();
    }

    @GetMapping("/spam/count")
    public ApiResponse<Map<String, Long>> spamCount() {
        return ApiResponse.ok(Map.of("count", mailService.countSpam()));
    }

    @PostMapping("/spam/report")
    public ApiResponse<Void> reportSpam(@Valid @RequestBody BatchIdsRequest request) {
        mailService.markAsSpam(request.ids());
        return ApiResponse.ok();
    }

    @PostMapping("/spam/cancel")
    public ApiResponse<Void> cancelSpam(@Valid @RequestBody BatchIdsRequest request) {
        mailService.unmarkSpam(request.ids());
        return ApiResponse.ok();
    }

    @PostMapping("/batch-star")
    public ApiResponse<Void> batchStar(@Valid @RequestBody BatchIdsRequest request) {
        mailService.toggleStars(request.ids(), request.boxType());
        return ApiResponse.ok();
    }

    @PostMapping("/batch-delete-permanently")
    public ApiResponse<Void> batchDeletePermanently(@Valid @RequestBody BatchIdsRequest request) {
        mailService.deletePermanently(request.ids(), request.boxType());
        return ApiResponse.ok();
    }

    @GetMapping("/trash")
    public ApiResponse<PageResult<MailListItemVO>> trash(@Valid MailListQuery query,
                                                         @RequestParam Map<String, String> params) {
        return ApiResponse.ok(mailService.listByMailList(query, params));
    }
}
