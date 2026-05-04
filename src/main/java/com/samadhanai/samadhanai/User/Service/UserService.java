package com.samadhanai.samadhanai.User.Service;

import com.samadhanai.samadhanai.Common.Enums.UserRole;
import com.samadhanai.samadhanai.Complaint.Repository.ComplaintRepository;
import com.samadhanai.samadhanai.Config.JwtUtil;
import com.samadhanai.samadhanai.Exception.AppExceptions;
import com.samadhanai.samadhanai.User.Dto.UserDTOs;
import com.samadhanai.samadhanai.User.Repository.UserRepository;
import com.samadhanai.samadhanai.User.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository        userRepository;
    private final ComplaintRepository   complaintRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtUtil               jwtUtil;

    // ─── Register ─────────────────────────────────────────
    public UserDTOs.AuthResponse register(UserDTOs.RegisterRequest dto) {

        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + dto.getEmail());
        }

        User user = User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(UserRole.USER)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved);
        log.info("New user registered: {}", saved.getEmail());

        return UserDTOs.AuthResponse.builder()
                .token(token).type("Bearer")
                .id(saved.getId()).name(saved.getName())
                .email(saved.getEmail()).role(saved.getRole())
                .build();
    }

    // ─── Login ────────────────────────────────────────────
    public UserDTOs.AuthResponse login(UserDTOs.LoginRequest dto) {

        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new AppExceptions.UnauthorizedException("Account is disabled");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user);
        log.info("User logged in: {}", user.getEmail());

        return UserDTOs.AuthResponse.builder()
                .token(token).type("Bearer")
                .id(user.getId()).name(user.getName())
                .email(user.getEmail()).role(user.getRole())
                .build();
    }

    // ─── Get Profile ──────────────────────────────────────
    public UserDTOs.UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toUserResponse(user);
    }

    // ─── Update Profile (name only) ───────────────────────
    public UserDTOs.UserResponse updateProfile(Long userId,
                                               UserDTOs.UpdateProfileRequest dto) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (dto.getName() != null && !dto.getName().isBlank()) {
            user.setName(dto.getName());
        }

        return toUserResponse(userRepository.save(user));
    }

    // ─── Change Password ──────────────────────────────────
    // ✅ NEW: separate method for password change
    public void changePassword(Long userId, UserDTOs.ChangePasswordRequest dto) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getEmail());
    }

    // ─── Admin: Get All Users ─────────────────────────────
    public List<UserDTOs.UserResponse> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    // ─── Admin: Toggle User Enable/Disable ────────────────
    public UserDTOs.UserResponse toggleUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setEnabled(!user.isEnabled());
        log.info("User {} {}", user.getEmail(), user.isEnabled() ? "enabled" : "disabled");
        return toUserResponse(userRepository.save(user));
    }

    // ─── Admin: Delete User ───────────────────────────────
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        userRepository.delete(user);
        log.info("User deleted: {}", user.getEmail());
    }

    // ─── Admin: Platform Stats ────────────────────────────
    public Map<String, Long> getPlatformStats() {
        long totalUsers      = userRepository.count();
        long activeUsers     = userRepository.findByEnabled(true).size();
        long disabledUsers   = userRepository.findByEnabled(false).size();
        long totalComplaints = complaintRepository.count();

        return Map.of(
                "totalUsers",      totalUsers,
                "activeUsers",     activeUsers,
                "disabledUsers",   disabledUsers,
                "totalComplaints", totalComplaints
        );
    }

    // ─── Entity → DTO ─────────────────────────────────────
    private UserDTOs.UserResponse toUserResponse(User user) {
        Long userId = user.getId();
        return UserDTOs.UserResponse.builder()
                .id(userId)
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .totalComplaints(complaintRepository.countByUserId(userId))
                .resolvedComplaints(complaintRepository.countByUserIdAndStatus(userId,
                        com.samadhanai.samadhanai.Common.Enums.ComplaintStatus.RESOLVED))
                .pendingComplaints(complaintRepository.countByUserIdAndStatus(userId,
                        com.samadhanai.samadhanai.Common.Enums.ComplaintStatus.PENDING))
                .inProgressComplaints(complaintRepository.countByUserIdAndStatus(userId,
                        com.samadhanai.samadhanai.Common.Enums.ComplaintStatus.IN_PROGRESS))
                .build();
    }
}