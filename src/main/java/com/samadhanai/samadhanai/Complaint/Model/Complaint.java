package com.samadhanai.samadhanai.Complaint.Model;

import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Enums.ComplaintType;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import com.samadhanai.samadhanai.Common.Enums.ResolutionSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String referenceId;

    // ─── Citizen Info ─────────────────────────────────────
    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private String userEmail;

    private String userPhone;

    @Column(length = 1000)
    private String userDescription;

    // ─── AI Results ───────────────────────────────────────
    @Enumerated(EnumType.STRING)
    private ComplaintType complaintType;

    @Enumerated(EnumType.STRING)
    private DepartmentType assignedDepartment;

    @Column(length = 500)
    private String aiGeneratedTitle;

    @Column(length = 2000)
    private String aiGeneratedDescription;

    private Integer aiConfidenceScore;

    // ─── Photos ───────────────────────────────────────────
    private String photoPath;

    @Column(length = 2000)
    private String extraPhotoPaths;

    private Boolean photoVerified;
    private String  photoRejectionReason;

    // ─── Location ─────────────────────────────────────────
    private Double latitude;
    private Double longitude;
    private String ward;
    private String area;
    private String city;
    private String state;
    private String pincode;

    @Column(length = 1000)
    private String fullAddress;

    // ─── Status ───────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ComplaintStatus status = ComplaintStatus.PENDING;

    @Column(length = 1000)
    private String resolutionNote;

    // ─── Resolution Tracking (NEW) ────────────────────────
    // Who resolved this complaint?
    // ADMIN / USER / DEPARTMENT_EMAIL
    @Enumerated(EnumType.STRING)
    private ResolutionSource resolvedBy;

    // When was it resolved?
    private LocalDateTime resolvedAt;

    // Extra info — e.g. "Auto-resolved via department email reply"
    @Column(length = 500)
    private String resolutionSource;

    // ─── Email ────────────────────────────────────────────
    private Boolean       emailSent;
    private String        departmentEmailSentTo;
    private LocalDateTime emailSentAt;

    // ─── Reminder ─────────────────────────────────────────
    @Builder.Default
    private Boolean       reminderSent = false;
    private LocalDateTime reminderSentAt;

    // ─── Timestamps ───────────────────────────────────────
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}