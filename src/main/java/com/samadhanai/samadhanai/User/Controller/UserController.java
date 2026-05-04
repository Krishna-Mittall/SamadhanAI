package com.samadhanai.samadhanai.User.Controller;

import com.samadhanai.samadhanai.Common.Response.ApiResponse;
import com.samadhanai.samadhanai.Complaint.Service.ComplaintService;
import com.samadhanai.samadhanai.User.Dto.UserDTOs;
import com.samadhanai.samadhanai.User.Service.UserService;
import com.samadhanai.samadhanai.User.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService      userService;
    private final ComplaintService complaintService;  // ✅ use Service not raw Repository

    // ─── GET /api/user/profile ────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse> getProfile(
            @AuthenticationPrincipal User currentUser) {

        UserDTOs.UserResponse result = userService.getProfile(currentUser.getId());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message("Profile fetched")
                .data(result).timestamp(LocalDateTime.now()).build());
    }

    // ─── PUT /api/user/profile ────────────────────────────
    // Update name only (no password here)
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse> updateProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UserDTOs.UpdateProfileRequest dto) {

        UserDTOs.UserResponse result =
                userService.updateProfile(currentUser.getId(), dto);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message("Profile updated successfully")
                .data(result).timestamp(LocalDateTime.now()).build());
    }

    // ─── PUT /api/user/change-password ───────────────────
    // ✅ NEW: Separate endpoint for password change
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse> changePassword(
            @AuthenticationPrincipal User currentUser,
            @RequestBody UserDTOs.ChangePasswordRequest dto) {

        userService.changePassword(currentUser.getId(), dto);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message("Password changed successfully")
                .timestamp(LocalDateTime.now()).build());
    }

    // ─── GET /api/user/complaints ─────────────────────────
    // ✅ FIXED: returns DTOs not raw Entity
    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse> getMyComplaints(
            @AuthenticationPrincipal User currentUser) {

        var complaints = complaintService
                .getComplaintsByUserId(currentUser.getId());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message("Your complaints fetched")
                .data(complaints).timestamp(LocalDateTime.now()).build());
    }
}