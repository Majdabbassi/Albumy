package com.mmea.albumy.controller;

import com.mmea.albumy.dto.*;
import com.mmea.albumy.model.Invite;
import com.mmea.albumy.model.User;
import com.mmea.albumy.repository.InviteRepository;
import com.mmea.albumy.repository.UserRepository;
import com.mmea.albumy.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                          UserRepository userRepository, InviteRepository inviteRepository,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails.getUsername());
        
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), user.getRole().name()));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        Invite invite = inviteRepository.findByToken(request.getInviteToken())
                .orElseThrow(() -> new RuntimeException("Invalid invite token"));
        
        if (invite.isUsed()) {
            throw new RuntimeException("Invite token has already been used");
        }
        
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Invite token has expired");
        }
        
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setDisplayName(request.getDisplayName());
        user.setRole(User.Role.ORGANIZER);
        userRepository.save(user);
        
        invite.setUsed(true);
        inviteRepository.save(invite);
        
        return ResponseEntity.ok("Registration successful");
    }
}
