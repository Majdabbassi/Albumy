package com.mmea.albumy.controller;

import com.mmea.albumy.dto.InviteResponse;
import com.mmea.albumy.service.InviteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/invites")
public class AdminInviteController {

    private final InviteService inviteService;

    public AdminInviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping
    public ResponseEntity<InviteResponse> createInvite() {
        return ResponseEntity.ok(inviteService.createInvite());
    }

    @GetMapping
    public ResponseEntity<List<InviteResponse>> listInvites() {
        return ResponseEntity.ok(inviteService.listInvites());
    }
}