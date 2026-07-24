package com.mmea.albumy.controller;

import com.mmea.albumy.dto.InviteResponse;
import com.mmea.albumy.model.Invite;
import com.mmea.albumy.repository.InviteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final InviteRepository inviteRepository;

    public AdminController(InviteRepository inviteRepository) {
        this.inviteRepository = inviteRepository;
    }

    @PostMapping("/invites")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InviteResponse> createInvite() {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        
        Invite invite = new Invite();
        invite.setToken(token);
        invite.setUsed(false);
        invite.setExpiresAt(expiresAt);
        inviteRepository.save(invite);
        
        String registrationUrl = "http://localhost:4200/register?invite=" + token;
        return ResponseEntity.ok(new InviteResponse(token, registrationUrl));
    }
}
