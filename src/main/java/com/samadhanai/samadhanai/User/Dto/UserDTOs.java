package com.samadhanai.samadhanai.User.Dto;

import com.samadhanai.samadhanai.Common.Enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

public class UserDTOs {

    // ─── Register Request ─────────────────────────────────
    @Data
    public static class RegisterRequest {

        @NotBlank(message = "Name is required")
        private String name;

        @Email(message = "Valid email required")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;
    }

    // ─── Login Request ────────────────────────────────────
    @Data
    public static class LoginRequest {

        @Email(message = "Valid email required")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    // ─── Auth Response ────────────────────────────────────
    @Data
    @Builder
    public static class AuthResponse {
        private String   token;
        private String   type;
        private Long     id;
        private String   name;
        private String   email;
        private UserRole role;
    }

    // ─── User Response ────────────────────────────────────
    @Data
    @Builder
    public static class UserResponse {
        private Long          id;
        private String        name;
        private String        email;
        private UserRole      role;
        private boolean       enabled;
        private LocalDateTime createdAt;
        private LocalDateTime lastLoginAt;
        private int           totalComplaints;
        private int           resolvedComplaints;
        private int           pendingComplaints;
        private int           inProgressComplaints;
    }

    // ─── Update Profile Request ───────────────────────────
    @Data
    public static class UpdateProfileRequest {

        @NotBlank(message = "Name is required")
        private String name;

        private String phone; // optional
    }

    // ─── Change Password Request ──────────────────────────
    // ✅ NEW: separate DTO for password change endpoint
    @Data
    public static class ChangePasswordRequest {

        @NotBlank(message = "Current password is required")
        private String currentPassword;  // ✅ matches profile.js oldPassword → mapped here

        @NotBlank(message = "New password is required")
        @Size(min = 6, message = "New password must be at least 6 characters")
        private String newPassword;
    }
}