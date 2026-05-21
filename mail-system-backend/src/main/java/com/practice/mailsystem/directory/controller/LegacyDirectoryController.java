package com.practice.mailsystem.directory.controller;

import com.practice.mailsystem.common.dto.BatchIdsRequest;
import com.practice.mailsystem.directory.dto.ContactUpsertRequest;
import com.practice.mailsystem.directory.dto.GroupUpsertRequest;
import com.practice.mailsystem.directory.service.DirectoryService;
import com.practice.mailsystem.mail.dto.LabelUpsertRequest;
import com.practice.mailsystem.mail.dto.MailListQuery;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class LegacyDirectoryController {

    private final DirectoryService directoryService;

    public LegacyDirectoryController(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping("/mail_contacts/list")
    public Map<String, Object> contacts(MailListQuery query) {
        return directoryService.listContacts(query);
    }

    @GetMapping("/mail_group/list")
    public Map<String, Object> groups() {
        return directoryService.listGroups();
    }

    @GetMapping("/mail_label/list")
    public Map<String, Object> labels() {
        return directoryService.listLabels();
    }

    @PostMapping("/mail_contacts")
    public String addContact(@Valid @RequestBody ContactUpsertRequest request) {
        directoryService.saveContact(request);
        return "success";
    }

    @PutMapping("/mail_contacts/{contactId}")
    public String updateContact(@PathVariable Long contactId, @Valid @RequestBody ContactUpsertRequest request) {
        directoryService.updateContact(contactId, request);
        return "success";
    }

    @PostMapping("/mail_contacts/delete")
    public String deleteContacts(@Valid @RequestBody BatchIdsRequest request) {
        directoryService.deleteContacts(request.ids());
        return "success";
    }

    @PostMapping("/mail_group")
    public String addGroup(@Valid @RequestBody GroupUpsertRequest request) {
        directoryService.saveGroup(request);
        return "success";
    }

    @PutMapping("/mail_group/{groupId}")
    public String updateGroup(@PathVariable Long groupId, @Valid @RequestBody GroupUpsertRequest request) {
        directoryService.updateGroup(groupId, request);
        return "success";
    }

    @DeleteMapping("/mail_group/{groupId}")
    public String deleteGroup(@PathVariable Long groupId) {
        directoryService.deleteGroup(groupId);
        return "success";
    }

    @PostMapping("/mail_label")
    public String addLabel(@Valid @RequestBody LabelUpsertRequest request) {
        directoryService.saveLabel(request);
        return "success";
    }

    @PutMapping("/mail_label/{labelId}")
    public String updateLabel(@PathVariable Long labelId, @Valid @RequestBody LabelUpsertRequest request) {
        directoryService.updateLabel(labelId, request);
        return "success";
    }

    @DeleteMapping("/mail_label/{labelId}")
    public String deleteLabel(@PathVariable Long labelId) {
        directoryService.deleteLabel(labelId);
        return "success";
    }
}
