package com.samadhanai.samadhanai.Complaint.Dto;

import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Enums.ComplaintType;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import com.samadhanai.samadhanai.Common.Enums.ResolutionSource;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class ComplaintDTOs {

    // ─── Submit Request (multipart form) ─────────────────
    @Data
    public static class Request {

        @NotBlank(message = "Name is required")
        private String userName;

        @Email(message = "Valid email required")
        @NotBlank(message = "Email is required")
        private String userEmail;

        private String userPhone;

        @NotBlank(message = "Please describe the problem")
        @Size(min = 10, max = 1000, message = "Description must be 10-1000 chars")
        private String userDescription;

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        private Double latitude;

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        private Double longitude;

        private boolean sendEmailToDepartment;
    }

    // ─── API Response ─────────────────────────────────────
    @Data
    @Builder
    public static class Response {
        private Long           id;
        private String         referenceId;
        private Long           userId;
        private String         userName;
        private String         userEmail;
        private String         userPhone;
        private String         userDescription;
        private ComplaintType  complaintType;
        private DepartmentType assignedDepartment;
        private String         aiGeneratedTitle;
        private String         aiGeneratedDescription;
        private Integer        aiConfidenceScore;
        private Boolean        photoVerified;
        private String         photoRejectionReason;
        private String         photoPath;
        private List<String>   extraPhotoPaths;
        private Double         latitude;
        private Double         longitude;
        private String         ward;
        private String         area;
        private String         city;
        private String         state;
        private String         pincode;
        private String         fullAddress;
        private ComplaintStatus status;
        private String         resolutionNote;

        // ✅ NEW — Resolution Tracking
        private ResolutionSource resolvedBy;       // ADMIN / USER / DEPARTMENT_EMAIL
        private LocalDateTime    resolvedAt;        // Kab resolve hua
        private String           resolutionSource;  // Human readable source

        private Boolean        emailSent;
        private String         departmentEmailSentTo;
        private LocalDateTime  emailSentAt;
        private Boolean        reminderSent;
        private LocalDateTime  reminderSentAt;
        private LocalDateTime  createdAt;
        private LocalDateTime  updatedAt;
    }
}