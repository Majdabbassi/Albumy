package com.mmea.albumy.controller;

import com.mmea.albumy.model.Invite;
import com.mmea.albumy.repository.InviteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/invites")
public class InviteController {

    private final InviteRepository inviteRepository;

    public InviteController(InviteRepository inviteRepository) {
        this.inviteRepository = inviteRepository;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Boolean> validateInvite(@PathVariable String token) {
        Invite invite = inviteRepository.findByToken(token)
                .orElse(null);
        
        if (invite == null) {
            return ResponseEntity.ok(false);
        }
        
        if (invite.isUsed()) {
            return ResponseEntity.ok(false);
        }
        
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.ok(false);
        }
        
        return ResponseEntity.ok(true);
    }
}
