package com.samadhanai.samadhanai.Email.Service;

import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import com.samadhanai.samadhanai.Email.Dto.EmailStatusDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sends transactional mail through Brevo's HTTPS API, which works on Render Free. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrevoEmailService {
    private static final String BREVO_EMAIL_API = "https://api.brevo.com/v3/smtp/email";

    private final ComplaintLetterBuilder letterBuilder;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${brevo.sender.email}")
    private String fromEmail;

    @Value("${brevo.sender.name:SamadhanAI Civic Platform}")
    private String fromName;

    @Value("${app.upload.dir}")
    private String uploadDir;

    public EmailStatusDTO sendComplaintToDepartment(Complaint complaint) {
        String departmentEmail = getDepartmentEmail(complaint.getAssignedDepartment());
        log.info("Starting Brevo email for complaint {} to {}", complaint.getReferenceId(), departmentEmail);
        try {
            ComplaintLetterBuilder.LetterResult letter = letterBuilder.buildComplaintLetter(complaint);
            send(departmentEmail, complaint.getUserEmail(), letter.getSubject(), letter.getBodyHtml(),
                    complaintAttachments(complaint, "complaint_" + complaint.getReferenceId()));
            log.info("Brevo accepted complaint email for {}", complaint.getReferenceId());
            return EmailStatusDTO.builder()
                    .sent(true).sentTo(departmentEmail).ccTo(complaint.getUserEmail())
                    .sentAt(LocalDateTime.now()).subject(letter.getSubject())
                    .complaintReferenceId(complaint.getReferenceId()).build();
        } catch (Exception e) {
            log.error("Brevo failed for complaint {}: {}", complaint.getReferenceId(), e.getMessage(), e);
            return failedStatus(complaint, departmentEmail, e);
        }
    }

    public void sendStatusUpdateToCitizen(Complaint complaint,
                                          ComplaintStatus oldStatus,
                                          ComplaintStatus newStatus) {
        if (complaint.getUserEmail() == null || complaint.getUserEmail().isBlank()) return;
        String subject = "Update on your complaint " + complaint.getReferenceId() + " — " + newStatus;
        String body = "<p>Your complaint <strong>" + complaint.getReferenceId() + "</strong> changed from "
                + oldStatus + " to <strong>" + newStatus + "</strong>.</p><p>SamadhanAI Civic Platform</p>";
        try {
            send(complaint.getUserEmail(), null, subject, body, List.of());
            log.info("Brevo accepted status update for {}", complaint.getReferenceId());
        } catch (Exception e) {
            log.error("Brevo status update failed for {}: {}", complaint.getReferenceId(), e.getMessage(), e);
        }
    }

    public EmailStatusDTO sendReminderToDepartment(Complaint complaint) {
        String departmentEmail = getDepartmentEmail(complaint.getAssignedDepartment());
        String subject = "REMINDER: Complaint pending — " + complaint.getReferenceId();
        String body = "<p>This is a reminder for civic complaint <strong>" + complaint.getReferenceId()
                + "</strong>.</p><p>Please review and take appropriate action.</p>";
        try {
            send(departmentEmail, complaint.getUserEmail(), subject, body,
                    complaintAttachments(complaint, "reminder_" + complaint.getReferenceId()));
            log.info("Brevo accepted reminder for {}", complaint.getReferenceId());
            return EmailStatusDTO.builder().sent(true).sentTo(departmentEmail)
                    .sentAt(LocalDateTime.now()).subject(subject)
                    .complaintReferenceId(complaint.getReferenceId()).build();
        } catch (Exception e) {
            log.error("Brevo reminder failed for {}: {}", complaint.getReferenceId(), e.getMessage(), e);
            return failedStatus(complaint, departmentEmail, e);
        }
    }

    private void send(String to, String cc, String subject, String html, List<Map<String, String>> attachments) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new IllegalStateException("BREVO_API_KEY is not configured");
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sender", Map.of("name", fromName, "email", fromEmail));
        request.put("to", List.of(Map.of("email", to)));
        if (cc != null && !cc.isBlank()) request.put("cc", List.of(Map.of("email", cc)));
        request.put("replyTo", Map.of("email", fromEmail, "name", fromName));
        request.put("subject", subject);
        request.put("htmlContent", html);
        if (!attachments.isEmpty()) request.put("attachment", attachments);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    BREVO_EMAIL_API, HttpMethod.POST, new HttpEntity<>(request, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Brevo returned HTTP " + response.getStatusCode().value());
            }
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("Brevo returned HTTP " + e.getStatusCode().value() + ": "
                    + e.getResponseBodyAsString(), e);
        }
    }

    private List<Map<String, String>> complaintAttachments(Complaint complaint, String filenamePrefix) throws IOException {
        List<Map<String, String>> attachments = new ArrayList<>();
        if (complaint.getPhotoPath() != null && !complaint.getPhotoPath().isBlank()) {
            attachments.add(readAttachment(complaint.getPhotoPath(), filenamePrefix + "_photo_1.jpg"));
        }
        if (complaint.getExtraPhotoPaths() == null || complaint.getExtraPhotoPaths().isBlank()) return attachments;

        int photoNumber = 2;
        for (String path : complaint.getExtraPhotoPaths().split(",")) {
            if (!path.isBlank()) attachments.add(readAttachment(path.trim(), filenamePrefix + "_photo_" + photoNumber++ + ".jpg"));
        }
        return attachments;
    }

    private Map<String, String> readAttachment(String filePath, String filename) throws IOException {
        byte[] content;
        if (filePath.startsWith("https://") || filePath.startsWith("http://")) {
            try (InputStream stream = new URL(filePath).openStream()) {
                content = stream.readAllBytes();
            }
        } else {
            File file = new File(uploadDir + filePath);
            if (!file.exists()) throw new IOException("Attachment not found: " + filePath);
            content = Files.readAllBytes(Paths.get(uploadDir + filePath));
        }
        return Map.of("name", filename, "content", Base64.getEncoder().encodeToString(content));
    }

    private EmailStatusDTO failedStatus(Complaint complaint, String departmentEmail, Exception e) {
        return EmailStatusDTO.builder().sent(false).sentTo(departmentEmail)
                .failureReason("Brevo error: " + e.getMessage())
                .complaintReferenceId(complaint.getReferenceId()).build();
    }

    private String getDepartmentEmail(DepartmentType department) {
        // Demo receiver: replace with verified government-department addresses before production use.
        return "krishnamittal969145@gmail.com";
    }
}
