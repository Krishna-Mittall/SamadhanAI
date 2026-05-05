package com.samadhanai.samadhanai.Complaint.Controller;

import com.samadhanai.samadhanai.Ai.FakePhotoDetectionService;
import com.samadhanai.samadhanai.Ai.PhotoAnalysisService;
import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Response.ApiResponse;
import com.samadhanai.samadhanai.Complaint.Dto.ComplaintDTOs;
import com.samadhanai.samadhanai.Complaint.Dto.ComplaintEditDTO;
import com.samadhanai.samadhanai.Complaint.Service.ComplaintService;
import com.samadhanai.samadhanai.User.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;
    private final PhotoAnalysisService photoAnalysisService;
    private final FakePhotoDetectionService fakePhotoDetectionService;

    // ─── POST /api/complaints/analyze ────────────────────
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse> analyzePhoto(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(value = "description", required = false, defaultValue = "") String description) {

        FakePhotoDetectionService.FakeDetectionResult fakeResult =
                fakePhotoDetectionService.detectFake(photo, 0.0, 0.0);

        boolean hasExif    = fakeResult.isPassedMetadataCheck();
        boolean isRejected = !fakeResult.isReal();
        String  rejectReason = isRejected ? fakeResult.getReason() : null;

        PhotoAnalysisService.PhotoAnalysisResult result = isRejected
                ? null
                : photoAnalysisService.analyzePhoto(photo, description);

        Map<String, Object> data = Map.of(
                "complaintType",   result != null ? result.getComplaintType()  : "OTHER",
                "department",      result != null ? result.getDepartment()      : "MUNICIPAL_CORPORATION",
                "title",           result != null && result.getTitle() != null  ? result.getTitle() : "",
                "confidence",      result != null && result.getConfidenceScore() != null ? result.getConfidenceScore() / 100.0 : 0.0,
                "hasExif",         hasExif,
                "isRejected",      isRejected,
                "rejectReason",    rejectReason != null ? rejectReason : "",
                "analysisSuccess", result != null && result.isAnalysisSuccess()
        );

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message(isRejected ? "Photo failed verification" : "Photo analyzed successfully")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build());
    }

    // ─── POST /api/complaints ─────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse> submitComplaint(
            @Valid @ModelAttribute ComplaintDTOs.Request dto,
            @RequestParam("photos") List<MultipartFile> photos,
            @AuthenticationPrincipal User currentUser) {

        if (photos == null || photos.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false).message("At least one photo is required.")
                    .timestamp(LocalDateTime.now()).build());
        }
        if (photos.size() > 3) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false).message("Maximum 3 photos allowed.")
                    .timestamp(LocalDateTime.now()).build());
        }

        Long userId = (currentUser != null) ? currentUser.getId() : null;
        ComplaintDTOs.Response result = complaintService.submitComplaint(dto, photos, userId);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message("Complaint submitted successfully!")
                .data(result).timestamp(LocalDateTime.now()).build());
    }

    // ─── GET /api/complaints/{referenceId} ────────────────
    @GetMapping("/{referenceId}")
    public ResponseEntity<ApiResponse> trackComplaint(
            @PathVariable String referenceId,
            @AuthenticationPrincipal User currentUser) {

        ComplaintDTOs.Response result = complaintService.trackComplaint(referenceId);
        if (currentUser == null) {
            result.setUserId(null);
            result.setUserEmail(null);
            result.setUserPhone(null);
            result.setLatitude(null);
            result.setLongitude(null);
        }
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message("OK")
                .data(result).timestamp(LocalDateTime.now()).build());
    }

    // ─── PUT /api/complaints/{referenceId}/edit ───────────
    @PutMapping("/{referenceId}/edit")
    public ResponseEntity<ApiResponse> editComplaint(
            @PathVariable String referenceId,
            @Valid @RequestBody ComplaintEditDTO dto,
            @AuthenticationPrincipal User currentUser) {

        ComplaintDTOs.Response result = complaintService.editComplaint(
                referenceId, dto, currentUser.getId());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message("Complaint updated successfully")
                .data(result).timestamp(LocalDateTime.now()).build());
    }

    // ─── POST /api/complaints/{referenceId}/photos ────────
    @PostMapping("/{referenceId}/photos")
    public ResponseEntity<ApiResponse> addPhotos(
            @PathVariable String referenceId,
            @RequestParam("photos") List<MultipartFile> photos,
            @AuthenticationPrincipal User currentUser) {

        ComplaintDTOs.Response result = complaintService.addExtraPhotos(
                referenceId, photos, currentUser.getId());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message(photos.size() + " photo(s) added successfully")
                .data(result).timestamp(LocalDateTime.now()).build());
    }

    // ─── POST /api/complaints/{referenceId}/send ──────────
    @PostMapping("/{referenceId}/send")
    public ResponseEntity<ApiResponse> sendToDepartment(
            @PathVariable String referenceId,
            @AuthenticationPrincipal User currentUser) {

        ComplaintDTOs.Response result = complaintService.sendToDepartment(
                referenceId, currentUser.getId());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message("Complaint sent to department successfully!")
                .data(result).timestamp(LocalDateTime.now()).build());
    }

    // ─── PUT /api/complaints/{referenceId}/status ─────────
    // Admin only
    @PutMapping("/{referenceId}/status")
    public ResponseEntity<ApiResponse> updateStatus(
            @PathVariable String referenceId,
            @RequestBody Map<String, String> body) {

        ComplaintStatus newStatus    = ComplaintStatus.valueOf(body.get("status"));
        String          resolutionNote = body.get("resolutionNote");

        ComplaintDTOs.Response result = complaintService.updateStatus(
                referenceId, newStatus, resolutionNote);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true).message("Status updated to " + newStatus)
                .data(result).timestamp(LocalDateTime.now()).build());
    }

    // ─── NEW: PUT /api/complaints/{referenceId}/resolve ───
    // Way 2 — Citizen/User resolves their own complaint
    @PutMapping("/{referenceId}/resolve")
    public ResponseEntity<ApiResponse> resolveByUser(
            @PathVariable String referenceId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal User currentUser) {

        String note = (body != null) ? body.get("note") : null;

        ComplaintDTOs.Response result = complaintService.resolveByUser(
                referenceId, currentUser.getId(), note);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("✅ Complaint marked as resolved by you!")
                .data(result)
                .timestamp(LocalDateTime.now())
                .build());
    }
}