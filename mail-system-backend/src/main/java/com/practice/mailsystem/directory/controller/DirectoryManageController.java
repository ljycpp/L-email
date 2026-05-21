package com.practice.mailsystem.directory.controller;

import com.practice.mailsystem.common.ApiResponse;
import com.practice.mailsystem.common.dto.BatchIdsRequest;
import com.practice.mailsystem.directory.dto.ContactUpsertRequest;
import com.practice.mailsystem.directory.dto.GroupUpsertRequest;
import com.practice.mailsystem.directory.service.DirectoryService;
import com.practice.mailsystem.mail.dto.LabelUpsertRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DirectoryManageController {

    private final DirectoryService directoryService;

    public DirectoryManageController(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping("/contacts")
    public ApiResponse<Map<String, Object>> listContacts(com.practice.mailsystem.mail.dto.MailListQuery query) {
        return ApiResponse.ok(directoryService.listContacts(query));
    }

    @GetMapping("/contact-groups")
    public ApiResponse<Map<String, Object>> listGroups() {
        return ApiResponse.ok(directoryService.listGroups());
    }

    @GetMapping("/contact-groups/{groupId}/contacts")
    public ApiResponse<Map<String, Object>> listGroupContacts(@PathVariable Long groupId) {
        return ApiResponse.ok(directoryService.listContactsByGroup(groupId));
    }

    @GetMapping("/labels")
    public ApiResponse<Map<String, Object>> listLabels() {
        return ApiResponse.ok(directoryService.listLabels());
    }

    @PostMapping("/contacts")
    public ApiResponse<Long> addContact(@Valid @RequestBody ContactUpsertRequest request) {
        return ApiResponse.ok(directoryService.saveContact(request));
    }

    @PutMapping("/contacts/{contactId}")
    public ApiResponse<Long> updateContact(@PathVariable Long contactId, @Valid @RequestBody ContactUpsertRequest request) {
        return ApiResponse.ok(directoryService.updateContact(contactId, request));
    }

    @DeleteMapping("/contacts")
    public ApiResponse<Void> deleteContacts(@Valid @RequestBody BatchIdsRequest request) {
        directoryService.deleteContacts(request.ids());
        return ApiResponse.ok();
    }

    @PostMapping("/contact-groups")
    public ApiResponse<Long> addGroup(@Valid @RequestBody GroupUpsertRequest request) {
        return ApiResponse.ok(directoryService.saveGroup(request));
    }

    @PutMapping("/contact-groups/{groupId}")
    public ApiResponse<Long> updateGroup(@PathVariable Long groupId, @Valid @RequestBody GroupUpsertRequest request) {
        return ApiResponse.ok(directoryService.updateGroup(groupId, request));
    }

    @DeleteMapping("/contact-groups/{groupId}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long groupId) {
        directoryService.deleteGroup(groupId);
        return ApiResponse.ok();
    }

    @PostMapping("/labels")
    public ApiResponse<Long> addLabel(@Valid @RequestBody LabelUpsertRequest request) {
        return ApiResponse.ok(directoryService.saveLabel(request));
    }

    @PutMapping("/labels/{labelId}")
    public ApiResponse<Long> updateLabel(@PathVariable Long labelId, @Valid @RequestBody LabelUpsertRequest request) {
        return ApiResponse.ok(directoryService.updateLabel(labelId, request));
    }

    @DeleteMapping("/labels/{labelId}")
    public ApiResponse<Void> deleteLabel(@PathVariable Long labelId) {
        directoryService.deleteLabel(labelId);
        return ApiResponse.ok();
    }
}
