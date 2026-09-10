package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.dto.InviteResponse;
import com.mmea.albumy.dto.InviteValidationResponse;
import com.mmea.albumy.exception.ApiException;
import com.mmea.albumy.model.Invite;
import com.mmea.albumy.repository.InviteRepository;
import com.mmea.albumy.service.InviteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InviteServiceImpl implements InviteService {

    private static final int MAX_INVITES = 100;

    private final InviteRepository inviteRepository;
    private final String frontendUrl;
    private final long expirationDays;

    public InviteServiceImpl(InviteRepository inviteRepository,
                             @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
                             @Value("${invite.expiration.days:7}") long expirationDays) {
        this.inviteRepository = inviteRepository;
        this.frontendUrl = frontendUrl;
        this.expirationDays = expirationDays;
    }

    @Override
    public InviteResponse createInvite() {
        Invite invite = new Invite();
        invite.setToken(UUID.randomUUID().toString());
        invite.setUsed(false);
        invite.setExpiresAt(LocalDateTime.now().plusDays(expirationDays));
        inviteRepository.save(invite);
        return toResponse(invite);
    }

    @Override
    public List<InviteResponse> listInvites() {
        return inviteRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, MAX_INVITES)).getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public InviteValidationResponse validate(String token) {
        if (token == null || token.trim().isEmpty()) {
            return new InviteValidationResponse(false, "Missing invite token");
        }
        Invite invite = inviteRepository.findByToken(token).orElse(null);
        if (invite == null) {
            return new InviteValidationResponse(false, "Invalid invite link");
        }
        if (invite.isUsed()) {
            return new InviteValidationResponse(false, "This invite has already been used");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            return new InviteValidationResponse(false, "This invite has expired");
        }
        return new InviteValidationResponse(true, "Invite is valid");
    }

    @Override
    @Transactional
    public Invite consume(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw ApiException.badRequest("Registration requires a valid invite link");
        }
        Invite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> ApiException.badRequest("Invalid invite link"));
        if (invite.isUsed()) {
            throw ApiException.badRequest("This invite has already been used");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("This invite has expired");
        }
        // Atomic conditional update: two concurrent registrations can't both win.
        int updated = inviteRepository.markUsedIfUnused(token, LocalDateTime.now());
        if (updated == 0) {
            throw ApiException.badRequest("This invite has already been used");
        }
        invite.setUsed(true);
        return invite;
    }

    private InviteResponse toResponse(Invite invite) {
        return new InviteResponse(
                invite.getToken(),
                frontendUrl + "/register?invite=" + invite.getToken(),
                invite.isUsed(),
                invite.getCreatedAt(),
                invite.getExpiresAt()
        );
    }
}