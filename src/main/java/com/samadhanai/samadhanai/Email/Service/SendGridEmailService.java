package com.samadhanai.samadhanai.Email.Service;

import com.samadhanai.samadhanai.Common.Enums.ComplaintStatus;
import com.samadhanai.samadhanai.Common.Enums.DepartmentType;
import com.samadhanai.samadhanai.Complaint.Model.Complaint;
import com.samadhanai.samadhanai.Email.Dto.EmailStatusDTO;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Attachments;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridEmailService {

    private final ComplaintLetterBuilder letterBuilder;

    @Value("${sendgrid.api.key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    @Value("${sendgrid.from.name}")
    private String fromName;

    public EmailStatusDTO sendComplaintToDepartment(Complaint complaint) {
        log.info("🚀 Starting SendGrid email process for: {}", complaint.getReferenceId());

        String deptEmail = getDepartmentEmail(complaint.getAssignedDepartment());
        log.info("📨 Target Email: {} for Department: {}", deptEmail, complaint.getAssignedDepartment());

        try {
            log.info("📝 Building complaint letter...");
            ComplaintLetterBuilder.LetterResult letter = letterBuilder.buildComplaintLetter(complaint);
            log.info("✅ Letter built successfully. Subject: {}", letter.getSubject());

            Email from = new Email(fromEmail, fromName);
            Email to = new Email(deptEmail);
            
            Content content = new Content("text/html", letter.getBodyHtml());
            Mail mail = new Mail(from, letter.getSubject(), to, content);

            // Add CC if citizen email exists
            if (complaint.getUserEmail() != null) {
                mail.personalization.get(0).addCc(new Email(complaint.getUserEmail()));
                log.info("📋 CC set to: {}", complaint.getUserEmail());
            }

            // Add attachments
            // Main photo
            if (complaint.getPhotoPath() != null) {
                addAttachment(mail, "/tmp/uploads/" + complaint.getPhotoPath(), 
                    "complaint_" + complaint.getReferenceId() + "_photo_1.jpg");
                log.info("✅ Attached photo: {}", complaint.getPhotoPath());
            }

            // Extra photos
            if (complaint.getExtraPhotoPaths() != null && !complaint.getExtraPhotoPaths().isBlank()) {
                int photoNum = 2;
                for (String path : complaint.getExtraPhotoPaths().split(",")) {
                    if (path.isBlank()) continue;
                    addAttachment(mail, "/tmp/uploads/" + path.trim(), 
                        "complaint_" + complaint.getReferenceId() + "_photo_" + photoNum + ".jpg");
                    log.info("✅ Attached extra photo: {}", path.trim());
                    photoNum++;
                }
            }

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();

            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            log.info("🚀 SENDING EMAIL via SendGrid API to: {} for: {}", deptEmail, complaint.getReferenceId());
            log.info("⏰ Email send attempt started at: {}", LocalDateTime.now());

            Response response = sg.api(request);

            if (response.getStatusCode() == 202) {
                log.info("🎉 SUCCESS - Email sent to: {} for: {}", deptEmail, complaint.getReferenceId());
                log.info("⏰ Email send completed at: {}", LocalDateTime.now());
                
                return EmailStatusDTO.builder()
                    .sent(true)
                    .sentTo(deptEmail)
                    .ccTo(complaint.getUserEmail())
                    .sentAt(LocalDateTime.now())
                    .subject(letter.getSubject())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();
            } else {
                log.error("❌ SendGrid API error: {} - {}", response.getStatusCode(), response.getBody());
                return EmailStatusDTO.builder()
                    .sent(false)
                    .sentTo(deptEmail)
                    .failureReason("SendGrid error: " + response.getStatusCode())
                    .complaintReferenceId(complaint.getReferenceId())
                    .build();
            }

        } catch (Exception e) {
            log.error("❌ Error sending email via SendGrid: {}", e.getMessage(), e);
            return EmailStatusDTO.builder()
                .sent(false)
                .sentTo(deptEmail)
                .failureReason("SendGrid error: " + e.getMessage())
                .complaintReferenceId(complaint.getReferenceId())
                .build();
        }
    }

    private void addAttachment(Mail mail, String filePath, String filename) throws IOException {
        File file = new File(filePath);
        if (file.exists()) {
            byte[] fileContent = Files.readAllBytes(Paths.get(filePath));
            String encoded = Base64.getEncoder().encodeToString(fileContent);
            
            Attachments attachment = new Attachments();
            attachment.setContent(encoded);
            attachment.setType("image/jpeg");
            attachment.setFilename(filename);
            attachment.setDisposition("attachment");
            
            mail.addAttachments(attachment);
        } else {
            log.warn("⚠️ Attachment not found: {}", filePath);
        }
    }

    private String getDepartmentEmail(DepartmentType dept) {
        if (dept == null) return "krishnamittal969145@gmail.com";
        return switch (dept) {
            case PWD -> "krishnamittal969145@gmail.com";
            case MUNICIPAL_CORPORATION -> "krishnamittal969145@gmail.com";
            case ELECTRICITY_BOARD -> "krishnamittal969145@gmail.com";
            case WATER_SUPPLY -> "krishnamittal969145@gmail.com";
            case SANITATION -> "krishnamittal969145@gmail.com";
        };
    }
}
