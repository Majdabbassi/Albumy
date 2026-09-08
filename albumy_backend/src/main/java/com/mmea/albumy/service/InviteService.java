package com.mmea.albumy.service;

import com.mmea.albumy.dto.InviteResponse;
import com.mmea.albumy.dto.InviteValidationResponse;
import com.mmea.albumy.model.Invite;

import java.util.List;

public interface InviteService {
    InviteResponse createInvite();
    List<InviteResponse> listInvites();
    InviteValidationResponse validate(String token);
    Invite consume(String token);
}