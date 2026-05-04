package com.samadhanai.samadhanai.Dashboard.Dto;

import com.samadhanai.samadhanai.Common.Enums.ComplaintType;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class DashboardDTOs {

    // ─────────────────────────────────────────────────────────────────
    // 📊 1. Overall Stats — top stat cards on dashboard
    // ─────────────────────────────────────────────────────────────────
    @Data
    @Builder
    public static class OverallStatsDTO {
        private long   totalComplaints;
        private long   pendingComplaints;
        private long   inProgressComplaints;
        private long   resolvedComplaints;
        private long   ignoredComplaints;
        private long   fakeRejectedComplaints;
        private double resolutionRatePercent;
        private double fakeRejectionRatePercent;
        private String mostComplainedWard;
        private String worstPerformingDepartment;
        private String mostCommonComplaintType;
    }

    // ─────────────────────────────────────────────────────────────────
    // 🗺️ 2. Ward Stats — bar chart data per ward
    // ─────────────────────────────────────────────────────────────────
    @Data
    @Builder
    public static class WardStatsDTO {
        private String wardName;
        private long   totalComplaints;
        private long   pendingComplaints;
        private long   inProgressComplaints;
        private long   resolvedComplaints;
        private long   ignoredComplaints;
        private double resolutionRatePercent;
        private String topComplaintType;
    }

    // ─────────────────────────────────────────────────────────────────
    // 🚨 3. Ignored Complaint — 30+ days no action
    // ─────────────────────────────────────────────────────────────────
    @Data
    @Builder
    public static class IgnoredComplaintDTO {
        private String         referenceId;
        private String         aiGeneratedTitle;
        private ComplaintType  complaintType;
        private DepartmentType assignedDepartment;
        private String         departmentName;
        private String         ward;
        private String         area;
        private String         fullAddress;
        private LocalDateTime  submittedAt;
        private long           daysIgnored;
        private Boolean        emailWasSent;
        private LocalDateTime  emailSentAt;
        private String         complaintUrl;
    }

    // ─────────────────────────────────────────────────────────────────
    // 🏛️ 4. Department Score — performance scorecard
    // ─────────────────────────────────────────────────────────────────
    @Data
    @Builder
    public static class DepartmentScoreDTO {
        private DepartmentType departmentType;
        private String         departmentName;
        private String         departmentEmail;
        private long           totalAssigned;
        private long           resolved;
        private long           pending;
        private long           ignored;
        private double         resolutionRatePercent;
        private double         avgResolutionDays;
        private String         performanceGrade;
        private long           emailsSentNoAction;
    }
}