package com.samadhanai.samadhanai.Dashboard.Controller;

import com.samadhanai.samadhanai.Common.Response.ApiResponse;
import com.samadhanai.samadhanai.Dashboard.Dto.DashboardDTOs;
import com.samadhanai.samadhanai.Dashboard.Dto.DashboardDTOs.DepartmentScoreDTO;
import com.samadhanai.samadhanai.Dashboard.Dto.DashboardDTOs.IgnoredComplaintDTO;
import com.samadhanai.samadhanai.Dashboard.Dto.DashboardDTOs.OverallStatsDTO;
import com.samadhanai.samadhanai.Dashboard.Dto.DashboardDTOs.WardStatsDTO;
import com.samadhanai.samadhanai.Dashboard.Service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<OverallStatsDTO>> getOverallStats() {
        log.info("Dashboard: overall stats requested");
        OverallStatsDTO stats = dashboardService.getOverallStats();
        return ResponseEntity.ok(ApiResponse.success("Overall stats fetched", stats));
    }

    @GetMapping("/ward-stats")
    public ResponseEntity<ApiResponse<List<WardStatsDTO>>> getWardStats() {
        log.info("Dashboard: ward-wise stats requested");
        List<WardStatsDTO> wardStats = dashboardService.getWardWiseStats();
        return ResponseEntity.ok(ApiResponse.success("Ward stats fetched", wardStats));
    }

    @GetMapping("/ignored")
    public ResponseEntity<ApiResponse<List<IgnoredComplaintDTO>>> getIgnoredComplaints() {
        log.info("Dashboard: ignored complaints requested");
        List<IgnoredComplaintDTO> ignored = dashboardService.getIgnoredComplaints();
        String message = ignored.isEmpty()
                ? "No complaints ignored — great performance!"
                : ignored.size() + " complaints have been ignored for 30+ days";
        return ResponseEntity.ok(ApiResponse.success(message, ignored));
    }

    @GetMapping("/dept-scores")
    public ResponseEntity<ApiResponse<List<DepartmentScoreDTO>>> getDepartmentScores() {
        log.info("Dashboard: department scores requested");
        List<DepartmentScoreDTO> scores = dashboardService.getDepartmentScores();
        return ResponseEntity.ok(ApiResponse.success("Department scores fetched", scores));
    }

    @GetMapping("/full")
    public ResponseEntity<ApiResponse<FullDashboardResponse>> getFullDashboard() {
        log.info("Dashboard: full dashboard data requested");
        FullDashboardResponse fullData = new FullDashboardResponse(
                dashboardService.getOverallStats(),
                dashboardService.getWardWiseStats(),
                dashboardService.getIgnoredComplaints(),
                dashboardService.getDepartmentScores()
        );
        return ResponseEntity.ok(ApiResponse.success("Full dashboard data fetched", fullData));
    }

    public record FullDashboardResponse(
            OverallStatsDTO            overallStats,
            List<WardStatsDTO>         wardStats,
            List<IgnoredComplaintDTO>  ignoredComplaints,
            List<DepartmentScoreDTO>   departmentScores
    ) {}
}