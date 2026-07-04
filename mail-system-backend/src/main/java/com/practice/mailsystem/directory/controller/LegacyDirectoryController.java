package com.practice.mailsystem.directory.controller;

import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.common.dto.BatchIdsRequest;
import com.practice.mailsystem.directory.dto.ContactUpsertRequest;
import com.practice.mailsystem.directory.dto.GroupUpsertRequest;
import com.practice.mailsystem.directory.service.DirectoryService;
import com.practice.mailsystem.mail.dto.LabelUpsertRequest;
import com.practice.mailsystem.mail.dto.MailListQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 旧版通讯录/标签路径，响应格式已统一为 {@link ApiResponse}。
 *
 * @deprecated 请迁移到 {@link com.practice.mailsystem.directory.controller.DirectoryManageController}（/api/**）。
 */
@Deprecated
@RestController
@RequiredArgsConstructor
public class LegacyDirectoryController {

    private final DirectoryService directoryService;

    @GetMapping("/mail_contacts/list")
    public ApiResponse<Map<String, Object>> contacts(MailListQuery query) {
        return ApiResponse.ok(directoryService.listContacts(query));
    }

    @GetMapping("/mail_group/list")
    public ApiResponse<Map<String, Object>> groups() {
        return ApiResponse.ok(directoryService.listGroups());
    }

    @GetMapping("/mail_label/list")
    public ApiResponse<Map<String, Object>> labels() {
        return ApiResponse.ok(directoryService.listLabels());
    }

    @PostMapping("/mail_contacts")
    public ApiResponse<Void> addContact(@Valid @RequestBody ContactUpsertRequest request) {
        directoryService.saveContact(request);
        return ApiResponse.ok();
    }

    @PutMapping("/mail_contacts/{contactId}")
    public ApiResponse<Void> updateContact(@PathVariable Long contactId, @Valid @RequestBody ContactUpsertRequest request) {
        directoryService.updateContact(contactId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/mail_contacts/delete")
    public ApiResponse<Void> deleteContacts(@Valid @RequestBody BatchIdsRequest request) {
        directoryService.deleteContacts(request.ids());
        return ApiResponse.ok();
    }

    @PostMapping("/mail_group")
    public ApiResponse<Void> addGroup(@Valid @RequestBody GroupUpsertRequest request) {
        directoryService.saveGroup(request);
        return ApiResponse.ok();
    }

    @PutMapping("/mail_group/{groupId}")
    public ApiResponse<Void> updateGroup(@PathVariable Long groupId, @Valid @RequestBody GroupUpsertRequest request) {
        directoryService.updateGroup(groupId, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/mail_group/{groupId}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long groupId) {
        directoryService.deleteGroup(groupId);
        return ApiResponse.ok();
    }

    @PostMapping("/mail_label")
    public ApiResponse<Void> addLabel(@Valid @RequestBody LabelUpsertRequest request) {
        directoryService.saveLabel(request);
        return ApiResponse.ok();
    }

    @PutMapping("/mail_label/{labelId}")
    public ApiResponse<Void> updateLabel(@PathVariable Long labelId, @Valid @RequestBody LabelUpsertRequest request) {
        directoryService.updateLabel(labelId, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/mail_label/{labelId}")
    public ApiResponse<Void> deleteLabel(@PathVariable Long labelId) {
        directoryService.deleteLabel(labelId);
        return ApiResponse.ok();
    }
}
