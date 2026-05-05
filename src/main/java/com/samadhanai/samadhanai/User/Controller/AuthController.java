package com.samadhanai.samadhanai.User.Controller;

import com.samadhanai.samadhanai.Common.Response.ApiResponse;
import com.samadhanai.samadhanai.User.Dto.UserDTOs;
import com.samadhanai.samadhanai.User.Service.UserService;
import com.samadhanai.samadhanai.User.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    // ─── POST /api/auth/register ──────────────────────────
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(
            @Valid @RequestBody UserDTOs.RegisterRequest dto) {

        UserDTOs.AuthResponse result = userService.register(dto);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Registration successful!")
                .data(result)
                .timestamp(LocalDateTime.now())
                .build());
    }

    // ─── POST /api/auth/login ─────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(
            @Valid @RequestBody UserDTOs.LoginRequest dto) {

        UserDTOs.AuthResponse result = userService.login(dto);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Login successful!")
                .data(result)
                .timestamp(LocalDateTime.now())
                .build());
    }

    // ─── GET /api/auth/me ─────────────────────────────────
    // Returns current logged-in user info from JWT
    @GetMapping("/me")
    public ResponseEntity<ApiResponse> getCurrentUser(
            @AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.builder()
                    .success(false)
                    .message("Unauthorized")
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        UserDTOs.UserResponse result = userService.getProfile(currentUser.getId());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("OK")
                .data(result)
                .timestamp(LocalDateTime.now())
                .build());
    }
}