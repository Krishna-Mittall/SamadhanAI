package com.samadhanai.samadhanai.User.Controller;

import com.samadhanai.samadhanai.Common.Response.ApiResponse;
import com.samadhanai.samadhanai.Complaint.Repository.ComplaintRepository;
import com.samadhanai.samadhanai.User.Dto.UserDTOs;
import com.samadhanai.samadhanai.User.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService         userService;
    private final ComplaintRepository complaintRepository;

    // ─── GET /api/admin/users ─────────────────────────────
    // Feature 5: All registered users list
    @GetMapping("/users")
    public ResponseEntity<ApiResponse> getAllUsers() {

        List<UserDTOs.UserResponse> users = userService.getAllUsers();

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message(users.size() + " users found")
                .data(users)
                .timestamp(LocalDateTime.now()).build());
    }

    // ─── PUT /api/admin/users/{id}/toggle ─────────────────
    // Feature 5: Enable or disable a user account
    @PutMapping("/users/{id}/toggle")
    public ResponseEntity<ApiResponse> toggleUser(@PathVariable Long id) {

        UserDTOs.UserResponse result = userService.toggleUser(id);

        String msg = result.isEnabled()
                ? "User enabled successfully"
                : "User disabled successfully";

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message(msg)
                .data(result).timestamp(LocalDateTime.now()).build());
    }

    // ─── DELETE /api/admin/users/{id} ─────────────────────
    // Feature 5: Delete a user account
    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse> deleteUser(@PathVariable Long id) {

        userService.deleteUser(id);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("User deleted successfully")
                .timestamp(LocalDateTime.now()).build());
    }

    // ─── GET /api/admin/complaints ────────────────────────
    // Feature 5: All complaints (for admin review + status update)
    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse> getAllComplaints() {

        var complaints = complaintRepository.findAllByOrderByCreatedAtDesc();

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message(complaints.size() + " complaints found")
                .data(complaints)
                .timestamp(LocalDateTime.now()).build());
    }

    // ─── GET /api/admin/stats ─────────────────────────────
    // Feature 5: Platform-wide stats for admin dashboard
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse> getPlatformStats() {

        Map<String, Long> stats = userService.getPlatformStats();

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message("Platform stats fetched")
                .data(stats).timestamp(LocalDateTime.now()).build());
    }
}