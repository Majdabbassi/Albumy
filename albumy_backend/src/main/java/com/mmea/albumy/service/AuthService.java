package com.mmea.albumy.service;

import com.mmea.albumy.dto.LoginRequest;
import com.mmea.albumy.dto.LoginResponse;
import com.mmea.albumy.dto.RegisterRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    LoginResponse register(RegisterRequest request);
}
