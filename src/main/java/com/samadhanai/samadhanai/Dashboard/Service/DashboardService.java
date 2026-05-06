package com.samadhanai.samadhanai.Dashboard.Service;

import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import com.samadhanai.samadhanai.Complaint.Repository.ComplaintRepository;
import com.samadhanai.samadhanai.Dashboard.Dto.DashboardDTOs.DepartmentScoreDTO;
import com.samadhanai.samadhanai.Dashboard.Dto.DashboardDTOs.IgnoredComplaintDTO;
import com.samadhanai.samadhanai.Dashboard.Dto.DashboardDTOs.OverallStatsDTO;
import com.samadhanai.samadhanai.Dashboard.Dto.DashboardDTOs.WardStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final ComplaintRepository complaintRepository;

    private static final int IGNORED_DAYS_THRESHOLD = 30;

    // ─────────────────────────────────────────────────────────────────
    // 📊 1. OVERALL STATS
    // ─────────────────────────────────────────────────────────────────
    public OverallStatsDTO getOverallStats() {
        log.info("Fetching overall dashboard stats");

        long total      = complaintRepository.count();
        long pending    = complaintRepository.countByStatus(ComplaintStatus.PENDING);
        long inProgress = complaintRepository.countByStatus(ComplaintStatus.IN_PROGRESS);
        long resolved   = complaintRepository.countByStatus(ComplaintStatus.RESOLVED);
        // ✅ FIX: findIgnoredComplaints() not countIgnoredComplaintsSince()
        LocalDateTime cutoff = LocalDateTime.now().minusDays(IGNORED_DAYS_THRESHOLD);
        long ignored = complaintRepository.findIgnoredComplaints(cutoff).size();

        double resolutionRate = total > 0
                ? Math.round((resolved * 100.0 / total) * 10.0) / 10.0 : 0.0;

        return OverallStatsDTO.builder()
                .totalComplaints(total)
                .pendingComplaints(pending)
                .inProgressComplaints(inProgress)
                .resolvedComplaints(resolved)
                .ignoredComplaints(ignored)
                .resolutionRatePercent(resolutionRate)
                .mostComplainedWard(getMostComplainedWard())
                .worstPerformingDepartment(getWorstPerformingDepartment())
                .mostCommonComplaintType(getMostCommonComplaintType())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // 🗺️ 2. WARD WISE STATS
    // ─────────────────────────────────────────────────────────────────
    public List<WardStatsDTO> getWardWiseStats() {
        log.info("Fetching ward-wise stats");

        // ✅ FIX: findWardComplaintCounts() not countComplaintsByWard()
        List<Object[]> wardCounts = complaintRepository.findWardComplaintCounts();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(IGNORED_DAYS_THRESHOLD);
        List<Complaint> ignoredAll = complaintRepository.findIgnoredComplaints(cutoff);

        // All complaints for status breakdown per ward
        List<Complaint> allComplaints = complaintRepository.findAll();

        // Build ward → status → count map in memory
        Map<String, Map<ComplaintStatus, Long>> wardStatusMap = allComplaints.stream()
                .filter(c -> c.getWard() != null)
                .collect(Collectors.groupingBy(
                        Complaint::getWard,
                        Collectors.groupingBy(Complaint::getStatus, Collectors.counting())
                ));

        List<WardStatsDTO> result = new ArrayList<>();

        for (Object[] row : wardCounts) {
            String ward = (String) row[0];
            Long total  = (Long) row[1];

            if (ward == null || ward.isBlank()) continue;

            Map<ComplaintStatus, Long> statusMap =
                    wardStatusMap.getOrDefault(ward, new HashMap<>());

            long pendingCount    = statusMap.getOrDefault(ComplaintStatus.PENDING, 0L);
            long inProgressCount = statusMap.getOrDefault(ComplaintStatus.IN_PROGRESS, 0L);
            long resolvedCount   = statusMap.getOrDefault(ComplaintStatus.RESOLVED, 0L);

            long ignoredCount = ignoredAll.stream()
                    .filter(c -> ward.equals(c.getWard()))
                    .count();

            double resolutionRate = total > 0
                    ? Math.round((resolvedCount * 100.0 / total) * 10.0) / 10.0 : 0.0;

            result.add(WardStatsDTO.builder()
                    .wardName(ward)
                    .totalComplaints(total)
                    .pendingComplaints(pendingCount)
                    .inProgressComplaints(inProgressCount)
                    .resolvedComplaints(resolvedCount)
                    .ignoredComplaints(ignoredCount)
                    .resolutionRatePercent(resolutionRate)
                    .topComplaintType(getTopComplaintTypeForWard(ward, allComplaints))
                    .build());
        }

        result.sort(Comparator.comparingLong(WardStatsDTO::getTotalComplaints).reversed());
        log.info("Found {} wards with complaints", result.size());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    // 🚨 3. IGNORED COMPLAINTS
    // ─────────────────────────────────────────────────────────────────
    public List<IgnoredComplaintDTO> getIgnoredComplaints() {
        log.info("Fetching ignored complaints (30+ days pending)");

        LocalDateTime cutoff = LocalDateTime.now().minusDays(IGNORED_DAYS_THRESHOLD);

        // ✅ FIX: findIgnoredComplaints() — correct method name
        List<Complaint> ignoredComplaints = complaintRepository.findIgnoredComplaints(cutoff);

        return ignoredComplaints.stream()
                .map(c -> {
                    long daysIgnored = ChronoUnit.DAYS.between(
                            c.getCreatedAt(), LocalDateTime.now());

                    return IgnoredComplaintDTO.builder()
                            .referenceId(c.getReferenceId())
                            .aiGeneratedTitle(c.getAiGeneratedTitle())
                            .complaintType(c.getComplaintType())
                            .assignedDepartment(c.getAssignedDepartment())
                            .departmentName(getDepartmentDisplayName(c.getAssignedDepartment()))
                            .ward(c.getWard())
                            .area(c.getArea())
                            .fullAddress(c.getFullAddress())
                            .submittedAt(c.getCreatedAt())
                            .daysIgnored(daysIgnored)
                            .emailWasSent(c.getEmailSent())
                            .emailSentAt(c.getEmailSentAt())
                            .complaintUrl("/track.html?ref=" + c.getReferenceId())
                            .build();
                })
                .sorted(Comparator.comparingLong(IgnoredComplaintDTO::getDaysIgnored).reversed())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────
    // 🏛️ 4. DEPARTMENT SCORES
    // ─────────────────────────────────────────────────────────────────
    public List<DepartmentScoreDTO> getDepartmentScores() {
        log.info("Fetching department performance scores");

        LocalDateTime cutoff = LocalDateTime.now().minusDays(IGNORED_DAYS_THRESHOLD);
        List<Complaint> ignoredAll   = complaintRepository.findIgnoredComplaints(cutoff);
        List<Complaint> allComplaints = complaintRepository.findAll();

        List<DepartmentScoreDTO> scores = new ArrayList<>();

        for (DepartmentType dept : DepartmentType.values()) {

            // ✅ FIX: filter in memory — no extra repo methods needed
            List<Complaint> deptComplaints = allComplaints.stream()
                    .filter(c -> dept.equals(c.getAssignedDepartment()))
                    .collect(Collectors.toList());

            long totalAssigned = deptComplaints.size();
            if (totalAssigned == 0) continue;

            long resolved = deptComplaints.stream()
                    .filter(c -> ComplaintStatus.RESOLVED.equals(c.getStatus()))
                    .count();
            long pending = deptComplaints.stream()
                    .filter(c -> ComplaintStatus.PENDING.equals(c.getStatus()))
                    .count();
            long ignored = ignoredAll.stream()
                    .filter(c -> dept.equals(c.getAssignedDepartment()))
                    .count();

            // Emails sent but complaint still pending
            long emailsSentNoAction = deptComplaints.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getEmailSent())
                            && ComplaintStatus.PENDING.equals(c.getStatus()))
                    .count();

            // Avg resolution days — only resolved complaints
            double avgDays = deptComplaints.stream()
                    .filter(c -> ComplaintStatus.RESOLVED.equals(c.getStatus())
                            && c.getUpdatedAt() != null)
                    .mapToLong(c -> ChronoUnit.DAYS.between(
                            c.getCreatedAt(), c.getUpdatedAt()))
                    .average()
                    .orElse(0.0);

            double resolutionRate = totalAssigned > 0
                    ? Math.round((resolved * 100.0 / totalAssigned) * 10.0) / 10.0 : 0.0;

            scores.add(DepartmentScoreDTO.builder()
                    .departmentType(dept)
                    .departmentName(getDepartmentDisplayName(dept))
                    .departmentEmail(getDepartmentEmail(dept))
                    .totalAssigned(totalAssigned)
                    .resolved(resolved)
                    .pending(pending)
                    .ignored(ignored)
                    .resolutionRatePercent(resolutionRate)
                    .avgResolutionDays(Math.round(avgDays * 10.0) / 10.0)
                    .performanceGrade(calculatePerformanceGrade(resolutionRate))
                    .emailsSentNoAction(emailsSentNoAction)
                    .build());
        }

        // Worst performers first
        scores.sort(Comparator.comparingDouble(DepartmentScoreDTO::getResolutionRatePercent));
        return scores;
    }

    // ─── Private Helpers ──────────────────────────────────

    private String calculatePerformanceGrade(double rate) {
        if (rate >= 80) return "A";
        if (rate >= 60) return "B";
        if (rate >= 40) return "C";
        return "D";
    }

    private String getMostComplainedWard() {
        // ✅ FIX: findWardComplaintCounts() not countComplaintsByWard()
        List<Object[]> wardCounts = complaintRepository.findWardComplaintCounts();
        return wardCounts.stream()
                .max(Comparator.comparingLong(row -> (Long) row[1]))
                .map(row -> (String) row[0])
                .orElse("N/A");
    }

    private String getWorstPerformingDepartment() {
        List<Complaint> all = complaintRepository.findAll();
        DepartmentType worst = null;
        double worstRate = Double.MAX_VALUE;

        for (DepartmentType dept : DepartmentType.values()) {
            long total = all.stream()
                    .filter(c -> dept.equals(c.getAssignedDepartment())).count();
            if (total == 0) continue;

            long resolved = all.stream()
                    .filter(c -> dept.equals(c.getAssignedDepartment())
                            && ComplaintStatus.RESOLVED.equals(c.getStatus())).count();

            double rate = resolved * 100.0 / total;
            if (rate < worstRate) {
                worstRate = rate;
                worst = dept;
            }
        }
        return worst != null ? getDepartmentDisplayName(worst) : "N/A";
    }

    private String getMostCommonComplaintType() {
        return complaintRepository.findAll().stream()
                .filter(c -> c.getComplaintType() != null)
                .collect(Collectors.groupingBy(Complaint::getComplaintType, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().toString())
                .orElse("N/A");
    }

    private String getTopComplaintTypeForWard(String ward, List<Complaint> all) {
        return all.stream()
                .filter(c -> ward.equals(c.getWard()) && c.getComplaintType() != null)
                .collect(Collectors.groupingBy(Complaint::getComplaintType, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().toString())
                .orElse("OTHER");
    }

    private String getDepartmentDisplayName(DepartmentType dept) {
        if (dept == null) return "Unknown Department";
        return switch (dept) {
            case PWD                   -> "Public Works Department (PWD)";
            case MUNICIPAL_CORPORATION -> "Municipal Corporation";
            case ELECTRICITY_BOARD     -> "Electricity Board";
            case WATER_SUPPLY          -> "Water Supply Department";
            case SANITATION            -> "Sanitation Department";
        };
    }

    private String getDepartmentEmail(DepartmentType dept) {
        if (dept == null) return "civic@municipal.gov.in";
        return switch (dept) {
            case PWD                   -> "pwd.indore@mp.gov.in";
            case MUNICIPAL_CORPORATION -> "mc.indore@mp.gov.in";
            case ELECTRICITY_BOARD     -> "mpcz.indore@mp.gov.in";
            case WATER_SUPPLY          -> "phed.indore@mp.gov.in";
            case SANITATION            -> "sanitation.indore@mp.gov.in";
        };
    }
}