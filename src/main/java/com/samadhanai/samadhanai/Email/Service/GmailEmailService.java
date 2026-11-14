package com.samadhanai.samadhanai.Email.Service;

import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import com.samadhanai.samadhanai.Email.Dto.EmailStatusDTO;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Sends the application's transactional email through Gmail SMTP.
 * This replaces SendGrid because the previous SendGrid trial has expired.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GmailEmailService {

    private final ComplaintLetterBuilder letterBuilder;
    private final JavaMailSender mailSender;

    @Value("${app.email.smtp.from}")
    private String fromEmail;

    @Value("${app.email.smtp.from.name:SamadhanAI Civic Platform}")
    private String fromName;

    @Value("${app.upload.dir}")
    private String uploadDir;

    public EmailStatusDTO sendComplaintToDepartment(Complaint complaint) {
        String departmentEmail = getDepartmentEmail(complaint.getAssignedDepartment());
        log.info("Starting Gmail SMTP email for complaint {} to {}", complaint.getReferenceId(), departmentEmail);

        try {
            ComplaintLetterBuilder.LetterResult letter = letterBuilder.buildComplaintLetter(complaint);
            MimeMessageHelper helper = createMessage(
                    departmentEmail, complaint.getUserEmail(), letter.getSubject(), letter.getBodyHtml());

            attachComplaintPhotos(helper, complaint, "complaint_" + complaint.getReferenceId());
            mailSender.send(helper.getMimeMessage());

            log.info("Gmail SMTP accepted complaint email for {}", complaint.getReferenceId());
            return EmailStatusDTO.builder()
                    .sent(true)
                    .sentTo(departmentEmail)
                    .ccTo(complaint.getUserEmail())
                    .sentAt(LocalDateTime.now())
                    .subject(letter.getSubject())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();
        } catch (Exception e) {
            log.error("Gmail SMTP failed for complaint {}: {}", complaint.getReferenceId(), e.getMessage(), e);
            return failedStatus(complaint, departmentEmail, e);
        }
    }

    public void sendStatusUpdateToCitizen(Complaint complaint,
                                          ComplaintStatus oldStatus,
                                          ComplaintStatus newStatus) {
        if (complaint.getUserEmail() == null || complaint.getUserEmail().isBlank()) return;

        String subject = "Update on your complaint " + complaint.getReferenceId() + " — " + newStatus;
        String body = "<p>Your complaint <strong>" + complaint.getReferenceId() + "</strong> changed from "
                + oldStatus + " to <strong>" + newStatus + "</strong>.</p>"
                + "<p>SamadhanAI Civic Platform</p>";
        try {
            MimeMessageHelper helper = createMessage(complaint.getUserEmail(), null, subject, body);
            mailSender.send(helper.getMimeMessage());
            log.info("Gmail SMTP sent status update for {}", complaint.getReferenceId());
        } catch (Exception e) {
            log.error("Gmail SMTP status update failed for {}: {}", complaint.getReferenceId(), e.getMessage(), e);
        }
    }

    public EmailStatusDTO sendReminderToDepartment(Complaint complaint) {
        String departmentEmail = getDepartmentEmail(complaint.getAssignedDepartment());
        String subject = "REMINDER: Complaint pending — " + complaint.getReferenceId();
        String body = "<p>This is a reminder for civic complaint <strong>" + complaint.getReferenceId() + "</strong>.</p>"
                + "<p>Please review and take appropriate action.</p>";
        try {
            MimeMessageHelper helper = createMessage(departmentEmail, complaint.getUserEmail(), subject, body);
            attachComplaintPhotos(helper, complaint, "reminder_" + complaint.getReferenceId());
            mailSender.send(helper.getMimeMessage());
            log.info("Gmail SMTP sent reminder for {}", complaint.getReferenceId());
            return EmailStatusDTO.builder()
                    .sent(true).sentTo(departmentEmail).sentAt(LocalDateTime.now())
                    .subject(subject).complaintReferenceId(complaint.getReferenceId()).build();
        } catch (Exception e) {
            log.error("Gmail SMTP reminder failed for {}: {}", complaint.getReferenceId(), e.getMessage(), e);
            return failedStatus(complaint, departmentEmail, e);
        }
    }

    private MimeMessageHelper createMessage(String to, String cc, String subject, String html) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail, fromName);
        helper.setTo(to);
        if (cc != null && !cc.isBlank()) helper.setCc(cc);
        helper.setSubject(subject);
        helper.setText(html, true);
        return helper;
    }

    private void attachComplaintPhotos(MimeMessageHelper helper, Complaint complaint, String filenamePrefix)
            throws Exception {
        if (complaint.getPhotoPath() != null && !complaint.getPhotoPath().isBlank()) {
            addAttachment(helper, complaint.getPhotoPath(), filenamePrefix + "_photo_1.jpg");
        }
        if (complaint.getExtraPhotoPaths() == null || complaint.getExtraPhotoPaths().isBlank()) return;

        int photoNumber = 2;
        for (String path : complaint.getExtraPhotoPaths().split(",")) {
            if (!path.isBlank()) {
                addAttachment(helper, path.trim(), filenamePrefix + "_photo_" + photoNumber++ + ".jpg");
            }
        }
    }

    private void addAttachment(MimeMessageHelper helper, String filePath, String filename) throws Exception {
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
        helper.addAttachment(filename, new ByteArrayResource(content), "image/jpeg");
    }

    private EmailStatusDTO failedStatus(Complaint complaint, String departmentEmail, Exception e) {
        return EmailStatusDTO.builder()
                .sent(false).sentTo(departmentEmail)
                .failureReason("Gmail SMTP error: " + e.getMessage())
                .complaintReferenceId(complaint.getReferenceId()).build();
    }

    private String getDepartmentEmail(DepartmentType department) {
        // Demo receiver: replace these with verified government department addresses before production use.
        return "krishnamittal969145@gmail.com";
    }
}
