package com.mmea.albumy.controller;

import com.mmea.albumy.dto.InviteValidationResponse;
import com.mmea.albumy.service.InviteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/invites")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<InviteValidationResponse> validateInvite(@PathVariable String token) {
        return ResponseEntity.ok(inviteService.validate(token));
    }
}