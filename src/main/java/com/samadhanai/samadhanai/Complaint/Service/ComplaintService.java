package com.samadhanai.samadhanai.Complaint.Service;

import com.samadhanai.samadhanai.Ai.FakePhotoDetectionService;
import com.samadhanai.samadhanai.Ai.PhotoAnalysisService;
import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Enums.ResolutionSource;
import com.samadhanai.samadhanai.Complaint.Dto.ComplaintDTOs;
import com.samadhanai.samadhanai.Complaint.Dto.ComplaintEditDTO;
import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import com.samadhanai.samadhanai.Complaint.Repository.ComplaintRepository;
import com.samadhanai.samadhanai.Email.Dto.EmailStatusDTO;
import com.samadhanai.samadhanai.Email.Service.EmailService;
import com.samadhanai.samadhanai.Exception.AppExceptions;
import com.samadhanai.samadhanai.Location.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintService {

    private final ComplaintRepository       complaintRepository;
    private final PhotoAnalysisService      photoAnalysisService;
    private final FakePhotoDetectionService fakePhotoDetectionService;
    private final LocationService           locationService;
    private final EmailService              emailService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    // ─── Submit Complaint ─────────────────────────────────
    public ComplaintDTOs.Response submitComplaint(
            ComplaintDTOs.Request dto,
            List<MultipartFile> photos,
            Long userId) {

        log.info("Submitting complaint for user: {}, photos count: {}", userId, photos != null ? photos.size() : 0);

        try {
            // Step 1: Verify ALL photos
            for (int i = 0; i < photos.size(); i++) {
                MultipartFile photo = photos.get(i);
                log.debug("Analyzing photo {}: {}", i + 1, photo.getOriginalFilename());
                
                FakePhotoDetectionService.FakeDetectionResult fakeResult =
                        fakePhotoDetectionService.detectFake(
                                photo, dto.getLatitude(), dto.getLongitude());

                if (!fakeResult.isReal()) {
                    String photoName = photo.getOriginalFilename() != null
                            ? photo.getOriginalFilename() : ("Photo " + (i + 1));
                    log.warn("Photo {} rejected: {}", photoName, fakeResult.getReason());
                    throw new AppExceptions.FakePhotoException(
                            "Photo \"" + photoName + "\": " + fakeResult.getReason());
                }
            }

        // Step 2: AI analysis on first photo
        PhotoAnalysisService.PhotoAnalysisResult aiResult =
                photoAnalysisService.analyzePhoto(photos.get(0), dto.getUserDescription());

        // Step 3: Save photos
        log.debug("Saving {} photos", photos.size());
        String mainPhotoPath = savePhoto(photos.get(0), null);
        log.info("Main photo saved: {}", mainPhotoPath);
        
        List<String> extraPaths = new ArrayList<>();
        for (int i = 1; i < photos.size(); i++) {
            String extraPath = savePhoto(photos.get(i), null);
            extraPaths.add(extraPath);
            log.debug("Extra photo {} saved: {}", i, extraPath);
        }

        // Step 4: Reverse geocode
        log.debug("Reverse geocoding coordinates: {}, {}", dto.getLatitude(), dto.getLongitude());
        LocationService.LocationResult location =
                locationService.reverseGeocode(dto.getLatitude(), dto.getLongitude());

        // Step 5: Generate reference ID
        String referenceId = generateReferenceId();
        log.info("Generated reference ID: {}", referenceId);

        // Step 6: Build and save
        Complaint complaint = Complaint.builder()
                .referenceId(referenceId)
                .userId(userId)
                .userName(dto.getUserName())
                .userEmail(dto.getUserEmail())
                .userPhone(dto.getUserPhone())
                .userDescription(dto.getUserDescription())
                .complaintType(aiResult.getComplaintType())
                .assignedDepartment(aiResult.getDepartment())
                .aiGeneratedTitle(aiResult.getTitle())
                .aiGeneratedDescription(aiResult.getDescription())
                .aiConfidenceScore(aiResult.getConfidenceScore())
                .photoPath(mainPhotoPath)
                .extraPhotoPaths(extraPaths.isEmpty() ? null : String.join(",", extraPaths))
                .photoVerified(true)
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .ward(location.getWard())
                .area(location.getArea())
                .city(location.getCity())
                .state(location.getState())
                .pincode(location.getPincode())
                .fullAddress(location.getFullAddress())
                .status(ComplaintStatus.PENDING)
                .build();

        log.debug("Saving complaint to database...");
        Complaint saved = complaintRepository.save(complaint);
        log.info("Complaint saved with ID: {}, Reference: {}", saved.getId(), saved.getReferenceId());

        // Step 7: Email
        if (dto.isSendEmailToDepartment()) {
            log.info("Sending email to department for complaint: {}", saved.getReferenceId());
            EmailStatusDTO emailStatus = emailService.sendComplaintToDepartment(saved);
            saved.setEmailSent(emailStatus.isSent());
            saved.setDepartmentEmailSentTo(emailStatus.getSentTo());
            saved.setEmailSentAt(LocalDateTime.now());
            complaintRepository.save(saved);
            log.info("Email status - Sent: {}, To: {}", emailStatus.isSent(), emailStatus.getSentTo());
        }

        return toResponse(saved);
        
        } catch (Exception e) {
            log.error("Failed to submit complaint: {}", e.getMessage(), e);
            throw new AppExceptions.ComplaintSubmissionException("Failed to save complaint: " + e.getMessage(), e);
        }
    }

    // ─── Track ────────────────────────────────────────────
    public ComplaintDTOs.Response trackComplaint(String referenceId) {
        Complaint c = complaintRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new AppExceptions.ComplaintNotFoundException(
                        "Complaint not found: " + referenceId));
        return toResponse(c);
    }

    // ─── Edit ─────────────────────────────────────────────
    public ComplaintDTOs.Response editComplaint(
            String referenceId, ComplaintEditDTO dto, Long userId) {

        Complaint c = getComplaintOrThrow(referenceId);

        if (!c.getUserId().equals(userId)) {
            throw new AppExceptions.UnauthorizedException(
                    "You can only edit your own complaints");
        }
        if (c.getStatus() != ComplaintStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING complaints can be edited");
        }
        if (dto.getComplaintType() != null) {
            c.setComplaintType(dto.getComplaintType());
        }
        if (dto.getUserDescription() != null && !dto.getUserDescription().isBlank()) {
            c.setUserDescription(dto.getUserDescription());
        }
        return toResponse(complaintRepository.save(c));
    }

    // ─── Add Extra Photos ─────────────────────────────────
    public ComplaintDTOs.Response addExtraPhotos(
            String referenceId, List<MultipartFile> photos, Long userId) {

        Complaint c = getComplaintOrThrow(referenceId);

        if (!c.getUserId().equals(userId)) {
            throw new AppExceptions.UnauthorizedException(
                    "You can only update your own complaints");
        }

        List<String> existing = getExtraPhotosList(c);
        for (MultipartFile photo : photos) {
            if (!photo.isEmpty()) {
                existing.add(savePhoto(photo, referenceId));
            }
        }
        c.setExtraPhotoPaths(String.join(",", existing));
        return toResponse(complaintRepository.save(c));
    }

    // ─── Send to Department ───────────────────────────────
    public ComplaintDTOs.Response sendToDepartment(String referenceId, Long userId) {

        Complaint c = getComplaintOrThrow(referenceId);

        if (!c.getUserId().equals(userId)) {
            throw new AppExceptions.UnauthorizedException(
                    "You can only send your own complaints");
        }

        EmailStatusDTO emailStatus = emailService.sendComplaintToDepartment(c);
        c.setEmailSent(emailStatus.isSent());
        c.setDepartmentEmailSentTo(emailStatus.getSentTo());
        c.setEmailSentAt(LocalDateTime.now());
        return toResponse(complaintRepository.save(c));
    }

    // ─── Update Status (Admin) ────────────────────────────
    public ComplaintDTOs.Response updateStatus(
            String referenceId,
            ComplaintStatus newStatus,
            String resolutionNote) {

        Complaint c = getComplaintOrThrow(referenceId);
        ComplaintStatus oldStatus = c.getStatus();

        c.setStatus(newStatus);
        if (resolutionNote != null) c.setResolutionNote(resolutionNote);

        // ✅ NEW: Track resolution metadata
        if (newStatus == ComplaintStatus.RESOLVED) {
            c.setResolvedBy(ResolutionSource.ADMIN);
            c.setResolvedAt(LocalDateTime.now());
            c.setResolutionSource("Resolved by Admin");
        }

        Complaint saved = complaintRepository.save(c);

        if (!oldStatus.equals(newStatus)) {
            emailService.sendStatusUpdateToCitizen(saved, oldStatus, newStatus);
        }
        return toResponse(saved);
    }

    // ─── NEW: Way 2 — User/Citizen Resolves ──────────────
    public ComplaintDTOs.Response resolveByUser(String referenceId,
                                                Long userId,
                                                String note) {

        Complaint c = getComplaintOrThrow(referenceId);

        // Only the complaint owner can resolve
        if (c.getUserId() == null || !c.getUserId().equals(userId)) {
            throw new AppExceptions.UnauthorizedException(
                    "You can only resolve your own complaints");
        }

        // Only PENDING or IN_PROGRESS can be resolved
        if (c.getStatus() == ComplaintStatus.RESOLVED) {
            throw new IllegalArgumentException("Complaint is already resolved");
        }
        if (c.getStatus() == ComplaintStatus.REJECTED) {
            throw new IllegalArgumentException("Rejected complaints cannot be resolved");
        }

        ComplaintStatus oldStatus = c.getStatus();

        c.setStatus(ComplaintStatus.RESOLVED);
        c.setResolvedBy(ResolutionSource.USER);
        c.setResolvedAt(LocalDateTime.now());
        c.setResolutionSource("Confirmed resolved by citizen");
        c.setResolutionNote(note != null && !note.isBlank()
                ? note
                : "Citizen confirmed the problem has been resolved.");

        Complaint saved = complaintRepository.save(c);

        // Send status update email to citizen (confirmation)
        emailService.sendStatusUpdateToCitizen(saved, oldStatus, ComplaintStatus.RESOLVED);

        log.info("✅ Complaint {} resolved by USER (citizen)", referenceId);
        return toResponse(saved);
    }

    // ─── NEW: Way 3 — Auto Resolve via Dept Email Reply ──
    // Called by EmailReplyListenerService
    public ComplaintDTOs.Response resolveByEmailReply(String referenceId,
                                                      String emailSnippet) {

        // Use special query — only complaints with email sent + still active
        Complaint c = complaintRepository
                .findActiveComplaintWithEmailSent(referenceId)
                .orElseThrow(() -> new AppExceptions.ComplaintNotFoundException(
                        "No active emailed complaint found: " + referenceId));

        ComplaintStatus oldStatus = c.getStatus();

        c.setStatus(ComplaintStatus.RESOLVED);
        c.setResolvedBy(ResolutionSource.DEPARTMENT_EMAIL);
        c.setResolvedAt(LocalDateTime.now());
        c.setResolutionSource("Auto-resolved via department email reply");
        c.setResolutionNote("Department replied via email confirming resolution. "
                + "Email snippet: \""
                + (emailSnippet != null && emailSnippet.length() > 200
                ? emailSnippet.substring(0, 200) + "..."
                : emailSnippet)
                + "\"");

        Complaint saved = complaintRepository.save(c);

        // Notify citizen
        emailService.sendStatusUpdateToCitizen(saved, oldStatus, ComplaintStatus.RESOLVED);

        log.info("✅ Complaint {} auto-resolved via DEPARTMENT EMAIL REPLY", referenceId);
        return toResponse(saved);
    }

    // ─── Get by User ──────────────────────────────────────
    public List<ComplaintDTOs.Response> getComplaintsByUserId(Long userId) {
        return complaintRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────
    private Complaint getComplaintOrThrow(String referenceId) {
        return complaintRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new AppExceptions.ComplaintNotFoundException(
                        "Complaint not found: " + referenceId));
    }

    private String savePhoto(MultipartFile file, String subfolder) {
        try {
            log.debug("Saving photo: {}", file.getOriginalFilename());
            
            // 🔥 ALWAYS ensure folder exists - Railway FS fix
            Path baseDir = Paths.get("/tmp/uploads");
            Files.createDirectories(baseDir);
            
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path target = baseDir.resolve(filename);
            
            log.debug("Target file: {}", target.toAbsolutePath());
            
            // 🔥 REPLACE Files.copy with transferTo (more stable on Railway)
            file.transferTo(target.toFile());
            
            // Verify file was saved
            if (!Files.exists(target)) {
                throw new IOException("File was not saved: " + target.toAbsolutePath());
            }
            
            long fileSize = Files.size(target);
            log.info("Photo saved successfully: {} ({} bytes)", filename, fileSize);
            
            return filename;
        } catch (Exception e) {
            log.error("Photo storage failed: {}", e.getMessage(), e);
            throw new AppExceptions.PhotoStorageException("Failed to save photo: " + e.getMessage(), e);
        }
    }

    private String generateReferenceId() {
        String date = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        int rand = (int)(Math.random() * 90000) + 10000;
        return "SAI-" + date + "-" + rand;
    }

    private List<String> getExtraPhotosList(Complaint c) {
        if (c.getExtraPhotoPaths() == null || c.getExtraPhotoPaths().isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(c.getExtraPhotoPaths().split(",")));
    }

    // ─── Entity → DTO ─────────────────────────────────────
    public ComplaintDTOs.Response toResponse(Complaint c) {
        return ComplaintDTOs.Response.builder()
                .id(c.getId())
                .referenceId(c.getReferenceId())
                .userId(c.getUserId())
                .userName(c.getUserName())
                .userEmail(c.getUserEmail())
                .userPhone(c.getUserPhone())
                .userDescription(c.getUserDescription())
                .complaintType(c.getComplaintType())
                .assignedDepartment(c.getAssignedDepartment())
                .aiGeneratedTitle(c.getAiGeneratedTitle())
                .aiGeneratedDescription(c.getAiGeneratedDescription())
                .aiConfidenceScore(c.getAiConfidenceScore())
                .photoVerified(c.getPhotoVerified())
                .photoRejectionReason(c.getPhotoRejectionReason())
                .photoPath(c.getPhotoPath())
                .extraPhotoPaths(getExtraPhotosList(c))
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .ward(c.getWard())
                .area(c.getArea())
                .city(c.getCity())
                .state(c.getState())
                .pincode(c.getPincode())
                .fullAddress(c.getFullAddress())
                .status(c.getStatus())
                .resolutionNote(c.getResolutionNote())
                // ✅ NEW fields in response
                .resolvedBy(c.getResolvedBy())
                .resolvedAt(c.getResolvedAt())
                .resolutionSource(c.getResolutionSource())
                .emailSent(c.getEmailSent())
                .departmentEmailSentTo(c.getDepartmentEmailSentTo())
                .emailSentAt(c.getEmailSentAt())
                .reminderSent(c.getReminderSent())
                .reminderSentAt(c.getReminderSentAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}